package org.libertalia.reader.lector

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.libertalia.reader.R
import org.libertalia.reader.biblioteca.esDePaso
import org.libertalia.reader.datos.ColorSubrayado
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.datos.TipoTarjeta
import kotlinx.coroutines.delay
import java.util.UUID
import org.libertalia.reader.lector.modotexto.ModoTexto
import org.libertalia.reader.lector.modotexto.PdfSinTexto
import org.libertalia.reader.LibertaliaApp
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Una entrada del índice, con su nivel (para sangrar los subcapítulos). */
data class EntradaIndice(val enlace: Link, val titulo: String, val nivel: Int)

/** Lo que muestra la búsqueda dentro del libro. */
data class EstadoBusqueda(
    val consulta: String = "",
    val buscando: Boolean = false,
    val resultados: List<Locator> = emptyList(),
    val buscada: Boolean = false,
)

/** «Pág. [pagina] de [total]». */
data class Paginacion(val pagina: Int, val total: Int)

/**
 * Estado de la pantalla del lector: barras visibles o no, posición actual,
 * índice, búsqueda, ajustes y páginas. Sobrevive al girar el móvil.
 */
@OptIn(ExperimentalReadiumApi::class)
class LectorViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val prefs = aplicacion.getSharedPreferences("lector", Context.MODE_PRIVATE)

    val publicacion: Publication? get() = LibroAbierto.publicacion

    /**
     * La barra inferior está plegada (solo queda la pestañita). Se recuerda.
     * La barra superior (título y autor) está siempre visible.
     */
    var inferiorPlegada by mutableStateOf(prefs.getBoolean(CLAVE_PLEGADA, false))
        private set

    /** Posición actual en el libro. */
    var posicion by mutableStateOf<Locator?>(null)
        private set

    /** Ajustes del panel Aa (tema, letra, brillo...). Se guardan al cambiarlos. */
    var ajustes by mutableStateOf(AjustesLectura.paraLibro(aplicacion, LibroAbierto.libro?.ajustesTexto))
        private set

    /** Marcapáginas del libro (la frase marcada), si lo tiene. */
    var marcapaginas by mutableStateOf(
        LibroAbierto.libro?.marcapaginas?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
    )
        private set

    var ajustesAbiertos by mutableStateOf(false)
    var confirmarMarcapaginas by mutableStateOf(false)
    var indiceAbierto by mutableStateOf(false)
    var busquedaAbierta by mutableStateOf(false)
    var busqueda by mutableStateOf(EstadoBusqueda())
        private set

    private var trabajoBusqueda: Job? = null

    val esPdf: Boolean get() = publicacion?.conformsTo(Publication.Profile.PDF) == true

    /** El libro es un PDF (aunque ahora se lea en modo texto). */
    val esPdfOriginal: Boolean get() = LibroAbierto.libro?.formato == Formato.PDF

    /** Es un cómic (por ahora, un PDF de cómic: se abre en el lector de PDF). */
    val esComic: Boolean get() = LibroAbierto.libro?.tipo == org.libertalia.reader.datos.TipoLibro.COMIC

    /** Se está leyendo el PDF en modo texto. */
    val enModoTexto: Boolean get() = LibroAbierto.modoTexto

    /** Si en este libro se separan las notas al pie en el modo texto (interruptor del panel Aa). */
    var notasActivas by mutableStateOf(
        LibroAbierto.libro?.let { ModoTexto.notasActivas(aplicacion, it.id) } ?: true,
    )

    /** Mientras se crea el modo texto: página por la que va y total. */
    var preparandoTexto by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** Aviso que mostrar (texto de strings.xml), por ejemplo si el PDF no tiene texto. */
    var avisoModoTexto by mutableStateOf<Int?>(null)

    private var trabajoModoTexto: Job? = null

    /** Crea el modo texto del PDF (solo la primera vez) y después llama a [alTerminar]. */
    fun prepararModoTexto(hrefPdf: String, alTerminar: () -> Unit) {
        val libro = LibroAbierto.libro ?: return
        if (trabajoModoTexto?.isActive == true) return
        preparandoTexto = 0 to 0
        trabajoModoTexto = viewModelScope.launch {
            try {
                ModoTexto.preparar(getApplication(), libro, hrefPdf) { pagina, total ->
                    preparandoTexto = pagina to total
                }
                preparandoTexto = null
                alTerminar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: PdfSinTexto) {
                avisoModoTexto = R.string.modo_texto_sin_texto
            } catch (e: Exception) {
                avisoModoTexto = R.string.modo_texto_error
            } finally {
                preparandoTexto = null
            }
        }
    }

    fun cancelarModoTexto() {
        trabajoModoTexto?.cancel()
        preparandoTexto = null
    }
    val sePuedeBuscar: Boolean get() = publicacion?.isSearchable == true

    /** El índice del libro, aplanado y con niveles. */
    val indice: List<EntradaIndice> by lazy {
        fun aplanar(enlaces: List<Link>, nivel: Int): List<EntradaIndice> = enlaces.flatMap { enlace ->
            listOf(EntradaIndice(enlace, enlace.title?.trim().orEmpty(), nivel)) + aplanar(enlace.children, nivel + 1)
        }
        aplanar(publicacion?.tableOfContents.orEmpty(), 0).filter { it.titulo.isNotEmpty() }
    }

    // ---- Páginas ----
    //
    // En PDF, las páginas son las del archivo.
    // En EPUB, una página es una pantalla: si la letra crece, hay más páginas.
    // Los capítulos ya abiertos se miden de verdad (cuántas pantallas ocupan); los demás
    // se calculan por su número de letras, con la misma proporción que los ya medidos.
    // Por eso, en libros largos, el total puede ajustarse un poco al ir leyendo.

    /** Posiciones de Readium (en PDF, una por página). */
    private var posiciones by mutableStateOf<List<Locator>>(emptyList())

    /** Capítulos del EPUB en orden de lectura. */
    private var ordenLectura: List<Link> = emptyList()

    /** Número de letras de cada capítulo del EPUB. */
    private var letras by mutableStateOf<Map<String, Int>>(emptyMap())

    /** Número de palabras de cada capítulo (para la velocidad de lectura). */
    private var palabras: Map<String, Int> = emptyMap()

    /** Pantallas que ocupa cada capítulo ya medido, con la letra y los márgenes actuales. */
    private val pantallasMedidas = mutableStateMapOf<String, Int>()

    init {
        // La primera vez que se abre un libro, se le quedan sus ajustes de texto
        // (los últimos usados): a partir de ahí, cambiar otro libro no le afecta.
        LibroAbierto.libro?.takeIf { it.ajustesTexto == null }?.let { libro ->
            val app = getApplication<LibertaliaApp>()
            val texto = ajustes.textoCodificado
            app.alcanceApp.launch { app.baseDatos.libros().guardarAjustesTexto(libro.id, texto) }
        }
        viewModelScope.launch {
            val pub = publicacion ?: return@launch
            posiciones = pub.positions()
            if (!esPdf) {
                ordenLectura = pub.readingOrder
                val cuentas = withContext(Dispatchers.IO) {
                    pub.readingOrder.associate { clave(it.href.toString()) to cuentaDe(pub, it) }
                }
                palabras = cuentas.mapValues { it.value.second }
                letras = cuentas.mapValues { it.value.first }
            }
        }
    }

    /** La página actual y el total. */
    val paginacion: Paginacion?
        get() {
            val actual = posicion ?: return null
            val porPosiciones = actual.locations.position?.let { p ->
                posiciones.size.takeIf { it > 0 }?.let { Paginacion(p.coerceIn(1, it), it) }
            }
            if (esPdf) return porPosiciones
            val capitulo = clave(actual.href.toString())
            val densidad = densidad() ?: return porPosiciones
            var antes = 0
            var total = 0
            var encontrado = false
            for (enlace in ordenLectura) {
                val c = clave(enlace.href.toString())
                val n = pantallasDe(c, densidad)
                if (c == capitulo) encontrado = true else if (!encontrado) antes += n
                total += n
            }
            if (!encontrado) return porPosiciones
            val enCapitulo = pantallasDe(capitulo, densidad)
            val pantalla = ((actual.locations.progression ?: 0.0) * enCapitulo).roundToInt()
                .coerceIn(0, enCapitulo - 1)
            return Paginacion(antes + pantalla + 1, total)
        }

    /** Lo que se midió en otras sesiones, por libro y por «firma» (letra, márgenes, pantalla). */
    private val medidasGuardadas = aplicacion.getSharedPreferences("paginas", Context.MODE_PRIVATE)

    /** Letra, márgenes, interlineado y tamaño de pantalla con los que se mide. */
    private var firma: String? = null

    private fun claveGuardada(): String? {
        val id = LibroAbierto.libro?.id ?: return null
        val f = firma ?: return null
        return "$id|$f"
    }

    /**
     * Cambia la firma (al abrir el libro o al cambiar la letra, los márgenes...).
     * Las medidas de otra firma no valen, pero se recuperan las guardadas de esta.
     * Devuelve true si ha cambiado.
     */
    fun usarFirma(nueva: String): Boolean {
        if (nueva == firma) return false
        firma = nueva
        pantallasMedidas.clear()
        claveGuardada()?.let { clave ->
            medidasGuardadas.getString(clave, null)?.split(';')?.forEach { par ->
                val capitulo = par.substringBeforeLast('=', "")
                val n = par.substringAfterLast('=').toIntOrNull()
                if (capitulo.isNotEmpty() && n != null && n > 0) pantallasMedidas[capitulo] = n
            }
        }
        return true
    }

    /** Apunta (y guarda) cuántas pantallas ocupa un capítulo (lo mide el lector). */
    fun capituloMedido(href: String, pantallas: Int) {
        if (pantallas <= 0 || pantallasMedidas[clave(href)] == pantallas) return
        pantallasMedidas[clave(href)] = pantallas
        claveGuardada()?.let { clave ->
            medidasGuardadas.edit {
                putString(clave, pantallasMedidas.entries.joinToString(";") { "${it.key}=${it.value}" })
            }
        }
    }

    /** La posición donde empieza una página (para la barra de progreso). */
    fun posicionDePagina(pagina: Int): Locator? {
        val pub = publicacion ?: return null
        val densidad = densidad()
        if (esPdf || densidad == null) {
            val total = paginacion?.total ?: return null
            if (posiciones.isEmpty()) return null
            val indice = ((pagina - 1).toLong() * posiciones.size / total).toInt()
            return posiciones[indice.coerceIn(0, posiciones.lastIndex)]
        }
        // La última página (o más allá, si el total ha cambiado mientras se arrastraba la barra)
        // lleva siempre al final del libro. Si el último capítulo aún no se ha medido, su número de
        // pantallas es una estimación y, calculando la pantalla, el salto podría quedarse antes.
        val total = paginacion?.total
        if (total != null && pagina >= total) return finalDelLibro(pub)
        var acumulado = 0
        for (enlace in ordenLectura) {
            val n = pantallasDe(clave(enlace.href.toString()), densidad)
            if (pagina <= acumulado + n) {
                val pantalla = pagina - acumulado - 1
                // El centro de la pantalla, para no caer en la anterior por redondeos.
                val progreso = if (pantalla == 0) 0.0 else (pantalla + 0.5) / n
                return pub.locatorFromLink(enlace)?.copyWithLocations(progression = progreso)
            }
            acumulado += n
        }
        return finalDelLibro(pub)
    }

    /** El final del último capítulo (su última pantalla). */
    private fun finalDelLibro(pub: Publication): Locator? =
        ordenLectura.lastOrNull()?.let { pub.locatorFromLink(it) }?.copyWithLocations(progression = 1.0)

    /** Pantallas por letra, sacado de los capítulos ya medidos (los largos son más fiables). */
    private fun densidad(): Double? {
        fun calcular(minimo: Int): Double? {
            var pantallas = 0
            var total = 0
            pantallasMedidas.forEach { (capitulo, n) ->
                val l = letras[capitulo] ?: 0
                if (l >= minimo) {
                    pantallas += n
                    total += l
                }
            }
            return if (total > 0) pantallas.toDouble() / total else null
        }
        return calcular(LETRAS_FIABLES) ?: calcular(1)
    }

    private fun pantallasDe(capitulo: String, densidad: Double): Int =
        pantallasMedidas[capitulo] ?: ceil((letras[capitulo] ?: 0) * densidad).toInt().coerceAtLeast(1)

    /**
     * Palabras que hay, más o menos, en una página (pantalla) del capítulo de [sitio]: las del
     * capítulo repartidas entre sus páginas. 0 en PDF (sus páginas no se miden así).
     */
    fun palabrasDePagina(sitio: Locator): Int {
        if (esPdf) return 0
        val capitulo = clave(sitio.href.toString())
        val enCapitulo = palabras[capitulo] ?: return 0
        val densidad = densidad() ?: return 0
        return enCapitulo / pantallasDe(capitulo, densidad).coerceAtLeast(1)
    }

    /** Cuenta las letras y las palabras del texto de un capítulo (sin las etiquetas HTML). */
    private suspend fun cuentaDe(pub: Publication, enlace: Link): Pair<Int, Int> = runCatching {
        val recurso = pub.get(enlace) ?: return 0 to 0
        val bytes = try {
            recurso.read().getOrNull()
        } finally {
            recurso.close()
        } ?: return 0 to 0
        val html = bytes.toString(Charsets.UTF_8)
        val texto = html.substringAfter("<body", html)
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&[a-zA-Z0-9#]+;"), "x")
            .replace(Regex("\\s+"), " ")
            .trim()
        texto.length to Regex("\\p{L}+").findAll(texto).count()
    }.getOrDefault(0 to 0)

    private fun clave(href: String): String = href.substringBefore('#')

    /** Un toque en el centro de la página pliega o despliega la barra inferior. */
    fun alternarBarraInferior() {
        plegarInferior(!inferiorPlegada)
    }

    fun plegarInferior(plegada: Boolean) {
        inferiorPlegada = plegada
        prefs.edit { putBoolean(CLAVE_PLEGADA, plegada) }
    }

    fun cambiarAjustes(nuevos: AjustesLectura) {
        if (nuevos == ajustes) return
        val cambiaTexto = !nuevos.mismoTexto(ajustes)
        ajustes = nuevos
        // En la app se guardan todos (son «los últimos que se usaron», para los libros nuevos)...
        nuevos.guardar(getApplication())
        // ...y la letra, los márgenes y el interlineado, también en este libro.
        val id = LibroAbierto.libro?.id ?: return
        if (cambiaTexto) {
            val app = getApplication<LibertaliaApp>()
            app.alcanceApp.launch { app.baseDatos.libros().guardarAjustesTexto(id, nuevos.textoCodificado) }
        }
    }

    /** Pone el marcapáginas en la frase elegida (solo hay uno por libro: se mueve). */
    fun ponerMarcapaginas(frase: Locator) {
        marcapaginas = frase
        val id = LibroAbierto.libro?.id ?: return
        val app = getApplication<LibertaliaApp>()
        app.alcanceApp.launch {
            app.baseDatos.libros().guardarMarcapaginas(id, frase.toJSON().toString(), System.currentTimeMillis())
        }
    }

    fun posicionCambiada(nueva: Locator) {
        posicion = nueva
    }

    // ---- Subrayados y páginas destacadas (cada uno es una tarjeta) ----

    private val tarjetasDao = (aplicacion as LibertaliaApp).baseDatos.tarjetas()

    /** Las tarjetas de este libro (se actualizan solas). */
    var tarjetas by mutableStateOf<List<Tarjeta>>(emptyList())
        private set

    /**
     * Se puede subrayar: EPUB y modo texto de los PDF (en el PDF original Readium no deja
     * seleccionar texto). En modo texto, el subrayado se vuelve a encontrar por su propio
     * texto (y el de alrededor), así que sigue en su sitio aunque se mejore la limpieza.
     */
    val sePuedeSubrayar: Boolean get() = !esPdf && !soloLeer

    /**
     * «Solo leer» (llegó con «Abrir con» y no se añadió a la biblioteca): no se guarda nada,
     * así que no hay subrayados, páginas destacadas, marcapáginas, tarjetas ni voz.
     */
    val soloLeer: Boolean get() = LibroAbierto.libro?.esDePaso == true

    /**
     * Se pueden destacar páginas en todos los libros de texto (no en cómics). En un PDF,
     * tanto en el original como en modo texto, se guarda la página del PDF: así la misma
     * página destacada se ve en los dos modos y no cambia nunca.
     */
    val sePuedeDestacar: Boolean get() = !esComic && !soloLeer

    /** El modo texto: su mapa de páginas del PDF. */
    private val mapa get() = if (enModoTexto) LibroAbierto.mapaTexto else null

    /** La página del PDF que se ve (en el PDF original o en modo texto), o null si no es un PDF. */
    private val paginaPdfActual: Int?
        get() {
            val actual = posicion ?: return null
            if (esPdf) return actual.locations.position
            return mapa?.paginaDe(actual)
        }

    /** Subrayado que el usuario ha tocado: se muestra su menú (color, nota, quitar). */
    var subrayadoAbierto by mutableStateOf<Tarjeta?>(null)

    /** Subrayado cuya nota se está escribiendo. */
    var notaAbierta by mutableStateOf<Tarjeta?>(null)

    /** Subrayado al que se está poniendo una etiqueta (diálogo «Nueva etiqueta»). */
    var etiquetaAbierta by mutableStateOf<Tarjeta?>(null)

    /** Todos los subrayados de todos los libros: para sugerir etiquetas que ya existen. */
    var todosLosSubrayados by mutableStateOf<List<Tarjeta>>(emptyList())
        private set

    /** Página destacada cuya descripción se está escribiendo (true si se acaba de destacar). */
    var destacadaAbierta by mutableStateOf<Pair<Tarjeta, Boolean>?>(null)

    /** Aviso breve «Al subrayar se crea una tarjeta…». */
    var avisoSubrayado by mutableStateOf(false)
        private set

    private var trabajoAviso: Job? = null

    init {
        LibroAbierto.libro?.id?.let { id ->
            viewModelScope.launch { tarjetasDao.observarDeLibro(id).collect { tarjetas = it } }
            viewModelScope.launch { tarjetasDao.observarTodosLosSubrayados().collect { todosLosSubrayados = it } }
        }
    }

    /** Subraya lo seleccionado y crea su tarjeta, con la frase completa. */
    /**
     * [alturaEnPantalla]: dónde está lo seleccionado en la pantalla, de 0 (arriba) a 1 (abajo).
     * Readium no siempre dice en qué punto exacto del libro está una selección (solo la
     * pantalla): con la altura se ordenan bien las tarjetas de una misma pantalla.
     */
    /**
     * Subraya lo seleccionado y crea su tarjeta. [parrafo] es el número del párrafo (dentro del
     * capítulo) donde empieza la selección: si ya hay otro subrayado en ese párrafo, los dos
     * se unen solos en una tarjeta (se puede «Separar» desde el aviso).
     */
    fun subrayar(seleccion: Locator, color: ColorSubrayado, alturaEnPantalla: Double = 0.0, parrafo: Int? = null) {
        val libro = LibroAbierto.libro ?: return
        val texto = seleccion.text.highlight?.trim().orEmpty()
        if (texto.isEmpty()) return
        val ahora = System.currentTimeMillis()
        // En modo texto se apunta también la página del PDF (para las tarjetas y para volver).
        val paginaPdf = mapa?.paginaDe(seleccion)
        val sitio = seleccion.toJSON()
        val propio = JSONObject()
        if (paginaPdf != null) propio.put("paginaPdf", paginaPdf)
        if (parrafo != null && parrafo >= 0) propio.put("parrafo", parrafo)
        if (propio.length() > 0) sitio.put(CLAVE_PROPIA, propio)
        val tarjeta = Tarjeta(
            id = UUID.randomUUID().toString(),
            idLibro = libro.id,
            tipo = TipoTarjeta.SUBRAYADO,
            color = color,
            texto = texto,
            contexto = FraseCompleta.de(seleccion.text),
            capitulo = seleccion.title?.takeIf { it.isNotBlank() } ?: capituloActual,
            pagina = paginaPdf ?: paginacion?.pagina,
            progreso = seleccion.locations.totalProgression
                ?: ((posicion?.locations?.totalProgression ?: 0.0) + alturaEnPantalla.coerceIn(0.0, 1.0) * 1e-5),
            posicion = sitio.toString(),
            fechaCreacion = ahora,
            fechaModificacion = ahora,
        )
        // ¿Hay ya otro subrayado en el mismo párrafo? Entonces van en la misma tarjeta.
        val href = seleccion.href.toString()
        val vecino = parrafo?.takeIf { it >= 0 }?.let { p ->
            tarjetas.firstOrNull { it.tipo == TipoTarjeta.SUBRAYADO && !it.eliminada && parrafoDe(it) == p && hrefDe(it) == href }
        }
        guardarEnSegundoPlano {
            tarjetasDao.insertar(tarjeta)
            if (vecino != null) tarjetasDao.unir(tarjeta.id, vecino.id, System.currentTimeMillis())
        }
        if (vecino != null) avisar(R.string.unir_automatico, separar = tarjeta) else mostrarAvisoSubrayado()
    }

    private fun parrafoDe(tarjeta: Tarjeta): Int? = runCatching {
        JSONObject(tarjeta.posicion).optJSONObject(CLAVE_PROPIA)?.optInt("parrafo", -1)?.takeIf { it >= 0 }
    }.getOrNull()

    private fun hrefDe(tarjeta: Tarjeta): String? = runCatching { JSONObject(tarjeta.posicion).optString("href") }.getOrNull()

    // ---- «Unir con otro subrayado» ----

    /** El subrayado que se va a unir con el siguiente que se toque (null: no se está uniendo). */
    var uniendo by mutableStateOf<Tarjeta?>(null)

    /** Aviso pequeño («Subrayados unidos»…) y, si se puede, el subrayado que «Separar» saca. */
    var aviso by mutableStateOf<Pair<Int, Tarjeta?>?>(null)
        private set
    private var trabajoAvisoUnir: Job? = null

    private fun avisar(texto: Int, separar: Tarjeta? = null) {
        aviso = texto to separar
        trabajoAvisoUnir?.cancel()
        trabajoAvisoUnir = viewModelScope.launch {
            delay(5_000)
            aviso = null
        }
    }

    /** La tarjeta principal de un subrayado (él mismo si no está unido a otros). */
    fun cabezaDe(tarjeta: Tarjeta): Tarjeta =
        tarjeta.idGrupo?.let { id -> tarjetas.firstOrNull { it.id == id } } ?: tarjeta

    /** Si el subrayado comparte tarjeta con otros. */
    fun estaUnido(tarjeta: Tarjeta): Boolean =
        tarjeta.idGrupo != null || tarjetas.any { it.idGrupo == tarjeta.id && !it.eliminada }

    fun empezarAUnir(tarjeta: Tarjeta) {
        subrayadoAbierto = null
        uniendo = tarjeta
    }

    fun cancelarUnir() {
        uniendo = null
    }

    fun separar(tarjeta: Tarjeta) {
        subrayadoAbierto = null
        aviso = null
        guardarEnSegundoPlano { tarjetasDao.separar(tarjeta.id, System.currentTimeMillis()) }
        avisar(R.string.unir_separado)
    }

    private fun mostrarAvisoSubrayado() {
        avisoSubrayado = true
        trabajoAviso?.cancel()
        trabajoAviso = viewModelScope.launch {
            delay(4_000)
            avisoSubrayado = false
        }
    }

    /** El usuario ha tocado un subrayado del libro. */
    fun subrayadoTocado(id: String) {
        // Se estaba uniendo: este es el otro subrayado.
        uniendo?.let { primero ->
            uniendo = null
            if (id != primero.id) {
                guardarEnSegundoPlano { tarjetasDao.unir(primero.id, id, System.currentTimeMillis()) }
                avisar(R.string.unir_hecho)
            }
            return
        }
        subrayadoAbierto = tarjetas.firstOrNull { it.id == id }
    }

    fun cambiarColor(tarjeta: Tarjeta, color: ColorSubrayado) {
        subrayadoAbierto = tarjeta.copy(color = color)
        guardarEnSegundoPlano {
            val grupo = tarjeta.idGrupo ?: tarjeta.id
            tarjetasDao.cambiarColorGrupo(grupo, color, System.currentTimeMillis())
        }
    }

    /** Guarda la nota (o la borra, si queda vacía o con null). */
    fun guardarNota(tarjeta: Tarjeta, nota: String?) {
        val limpia = nota?.trim()?.takeIf { it.isNotEmpty() }
        guardarEnSegundoPlano { tarjetasDao.guardarNota(tarjeta.id, limpia, System.currentTimeMillis()) }
    }

    /** Pone una etiqueta al subrayado (desde el propio texto). */
    fun anadirEtiqueta(tarjeta: Tarjeta, etiqueta: String) {
        val texto = org.libertalia.reader.tarjetas.ReglasEtiquetas.anadir(tarjeta, etiqueta) ?: return
        if (subrayadoAbierto?.id == tarjeta.id) subrayadoAbierto = tarjeta.copy(etiquetas = texto)
        guardarEnSegundoPlano { tarjetasDao.guardarEtiquetas(tarjeta.id, texto, System.currentTimeMillis()) }
    }

    fun quitarEtiqueta(tarjeta: Tarjeta, etiqueta: String) {
        val texto = org.libertalia.reader.tarjetas.ReglasEtiquetas.quitar(tarjeta, etiqueta)
        if (subrayadoAbierto?.id == tarjeta.id) subrayadoAbierto = tarjeta.copy(etiquetas = texto)
        guardarEnSegundoPlano { tarjetasDao.guardarEtiquetas(tarjeta.id, texto, System.currentTimeMillis()) }
    }

    /** Quita un subrayado o una página destacada (y su tarjeta). */
    fun quitarTarjeta(tarjeta: Tarjeta) {
        // (Si estaba unido a otros, la tarjeta común sigue con los demás.)
        guardarEnSegundoPlano { tarjetasDao.eliminarFragmento(tarjeta.id, System.currentTimeMillis()) }
    }

    /** La página destacada que se está viendo ahora, si la hay. */
    val paginaDestacada: Tarjeta?
        get() {
            val actual = posicion ?: return null
            val destacadas = destacadas
            if (destacadas.isEmpty()) return null
            if (esPdf || enModoTexto) {
                // PDF (original o en modo texto): se compara la página del PDF.
                val pagina = paginaPdfActual ?: return null
                return destacadas.firstOrNull { locatorDe(it)?.locations?.position == pagina }
            }
            // EPUB: una página es una pantalla, que cambia con la letra. Cuenta la destacada
            // que empieza dentro de la pantalla que se ve.
            val href = clave(actual.href.toString())
            val desde = actual.locations.progression ?: 0.0
            val pantallas = densidad()?.let { pantallasDe(href, it) } ?: 1
            val hasta = desde + 1.0 / pantallas
            return destacadas.firstOrNull { tarjeta ->
                val sitio = locatorDe(tarjeta) ?: return@firstOrNull false
                val p = sitio.locations.progression ?: 0.0
                clave(sitio.href.toString()) == href && p >= desde - 0.0001 && p < hasta - 0.0001
            }
        }

    /** Botón de la barra superior: destaca la página (y pide una descripción) o abre la ya destacada. */
    fun pulsarDestacar() {
        paginaDestacada?.let {
            destacadaAbierta = it to false
            return
        }
        val libro = LibroAbierto.libro ?: return
        val actual = posicion ?: return
        val ahora = System.currentTimeMillis()
        // En modo texto se guarda la página del PDF (la misma que en el PDF original).
        val mapaTexto = mapa
        val paginaPdf = if (mapaTexto != null) mapaTexto.paginaDe(actual) ?: return else null
        val sitio = if (mapaTexto != null && paginaPdf != null) mapaTexto.posicionPdf(paginaPdf) ?: return else actual
        val pagina = paginaPdf ?: paginacion?.pagina
        val total = mapaTexto?.totalPaginas ?: paginacion?.total
        val tarjeta = Tarjeta(
            id = UUID.randomUUID().toString(),
            idLibro = libro.id,
            tipo = TipoTarjeta.PAGINA,
            texto = actual.text.highlight?.trim().orEmpty(),
            capitulo = actual.title?.takeIf { it.isNotBlank() } ?: capituloActual,
            pagina = pagina,
            progreso = sitio.locations.totalProgression
                ?: pagina?.let { p -> total?.let { (p - 1).toDouble() / it.coerceAtLeast(1) } }
                ?: 0.0,
            posicion = sitio.toJSON().toString(),
            fechaCreacion = ahora,
            fechaModificacion = ahora,
        )
        guardarEnSegundoPlano { tarjetasDao.insertar(tarjeta) }
        destacadaAbierta = tarjeta to true
    }

    fun guardarDescripcion(tarjeta: Tarjeta, descripcion: String) {
        val limpia = descripcion.trim().takeIf { it.isNotEmpty() }
        guardarEnSegundoPlano { tarjetasDao.guardarDescripcion(tarjeta.id, limpia, System.currentTimeMillis()) }
    }

    fun locatorDe(tarjeta: Tarjeta): Locator? =
        runCatching { Locator.fromJSON(JSONObject(tarjeta.posicion)) }.getOrNull()

    /**
     * Adónde ir para ver una tarjeta (al volver de «Ir al libro»). Los subrayados de un PDF
     * se hicieron en modo texto: si ahora se ve el PDF original, se va a su página del PDF.
     */
    fun destinoDeTarjeta(id: String): Locator? {
        val tarjeta = tarjetas.firstOrNull { it.id == id } ?: return null
        if (esPdf) {
            val pagina = paginaPdfDe(tarjeta) ?: tarjeta.pagina ?: return null
            return posicionDePagina(pagina)
        }
        return locatorDe(tarjeta)
    }

    /** La página del PDF que se guardó con un subrayado hecho en modo texto. */
    private fun paginaPdfDe(tarjeta: Tarjeta): Int? = runCatching {
        JSONObject(tarjeta.posicion).optJSONObject(CLAVE_PROPIA)?.optInt("paginaPdf")?.takeIf { it > 0 }
    }.getOrNull()

    // ---- Lista de páginas destacadas ----

    /** Las páginas destacadas del libro, en orden. */
    val destacadas: List<Tarjeta> get() = tarjetas.filter { it.tipo == TipoTarjeta.PAGINA }

    var listaDestacadasAbierta by mutableStateOf(false)

    /**
     * Adónde ir para ver una página destacada. En modo texto, las destacadas guardan la
     * página del PDF: se busca dónde empieza esa página en el texto.
     */
    fun destinoDe(tarjeta: Tarjeta): Locator? {
        val sitio = locatorDe(tarjeta) ?: return null
        val mapaTexto = mapa ?: return sitio
        val pagina = sitio.locations.position ?: tarjeta.pagina ?: return null
        return mapaTexto.posicionTexto(pagina, publicacion ?: return null)
    }

    /** Se guarda aunque se cierre el lector justo después. */
    private fun guardarEnSegundoPlano(accion: suspend () -> Unit) {
        getApplication<LibertaliaApp>().alcanceApp.launch { accion() }
    }

    /**
     * Título del capítulo actual: el que trae la posición o, si no lo trae,
     * el del índice que corresponde a esa parte del libro.
     */
    val capituloActual: String?
        get() {
            val actual = posicion ?: return null
            actual.title?.takeIf { it.isNotBlank() }?.let { return it }
            val href = actual.href.toString().substringBefore('#')
            return indice.lastOrNull { it.enlace.href.toString().substringBefore('#') == href }?.titulo
        }

    fun cambiarConsulta(texto: String) {
        busqueda = busqueda.copy(consulta = texto)
    }

    /** Busca el texto en todo el libro (como mucho 300 resultados). */
    fun buscar() {
        val consulta = busqueda.consulta.trim()
        val pub = publicacion ?: return
        if (consulta.isEmpty()) return
        trabajoBusqueda?.cancel()
        busqueda = busqueda.copy(buscando = true, resultados = emptyList(), buscada = false)
        trabajoBusqueda = viewModelScope.launch {
            val encontrados = mutableListOf<Locator>()
            val iterador = pub.search(consulta)
            try {
                while (iterador != null && encontrados.size < MAXIMO_RESULTADOS) {
                    val pagina = iterador.next().getOrNull() ?: break
                    encontrados += pagina.locators
                    busqueda = busqueda.copy(resultados = encontrados.toList())
                }
            } finally {
                iterador?.close()
            }
            busqueda = busqueda.copy(buscando = false, resultados = encontrados.toList(), buscada = true)
        }
    }

    private companion object {
        const val CLAVE_PLEGADA = "barra_inferior_plegada"
        const val MAXIMO_RESULTADOS = 300
        const val LETRAS_FIABLES = 500
        /** Datos propios que se añaden a la posición de Readium (por ejemplo, la página del PDF). */
        const val CLAVE_PROPIA = "libertalia"
    }
}
