package org.libertalia.reader.comic

import org.libertalia.reader.biblioteca.LibroDePaso
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.R
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.lector.AjustesLectura
import org.libertalia.reader.racha.MedidorLectura

/** Cómo se ven las páginas: una a una, de dos en dos (como un cómic abierto) o en scroll vertical. */
enum class ModoComic { PAGINA, DOBLE, SCROLL }

/**
 * Estado del lector de cómics: el archivo abierto, la página por la que se va, el sentido
 * de lectura, el modo, el recorte de márgenes, los marcadores y las barras. Sobrevive al
 * giro del móvil.
 */
class LectorComicViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as LibertaliaApp
    private val libros = app.baseDatos.libros()
    private val preferencias = aplicacion.getSharedPreferences("comic", Context.MODE_PRIVATE)

    var libro by mutableStateOf<Libro?>(null)
        private set

    /** Número de páginas (0 mientras se abre). */
    var total by mutableIntStateOf(0)
        private set

    /** Página actual, empezando en 0. En doble página, la primera de las dos. */
    var pagina by mutableIntStateOf(0)
        private set

    var derechaAIzquierda by mutableStateOf(false)
        private set

    var barrasVisibles by mutableStateOf(true)

    /** Hay una página ampliada: deslizar mueve la imagen, no pasa de página. */
    var ampliada by mutableStateOf(false)

    /** Texto de error (strings.xml) si no se ha podido abrir. */
    var error by mutableStateOf<Int?>(null)
        private set

    val ajustes: AjustesLectura = AjustesLectura.cargar(aplicacion)

    // --- Modo de lectura ---

    /** El modo que el usuario eligió para este cómic, o null si no eligió ninguno. */
    var modoElegido by mutableStateOf<ModoComic?>(null)
        private set

    /** El móvil está en horizontal (lo apunta la pantalla). */
    var horizontal by mutableStateOf(false)

    /** El modo que se usa: el elegido o, si no hay, doble página en horizontal y página a página en vertical. */
    val modo: ModoComic get() = modoElegido ?: if (horizontal) ModoComic.DOBLE else ModoComic.PAGINA

    fun elegirModo(nuevo: ModoComic) {
        val datos = libro ?: return
        if (nuevo == modo) return // pulsar el modo que ya se ve no cambia nada (ni quita el automático)
        modoElegido = nuevo
        ampliada = false
        preferencias.edit().putString("modo_${datos.id}", nuevo.name).apply()
    }

    // --- Doble página ---
    // La portada (página 0) va sola, como en un cómic de papel; después van de dos en dos:
    // pliego 1 = páginas 1 y 2, pliego 2 = páginas 3 y 4...

    val pliegos: Int get() = if (total == 0) 0 else 1 + total / 2

    fun pliegoDe(indice: Int): Int = (indice + 1) / 2

    fun paginasDelPliego(pliego: Int): List<Int> =
        if (pliego == 0) listOf(0) else listOf(2 * pliego - 1, 2 * pliego).filter { it < total }

    /** Las páginas que se ven ahora (una, o dos en doble página). */
    val paginasVisibles: List<Int>
        get() = if (modo == ModoComic.DOBLE) paginasDelPliego(pliegoDe(pagina)) else listOf(pagina)

    // --- Recorte de márgenes (para todos los cómics) ---

    var recortar by mutableStateOf(preferencias.getBoolean("recortar", true))
        private set

    fun alternarRecorte() {
        recortar = !recortar
        preferencias.edit().putBoolean("recortar", recortar).apply()
        cache.evictAll()
    }

    // --- Marcadores de página ---

    /** Páginas marcadas (desde 0), en orden. */
    var marcadores by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Si alguna de las páginas que se ven tiene marcador. */
    val hayMarcadorAqui: Boolean get() = paginasVisibles.any { it in marcadores }

    /** Pone o quita el marcador de la página que se ve (en doble página, de las dos). */
    fun alternarMarcador() {
        val visibles = paginasVisibles
        marcadores = if (visibles.any { it in marcadores }) marcadores - visibles.toSet() else (marcadores + pagina).sorted()
        guardarMarcadores()
    }

    fun quitarMarcador(indice: Int) {
        marcadores = marcadores - indice
        guardarMarcadores()
    }

    private fun guardarMarcadores() {
        val datos = libro ?: return
        // Se guardan como lista de números de página (desde 1): «[3, 12, 40]».
        val texto = if (marcadores.isEmpty()) null else JSONArray(marcadores.map { it + 1 }).toString()
        app.alcanceApp.launch { libros.guardarMarcapaginas(datos.id, texto, System.currentTimeMillis()) }
    }

    private fun leerMarcadores(texto: String?): List<Int> = runCatching {
        val lista = JSONArray(texto ?: return emptyList())
        (0 until lista.length()).map { lista.getInt(it) - 1 }.filter { it in 0 until total }.distinct().sorted()
    }.getOrDefault(emptyList())

    // --- Archivo y páginas ---

    private var fuente: FuentePaginas? = null
    private val cerrojo = Mutex()

    /** Páginas ya dibujadas, para no volver a leerlas al ir y volver (hasta ~1/5 de la memoria). */
    private val cache = object : LruCache<String, List<Bitmap>>((Runtime.getRuntime().maxMemory() / 5).toInt()) {
        override fun sizeOf(key: String, value: List<Bitmap>) = value.sumOf { it.allocationByteCount }
    }

    /** Peticiones de pasar página (+1 / -1) desde los toques o los botones de volumen. */
    private val _saltos = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val saltos: SharedFlow<Int> = _saltos

    /** Saltos a una página concreta (desde la barra de progreso o los marcadores). */
    private val _destinos = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val destinos: SharedFlow<Int> = _destinos

    fun saltarA(indice: Int) {
        _destinos.tryEmit(indice)
    }

    /** Solo para pruebas: doble toque simulado en (x, y), de 0 a 1 sobre la página. */
    private val _pruebasDobleToque = MutableSharedFlow<Pair<Float, Float>>(extraBufferCapacity = 2)
    val pruebasDobleToque: SharedFlow<Pair<Float, Float>> = _pruebasDobleToque

    fun simularDobleToque(x: Float, y: Float) {
        _pruebasDobleToque.tryEmit(x to y)
    }

    private var cargado = false

    fun cargar(idLibro: String) {
        if (cargado) return
        cargado = true
        // El modo de este cómic; si no tiene, el de Ajustes («automático» = según cómo se sujete el móvil).
        modoElegido = preferencias.getString("modo_$idLibro", null)?.let { runCatching { ModoComic.valueOf(it) }.getOrNull() }
            ?: org.libertalia.reader.ui.ajustes.AjustesApp.modoComicPorDefecto(getApplication())
        viewModelScope.launch {
            // («Solo leer»: el cómic no está en la base de datos.)
            val datos = LibroDePaso.actual?.takeIf { it.id == idLibro } ?: libros.buscarPorId(idLibro)
            val uri = datos?.uriArchivo
            if (datos == null || uri == null) {
                error = R.string.comic_error_abrir
                return@launch
            }
            libro = datos
            derechaAIzquierda = datos.sentidoLectura == SentidoLectura.DERECHA_A_IZQUIERDA
            val abierta = withContext(Dispatchers.IO) {
                runCatching { FuentePaginas.abrir(app, Uri.parse(uri), datos.formato.name, datos.id) }
            }.getOrElse {
                error = R.string.comic_error_abrir
                return@launch
            }
            fuente = abierta
            if (abierta.total == 0) {
                error = R.string.comic_error_abrir
                return@launch
            }
            // La página se pone antes que el total: al aparecer las páginas, ya se abren en su sitio.
            pagina = paginaGuardada(datos.posicion).coerceIn(0, abierta.total - 1)
            total = abierta.total
            marcadores = leerMarcadores(datos.marcapaginas)
            libros.marcarAbierto(datos.id, System.currentTimeMillis())
        }
    }

    /** La página guardada: «{"pagina": 12}» (empieza en 1) o, en un PDF leído antes con Readium, su posición. */
    private fun paginaGuardada(posicion: String?): Int = runCatching {
        val json = JSONObject(posicion ?: return 0)
        val numero = if (json.has("pagina")) json.getInt("pagina") else json.getJSONObject("locations").getInt("position")
        numero - 1
    }.getOrDefault(0)

    /** La imagen de una página para verla entera en pantalla (sin márgenes, si el recorte está activado). */
    suspend fun imagen(indice: Int, ancho: Int, alto: Int): Bitmap? {
        val conRecorte = recortar
        return obtener("$indice|$ancho|$alto|$conRecorte") { origen ->
            val leida = origen.pagina(indice, ancho, alto) ?: return@obtener null
            listOf(if (conRecorte) Recorte.recortar(leida) else leida)
        }?.firstOrNull()
    }

    /**
     * Una página para el scroll vertical, a todo el ancho. Las de los webtoons pueden ser
     * larguísimas (800 x 12 000 píxeles): se leen enteras y se cortan en tiras, porque la
     * tarjeta gráfica no dibuja imágenes tan altas de una vez. Aquí no se recortan los
     * márgenes: en los webtoons los espacios en blanco entre viñetas son parte de la historia.
     */
    suspend fun tiras(indice: Int, ancho: Int): List<Bitmap>? =
        obtener("tira|$indice|$ancho") { origen ->
            val entera = origen.pagina(indice, ancho, Int.MAX_VALUE / 4, maximoLado = 32_768) ?: return@obtener null
            if (entera.height <= ALTO_TIRA) return@obtener listOf(entera)
            val trozos = (0 until entera.height step ALTO_TIRA).map { y ->
                Bitmap.createBitmap(entera, 0, y, entera.width, minOf(ALTO_TIRA, entera.height - y))
            }
            entera.recycle()
            trozos
        }

    private suspend fun obtener(clave: String, leer: (FuentePaginas) -> List<Bitmap>?): List<Bitmap>? {
        cache.get(clave)?.let { return it }
        val origen = fuente ?: return null
        return withContext(Dispatchers.IO) {
            cerrojo.withLock {
                cache.get(clave) ?: runCatching { leer(origen) }.getOrNull()?.also { cache.put(clave, it) }
            }
        }
    }

    /** Mide el tiempo que se pasa leyendo (para la racha). */
    val medidor = MedidorLectura(app, comic = true)

    /** «Solo leer»: llegó con «Abrir con» y no se añadió; no se guarda ni se cuenta nada. */
    val soloLeer: Boolean get() = LibroDePaso.esDePaso(libro?.id)

    private var trabajoGuardar: Job? = null

    /** La pantalla avisa de cada cambio de página; se guarda un momento después. */
    fun paginaCambiada(nueva: Int) {
        if (nueva == pagina) return
        pagina = nueva
        medidor.actividad()
        trabajoGuardar?.cancel()
        trabajoGuardar = viewModelScope.launch {
            delay(600)
            guardar()
        }
    }

    fun guardar() {
        val datos = libro ?: return
        if (total == 0) return
        val actual = pagina
        val progreso = if (total <= 1) 1.0 else actual.toDouble() / (total - 1)
        app.alcanceApp.launch {
            libros.guardarPosicion(
                datos.id,
                JSONObject().put("pagina", actual + 1).toString(),
                progreso,
                null,
                System.currentTimeMillis(),
                pagina = actual + 1,
                paginas = total,
            )
        }
    }

    /** Pasar a la página siguiente (+1) o anterior (-1), en orden de lectura. */
    fun pasar(cuantas: Int) {
        _saltos.tryEmit(cuantas)
    }

    /** Toque en un lado de la pantalla: en manga, el lado izquierdo es «adelante». */
    fun tocarLado(izquierdo: Boolean) {
        val haciaAdelante = if (derechaAIzquierda && modo != ModoComic.SCROLL) izquierdo else !izquierdo
        pasar(if (haciaAdelante) 1 else -1)
    }

    fun cambiarSentido() {
        val datos = libro ?: return
        derechaAIzquierda = !derechaAIzquierda
        val sentido = if (derechaAIzquierda) SentidoLectura.DERECHA_A_IZQUIERDA else SentidoLectura.IZQUIERDA_A_DERECHA
        libro = datos.copy(sentidoLectura = sentido)
        app.alcanceApp.launch { libros.cambiarSentido(datos.id, sentido, System.currentTimeMillis()) }
    }

    override fun onCleared() {
        guardar()
        cache.evictAll()
        runCatching { fuente?.close() }
        super.onCleared()
    }

    private companion object {
        /** Alto máximo de cada tira en el scroll vertical. */
        const val ALTO_TIRA = 2048
    }
}
