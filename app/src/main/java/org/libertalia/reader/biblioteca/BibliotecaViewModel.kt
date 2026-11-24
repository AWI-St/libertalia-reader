package org.libertalia.reader.biblioteca

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.R
import org.libertalia.reader.datos.EstadoLectura
import org.libertalia.reader.datos.Estanteria
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.LibroEnEstanteria
import org.libertalia.reader.comic.OrigenComic
import org.libertalia.reader.comic.SentidoLectura
import org.libertalia.reader.datos.TipoLibro
import org.libertalia.reader.datos.Tarjeta
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Cuántos archivos han acabado en cada situación al añadirlos. */
data class ResumenImportacion(val cuentas: Map<ResultadoImportacion, Int>) {
    fun de(resultado: ResultadoImportacion): Int = cuentas[resultado] ?: 0
}

/** Qué se ve en la biblioteca: todo, un estado o una estantería del usuario. */
sealed interface FiltroBiblioteca {
    data object Todos : FiltroBiblioteca
    data class PorEstado(val estado: EstadoLectura) : FiltroBiblioteca
    data class PorEstanteria(val idEstanteria: String) : FiltroBiblioteca
}

/**
 * Guarda el estado de la biblioteca mientras la pantalla existe
 * (también al girar el móvil) y hace el trabajo pesado fuera de la pantalla.
 */
class BibliotecaViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as LibertaliaApp
    private val librosDao = app.baseDatos.libros()
    private val estanteriasDao = app.baseDatos.estanterias()

    /** Lista de libros; null mientras se carga por primera vez. */
    val libros: StateFlow<List<Libro>?> = librosDao.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Las estanterías creadas por el usuario. */
    val estanterias: StateFlow<List<Estanteria>> = estanteriasDao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Para cada estantería, los identificadores de sus libros. */
    val librosPorEstanteria: StateFlow<Map<String, Set<String>>> = estanteriasDao.observarRelaciones()
        .map { relaciones ->
            relaciones.groupBy({ it.estanteriaId }, { it.libroId }).mapValues { it.value.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _filtro = MutableStateFlow<FiltroBiblioteca>(FiltroBiblioteca.Todos)
    val filtro: StateFlow<FiltroBiblioteca> = _filtro.asStateFlow()

    /**
     * Modo Tarjetas (boceto 7): la misma estantería, pero al tocar un libro se abren sus
     * tarjetas. Se conserva al volver de las tarjetas de un libro.
     */
    private val _modoTarjetas = MutableStateFlow(false)
    val modoTarjetas: StateFlow<Boolean> = _modoTarjetas.asStateFlow()

    fun alternarModoTarjetas() {
        _modoTarjetas.value = !_modoTarjetas.value
    }

    /** Cuántas tarjetas tiene cada libro (por su id). */
    val conteoTarjetas: StateFlow<Map<String, Int>> = app.baseDatos.tarjetas().observarConteo()
        .map { lista -> lista.associate { it.idLibro to it.numero } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Todas las tarjetas (subrayados) de todos los libros, para la búsqueda de la biblioteca. */
    val todasLasTarjetas: StateFlow<List<Tarjeta>?> = app.baseDatos.tarjetas().observarTodosLosSubrayados()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** El último libro abierto que no está terminado: la tarjeta «Seguir leyendo». */
    val seguirLeyendo: StateFlow<Libro?> = libros
        .map { lista ->
            lista.orEmpty()
                .filter { it.fechaUltimaLectura != null && it.estado != EstadoLectura.TERMINADO }
                .maxByOrNull { it.fechaUltimaLectura ?: 0L }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importando = MutableStateFlow(false)
    val importando: StateFlow<Boolean> = _importando.asStateFlow()

    private val _resumen = MutableStateFlow<ResumenImportacion?>(null)
    val resumen: StateFlow<ResumenImportacion?> = _resumen.asStateFlow()

    /** Aviso corto para mostrar abajo (texto de strings.xml). */
    private val _aviso = MutableStateFlow<Int?>(null)
    val aviso: StateFlow<Int?> = _aviso.asStateFlow()

    /**
     * Cómic recién añadido que la app no podrá volver a abrir otro día (llegó con «Abrir con»
     * y no está en ninguna carpeta con permiso): hay que pedir al usuario su carpeta.
     */
    private val _comicSinCarpeta = MutableStateFlow<Libro?>(null)
    val comicSinCarpeta: StateFlow<Libro?> = _comicSinCarpeta.asStateFlow()

    /** Cómic recién añadido desde una nube: se recomienda guardarlo en el móvil. */
    private val _comicEnLaNube = MutableStateFlow<Libro?>(null)
    val comicEnLaNube: StateFlow<Libro?> = _comicEnLaNube.asStateFlow()

    fun avisoNubeVisto() {
        _comicEnLaNube.value = null
    }

    /** Si el archivo del libro está en una nube (para ofrecer «Guardar en el móvil»). */
    fun estaEnLaNube(libro: Libro): Boolean = OrigenComic.estaEnLaNube(libro.uriArchivo)

    /**
     * Descarga una vez el cómic de la nube a la carpeta de Libertalia y, desde entonces, se lee
     * de ahí: sin gastar datos cada vez y también sin conexión. En la nube sigue estando.
     */
    fun guardarEnMovil(libro: Libro) {
        val origen = libro.uriArchivo ?: return
        avisar(R.string.nube_guardando)
        app.alcanceApp.launch {
            val guardado = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = if (libro.formato == org.libertalia.reader.datos.Formato.PDF) "application/pdf" else "application/octet-stream"
                    val nombre = libro.nombreArchivo ?: "${libro.titulo}.${libro.formato.name.lowercase()}"
                    app.contentResolver.openInputStream(Uri.parse(origen))?.use { entrada ->
                        app.carpeta.guardarArchivo(nombre, mime, entrada)
                    }
                }.getOrNull()
            }
            if (guardado == null) {
                avisar(R.string.nube_error)
                return@launch
            }
            // Se leen los datos al día (la página puede haber cambiado mientras se descargaba).
            val actual = librosDao.buscarPorId(libro.id) ?: return@launch
            librosDao.actualizar(
                actual.copy(uriArchivo = guardado.uri.toString(), nombreArchivo = guardado.name, fechaModificacion = System.currentTimeMillis())
            )
            // Ya no hace falta el permiso sobre el archivo de la nube.
            runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(origen), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            avisar(R.string.nube_guardado)
        }
    }

    /** Archivos pesados recién añadidos: se pregunta si van en la copia automática. */
    private val _pesados = MutableStateFlow<List<Libro>>(emptyList())
    val pesados: StateFlow<List<Libro>> = _pesados.asStateFlow()

    /** Tras añadir libros: los que pesan mucho, según la elección recordada o preguntando. */
    private suspend fun revisarPesados(ids: List<String>) {
        val limite = app.preferencias.limitePesadoMb * 1024L * 1024L
        val pesados = ids.distinct().mapNotNull { librosDao.buscarPorId(it) }.filter { it.tamanoBytes > limite }
        if (pesados.isEmpty()) return
        when (app.preferencias.incluirPesados) {
            true -> Unit // Por defecto ya van en la copia.
            false -> pesados.forEach { librosDao.cambiarIncluirEnCopia(it.id, false, System.currentTimeMillis()) }
            null -> _pesados.value = _pesados.value + pesados
        }
    }

    /** Respuesta al aviso de archivo pesado (para el primero de la lista; con «Recordar», para todos). */
    fun responderPesado(incluir: Boolean, recordar: Boolean) {
        val lista = _pesados.value
        val afectados = if (recordar) lista else lista.take(1)
        _pesados.value = lista - afectados.toSet()
        if (recordar) app.preferencias.incluirPesados = incluir
        viewModelScope.launch {
            afectados.forEach { librosDao.cambiarIncluirEnCopia(it.id, incluir, System.currentTimeMillis()) }
        }
    }

    /** «Incluir el archivo en la copia automática» desde el menú del libro. */
    fun cambiarIncluirEnCopia(libro: Libro, incluir: Boolean) {
        viewModelScope.launch { librosDao.cambiarIncluirEnCopia(libro.id, incluir, System.currentTimeMillis()) }
    }

    /** Originales (p. ej. en Descargas) que sobran porque el libro ya está en la carpeta de la app. */
    private val _originalesParaBorrar = MutableStateFlow<List<Uri>>(emptyList())
    val originalesParaBorrar: StateFlow<List<Uri>> = _originalesParaBorrar.asStateFlow()

    /** Según lo que eligió el usuario: preguntar, borrar sin preguntar o dejarlos. */
    private fun ofrecerBorrarOriginales(lista: List<Uri>) {
        if (lista.isEmpty()) return
        when (app.preferencias.borrarOriginales) {
            true -> borrarArchivos(lista)
            false -> Unit
            null -> _originalesParaBorrar.value = lista
        }
    }

    /** Respuesta a «¿Borrar el original?». */
    fun responderOriginales(borrar: Boolean, recordar: Boolean) {
        val lista = _originalesParaBorrar.value
        _originalesParaBorrar.value = emptyList()
        if (recordar) app.preferencias.borrarOriginales = borrar
        if (borrar) borrarArchivos(lista)
    }

    private fun borrarArchivos(lista: List<Uri>) {
        viewModelScope.launch {
            val fallos = withContext(Dispatchers.IO) { lista.count { !ArchivosExternos.borrar(app, it) } }
            avisar(if (fallos == 0) R.string.originales_borrados else R.string.originales_no_borrados)
        }
    }

    /** Copia .libertalia recibida (con el botón + o «Abrir con»): hay que ofrecer importarla. */
    private val _copiaRecibida = MutableStateFlow<Uri?>(null)
    val copiaRecibida: StateFlow<Uri?> = _copiaRecibida.asStateFlow()

    fun copiaAtendida() {
        _copiaRecibida.value = null
    }

    /** Libro que hay que abrir en el lector (por ejemplo, tras «Abrir con»). */
    private val _libroParaAbrir = MutableStateFlow<Libro?>(null)
    val libroParaAbrir: StateFlow<Libro?> = _libroParaAbrir.asStateFlow()

    fun elegirFiltro(filtro: FiltroBiblioteca) {
        _filtro.value = filtro
    }

    fun importar(archivos: List<Uri>) {
        if (archivos.isEmpty() || _importando.value) return
        _importando.value = true
        viewModelScope.launch {
            val importaciones = archivos.map { app.importador.importar(it) }
            // Si entre los archivos hay una copia .libertalia, se ofrece importarla.
            archivos.zip(importaciones).firstOrNull { it.second.resultado == ResultadoImportacion.COPIA }
                ?.let { _copiaRecibida.value = it.first }
            _resumen.value = ResumenImportacion(
                importaciones.filter { it.resultado != ResultadoImportacion.COPIA }.groupingBy { it.resultado }.eachCount(),
            )
            ofrecerBorrarOriginales(importaciones.mapNotNull { it.originalBorrable })
            revisarPesados(importaciones.filter { it.resultado == ResultadoImportacion.ANADIDO }.mapNotNull { it.idLibro })
            importaciones.firstOrNull { it.faltaCarpeta }?.idLibro?.let { _comicSinCarpeta.value = librosDao.buscarPorId(it) }
            importaciones.firstOrNull { it.enLaNube }?.idLibro?.let { _comicEnLaNube.value = librosDao.buscarPorId(it) }
            _importando.value = false
        }
    }

    /** «Abrir con» desde otra app: se añade a la biblioteca (si no estaba) y se abre. */
    /**
     * Un archivo que llega desde otra app («Abrir con» o «Compartir»). Si ya está en la
     * biblioteca, se abre. Si es nuevo, se pregunta antes si se añade o solo se lee (así la
     * app sirve también para leer un PDF suelto sin llenar la biblioteca).
     */
    fun recibirDeOtraApp(archivo: Uri) {
        _importando.value = true
        viewModelScope.launch {
            val examinado = app.importador.examinar(archivo)
            val final = examinado.final
            if (final == null) {
                // Nuevo: se pregunta (el temporal sigue ahí hasta que se responda).
                _importando.value = false
                _preguntaAnadir.value = examinado
                return@launch
            }
            app.importador.descartar(examinado)
            when (final.resultado) {
                ResultadoImportacion.COPIA -> _copiaRecibida.value = archivo
                ResultadoImportacion.YA_EXISTIA -> {
                    val libro = final.idLibro?.let { librosDao.buscarPorId(it) }
                    // El libro ya está en la carpeta: el original recibido sobra.
                    if (libro != null && libro.tipo == TipoLibro.TEXTO && app.carpeta.contiene(libro.uriArchivo)) {
                        ofrecerMoverOriginal(archivo, libro, abrirDespues = true)
                    } else {
                        _libroParaAbrir.value = libro
                    }
                }
                else -> _resumen.value = ResumenImportacion(mapOf(final.resultado to 1))
            }
            _importando.value = false
        }
    }

    /** Archivo nuevo recibido de otra app: ¿se añade a la biblioteca o solo se lee? */
    private val _preguntaAnadir = MutableStateFlow<ArchivoExaminado?>(null)
    val preguntaAnadir: StateFlow<ArchivoExaminado?> = _preguntaAnadir.asStateFlow()

    /** [anadir]: true = a la biblioteca; false = solo leer; null = no hacer nada. */
    fun responderAnadir(anadir: Boolean?) {
        val examinado = _preguntaAnadir.value ?: return
        _preguntaAnadir.value = null
        if (anadir == null) {
            app.importador.descartar(examinado)
            return
        }
        _importando.value = true
        viewModelScope.launch {
            if (!anadir) {
                val libro = app.importador.prepararDePaso(examinado)
                _importando.value = false
                if (libro == null) {
                    avisar(R.string.abrir_con_error)
                    return@launch
                }
                LibroDePaso.actual = libro
                _libroParaAbrir.value = libro
                return@launch
            }
            val importacion = app.importador.completar(examinado)
            val resultado = importacion.resultado
            _resumen.value = ResumenImportacion(mapOf(resultado to 1))
            if (resultado == ResultadoImportacion.ANADIDO) revisarPesados(listOfNotNull(importacion.idLibro))
            val libro = importacion.idLibro?.let { librosDao.buscarPorId(it) }
            _importando.value = false
            if (libro == null) return@launch
            if (importacion.enLaNube) _comicEnLaNube.value = libro
            // Para un cómic sin permiso permanente, si no se mueve a la carpeta hay que pedir
            // que se vuelva a elegir (se hace después de la pregunta de mover).
            comicSinCarpetaPendiente = libro.takeIf { importacion.faltaCarpeta }
            ofrecerMoverOriginal(examinado.origen, libro, abrirDespues = true)
        }
    }

    /** Pregunta pendiente: ¿quitar el original de su carpeta y dejarlo en la de Libertalia? */
    data class MoverOriginal(val origen: Uri, val libro: Libro, val nombre: String, val abrirDespues: Boolean)

    private val _moverOriginal = MutableStateFlow<MoverOriginal?>(null)
    val moverOriginal: StateFlow<MoverOriginal?> = _moverOriginal.asStateFlow()

    private var comicSinCarpetaPendiente: Libro? = null

    /**
     * Tras añadir un archivo que llegó de otra app: si el original está en el móvil (no en una
     * nube) y fuera de la carpeta Libertalia, se ofrece quitarlo de allí para no tenerlo dos
     * veces (en los cómics, eso es moverlo a la carpeta Libertalia).
     */
    private suspend fun ofrecerMoverOriginal(origen: Uri, libro: Libro, abrirDespues: Boolean) {
        val delMovil = withContext(Dispatchers.IO) { ArchivosExternos.estaEnElMovil(app, origen, app.carpeta) }
        val peticion = MoverOriginal(origen, libro, withContext(Dispatchers.IO) { ArchivosExternos.nombre(app, origen) }, abrirDespues)
        when {
            !delMovil -> terminarRecepcion(peticion)
            app.preferencias.borrarOriginales == true -> moverOriginalAhora(peticion)
            app.preferencias.borrarOriginales == false -> terminarRecepcion(peticion)
            else -> _moverOriginal.value = peticion
        }
    }

    fun responderMoverOriginal(mover: Boolean, recordar: Boolean) {
        val peticion = _moverOriginal.value ?: return
        _moverOriginal.value = null
        if (recordar) app.preferencias.borrarOriginales = mover
        viewModelScope.launch {
            if (mover) moverOriginalAhora(peticion) else terminarRecepcion(peticion)
        }
    }

    /** Copia el cómic a la carpeta (los libros ya están) y borra el original. */
    private suspend fun moverOriginalAhora(peticion: MoverOriginal) {
        var libro = peticion.libro
        if (libro.tipo == TipoLibro.COMIC && !app.carpeta.contiene(libro.uriArchivo)) {
            val guardado = withContext(Dispatchers.IO) { copiarComicALaCarpeta(libro, peticion.origen) }
            if (guardado == null) {
                avisar(R.string.mover_error_copiar)
                terminarRecepcion(peticion)
                return
            }
            libro = guardado
            comicSinCarpetaPendiente = null
        }
        val siguiente = peticion.copy(libro = libro)
        val borrado = withContext(Dispatchers.IO) {
            ArchivosExternos.sePuedeBorrar(app, peticion.origen, app.carpeta) && ArchivosExternos.borrar(app, peticion.origen)
        }
        if (borrado) {
            avisar(R.string.mover_hecho)
            terminarRecepcion(siguiente)
        } else {
            // Con «Abrir con», Android casi nunca deja borrar el archivo: se abre el selector
            // en su carpeta para que el usuario lo toque una vez y la app pueda borrarlo.
            _pedirOriginal.value = siguiente
        }
    }

    /** El cómic se copia a la carpeta Libertalia y desde entonces se lee de ahí. */
    private suspend fun copiarComicALaCarpeta(libro: Libro, origen: Uri): Libro? = runCatching {
        val nombre = libro.nombreArchivo ?: "${libro.titulo}.${libro.formato.name.lowercase()}"
        val guardado = app.contentResolver.openInputStream(origen)?.use { entrada ->
            app.carpeta.guardarArchivo(nombre, tipoMimeDe(libro.formato), entrada)
        } ?: return null
        val actual = librosDao.buscarPorId(libro.id) ?: return null
        val nuevo = actual.copy(uriArchivo = guardado.uri.toString(), nombreArchivo = guardado.name, fechaModificacion = System.currentTimeMillis())
        librosDao.actualizar(nuevo)
        runCatching { app.contentResolver.releasePersistableUriPermission(origen, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        nuevo
    }.getOrNull()

    /** Hay que abrir el selector de Android en la carpeta del original para poder borrarlo. */
    private val _pedirOriginal = MutableStateFlow<MoverOriginal?>(null)
    val pedirOriginal: StateFlow<MoverOriginal?> = _pedirOriginal.asStateFlow()

    /** El usuario ha tocado (o no, si [elegido] es null) el original en el selector. */
    fun originalElegido(elegido: Uri?) {
        val peticion = _pedirOriginal.value ?: return
        _pedirOriginal.value = null
        viewModelScope.launch {
            if (elegido != null) {
                val libro = peticion.libro
                val borrado = withContext(Dispatchers.IO) {
                    // Solo si es el mismo archivo (mismo tamaño y misma huella).
                    OrigenComic.esElMismo(app, elegido, libro.tamanoBytes, libro.huella) &&
                        !app.carpeta.contieneDocumento(elegido) && ArchivosExternos.borrar(app, elegido)
                }
                avisar(if (borrado) R.string.mover_hecho else R.string.mover_no_borrado)
            }
            terminarRecepcion(peticion)
        }
    }

    /** Tras las preguntas: pedir el cómic si hace falta y abrir el libro. */
    private fun terminarRecepcion(peticion: MoverOriginal) {
        comicSinCarpetaPendiente?.let {
            comicSinCarpetaPendiente = null
            _comicSinCarpeta.value = it
            return
        }
        if (peticion.abrirDespues) _libroParaAbrir.value = peticion.libro
    }

    /**
     * El usuario ha vuelto a elegir (o no, si [archivo] es null) el cómic con el selector de
     * Android: así la app recibe permiso permanente sobre él. Se comprueba que sea el mismo
     * (por su tamaño y su huella) y desde entonces se abre desde ahí.
     */
    fun archivoDeComicElegido(archivo: Uri?) {
        val comic = _comicSinCarpeta.value ?: return
        _comicSinCarpeta.value = null
        if (archivo == null) return
        viewModelScope.launch {
            val mismo = withContext(Dispatchers.IO) {
                val resolver = app.contentResolver
                runCatching { resolver.takePersistableUriPermission(archivo, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val esElMismo = OrigenComic.esElMismo(app, archivo, comic.tamanoBytes, comic.huella)
                if (esElMismo) {
                    librosDao.actualizar(comic.copy(uriArchivo = archivo.toString(), fechaModificacion = System.currentTimeMillis()))
                } else {
                    runCatching { resolver.releasePersistableUriPermission(archivo, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                esElMismo
            }
            avisar(if (mismo) R.string.comic_archivo_ok else R.string.comic_archivo_otro)
        }
    }

    /** Un cómic que ya no se puede abrir (permiso perdido): se pide al usuario que lo vuelva a elegir. */
    fun pedirArchivoDeComic(comic: Libro) {
        _comicSinCarpeta.value = comic
    }

    fun libroAbierto() {
        _libroParaAbrir.value = null
    }

    fun resumenMostrado() {
        _resumen.value = null
    }

    fun avisoMostrado() {
        _aviso.value = null
    }

    private fun avisar(@StringRes texto: Int) {
        _aviso.value = texto
    }

    // ── Acciones del menú al mantener pulsado ──────────────────────────────

    /** Si el archivo del libro está en la carpeta de la app (se puede renombrar o borrar). */
    fun estaEnCarpeta(libro: Libro): Boolean = app.carpeta.contiene(libro.uriArchivo)

    /** Número de tomo de un cómic (null = sin número). */
    fun cambiarTomo(libro: Libro, tomo: Int?) {
        if (tomo == libro.tomo) return
        app.alcanceApp.launch { librosDao.actualizar(libro.copy(tomo = tomo, fechaModificacion = System.currentTimeMillis())) }
    }

    /** Sentido de lectura de un cómic («ltr» o «rtl»). */
    fun cambiarSentido(libro: Libro, sentido: String) {
        app.alcanceApp.launch {
            librosDao.actualizar(libro.copy(sentidoLectura = sentido, fechaModificacion = System.currentTimeMillis()))
        }
    }

    /** Un PDF se puede tratar como libro de texto o como cómic. */
    fun cambiarTipo(libro: Libro, tipo: TipoLibro) {
        app.alcanceApp.launch {
            val sentido = libro.sentidoLectura ?: SentidoLectura.IZQUIERDA_A_DERECHA
            librosDao.actualizar(
                libro.copy(
                    tipo = tipo,
                    sentidoLectura = if (tipo == TipoLibro.COMIC) sentido else libro.sentidoLectura,
                    fechaModificacion = System.currentTimeMillis(),
                )
            )
        }
    }

    fun cambiarEstado(libro: Libro, estado: EstadoLectura) {
        app.alcanceApp.launch { librosDao.cambiarEstado(libro.id, estado, System.currentTimeMillis()) }
    }

    /**
     * Guarda el título, el autor y la portada editados. Si [renombrarArchivo],
     * el archivo de la carpeta pasa a llamarse como el título (el contenido no cambia).
     */
    fun guardarDatos(libro: Libro, titulo: String, autor: String, nuevaPortada: Uri?, renombrarArchivo: Boolean) {
        app.alcanceApp.launch {
            var actualizado = libro.copy(
                titulo = titulo.trim().ifEmpty { libro.titulo },
                autor = autor.trim(),
                fechaModificacion = System.currentTimeMillis(),
            )
            if (nuevaPortada != null) {
                if (Portadas.guardarDesdeImagen(app, libro.id, nuevaPortada)) {
                    actualizado = actualizado.copy(tienePortada = true)
                } else {
                    avisar(R.string.aviso_error_portada)
                }
            }
            val uri = libro.uriArchivo
            if (renombrarArchivo && uri != null && app.carpeta.contiene(uri)) {
                val extension = libro.formato.name.lowercase()
                val renombrado = app.carpeta.renombrar(Uri.parse(uri), "${actualizado.titulo}.$extension")
                if (renombrado != null) {
                    actualizado = actualizado.copy(
                        uriArchivo = renombrado.uri.toString(),
                        nombreArchivo = renombrado.name,
                    )
                } else {
                    avisar(R.string.aviso_error_renombrar)
                }
            }
            librosDao.actualizar(actualizado)
        }
    }

    fun crearEstanteria(nombre: String, conLibro: Libro?) {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return
        app.alcanceApp.launch {
            val nueva = Estanteria(
                id = UUID.randomUUID().toString(),
                nombre = limpio,
                orden = estanteriasDao.ordenMaximo() + 1,
                fechaModificacion = System.currentTimeMillis(),
            )
            estanteriasDao.insertar(nueva)
            if (conLibro != null) estanteriasDao.anadirLibro(LibroEnEstanteria(conLibro.id, nueva.id))
        }
    }

    fun ponerEnEstanteria(libro: Libro, estanteria: Estanteria, dentro: Boolean) {
        app.alcanceApp.launch {
            val relacion = LibroEnEstanteria(libro.id, estanteria.id)
            if (dentro) estanteriasDao.anadirLibro(relacion) else estanteriasDao.quitarLibro(relacion)
        }
    }

    /**
     * Quita el libro de la biblioteca. Sus datos (página, estado, estanterías...) se guardan
     * ocultos: si se vuelve a añadir el mismo libro, lo recupera todo.
     * Si se pide, también se borra su archivo de la carpeta.
     */
    fun eliminar(libro: Libro, borrarArchivo: Boolean) {
        app.alcanceApp.launch {
            val uri = libro.uriArchivo
            // Solo se borran archivos de la carpeta de la app (nunca un cómic en su sitio original).
            if (borrarArchivo && uri != null && app.carpeta.contiene(uri) && !app.carpeta.borrar(Uri.parse(uri))) {
                avisar(R.string.aviso_error_borrar_archivo)
            }
            librosDao.eliminar(libro.id, System.currentTimeMillis())
            avisar(R.string.aviso_libro_eliminado)
        }
    }

    // ── Organizar estanterías ───────────────────────────────────────────────

    fun renombrarEstanteria(estanteria: Estanteria, nombre: String) {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return
        app.alcanceApp.launch { estanteriasDao.renombrar(estanteria.id, limpio, System.currentTimeMillis()) }
    }

    /** Sube o baja una estantería un puesto en el orden. */
    fun moverEstanteria(estanteria: Estanteria, haciaArriba: Boolean) {
        val lista = estanterias.value
        val posicion = lista.indexOfFirst { it.id == estanteria.id }
        val destino = if (haciaArriba) posicion - 1 else posicion + 1
        if (posicion < 0 || destino !in lista.indices) return
        val nuevoOrden = lista.toMutableList().apply { add(destino, removeAt(posicion)) }
        app.alcanceApp.launch {
            nuevoOrden.forEachIndexed { indice, e -> estanteriasDao.cambiarOrden(e.id, indice) }
        }
    }

    /** Borra la estantería (los libros siguen en la biblioteca). */
    fun eliminarEstanteria(estanteria: Estanteria) {
        if (_filtro.value == FiltroBiblioteca.PorEstanteria(estanteria.id)) _filtro.value = FiltroBiblioteca.Todos
        app.alcanceApp.launch { estanteriasDao.eliminar(estanteria) }
    }
}
