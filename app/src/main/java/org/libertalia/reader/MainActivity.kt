package org.libertalia.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import org.libertalia.reader.datos.TipoLibro
import org.libertalia.reader.comic.LectorComicActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import org.libertalia.reader.ui.ajustes.PantallaAcercaDe
import org.libertalia.reader.ui.ajustes.PantallaAjustes
import org.libertalia.reader.ui.ajustes.PantallaEstadisticas
import org.libertalia.reader.ui.ajustes.PantallaLiberarEspacio
import org.libertalia.reader.ui.copias.RutaExportar
import org.libertalia.reader.ui.copias.RutaImportar
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.libertalia.reader.biblioteca.BibliotecaViewModel
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.tarjetas.TarjetasActivity
import org.libertalia.reader.tarjetas.PantallaBuscarTarjetas
import org.libertalia.reader.tarjetas.PantallaEtiqueta
import org.libertalia.reader.tarjetas.BuscadorEtiquetas
import org.readium.r2.shared.publication.services.positions
import org.libertalia.reader.lector.LectorActivity
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.lector.LibroAbierto
import org.libertalia.reader.lector.modotexto.ModoTexto
import org.libertalia.reader.ui.PantallaElegirCarpeta
import org.libertalia.reader.ui.copias.RutaCopiaAutomatica
import org.libertalia.reader.ui.biblioteca.AccionesEstanterias
import org.libertalia.reader.ui.biblioteca.AccionesLibro
import org.libertalia.reader.ui.biblioteca.DialogoBorrarOriginales
import org.libertalia.reader.ui.biblioteca.DialogoArchivoPesado
import org.libertalia.reader.ui.biblioteca.PantallaBiblioteca
import org.libertalia.reader.ui.tema.TemaLibertalia
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** Primera pantalla de la app: la biblioteca (o elegir la carpeta, la primera vez). */
class MainActivity : AppCompatActivity() {

    private val app get() = application as LibertaliaApp
    private val biblioteca: BibliotecaViewModel by viewModels()

    private var carpetaElegida by mutableStateOf(false)
    private var abriendo = false
    private var mensaje by mutableStateOf<String?>(null)

    /** Para ir a otra pantalla desde fuera del NavHost (por ejemplo, «Exportar» en el menú del libro). */
    private var navegacionActual: NavHostController? = null

    /** Se ha tocado la notificación de «la copia automática ha fallado»: abrir su pantalla. */
    private var abrirCopiaAutomatica by mutableStateOf(false)

    /** Versión nueva en GitHub de la que hay que avisar («Buscar actualizaciones»). */
    private var versionNueva by mutableStateOf<org.libertalia.reader.actualizaciones.VersionNueva?>(null)

    /** Archivo recibido con «Abrir con» antes de haber elegido la carpeta. */
    private var archivoPendiente: Uri? = null

    /** Selector de carpetas de Android. */
    private val elegirCarpeta =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                app.carpeta.guardarEleccion(uri)
                carpetaElegida = app.carpeta.estaElegida()
                if (carpetaElegida) archivoPendiente?.let(::recibirArchivo)
            }
        }

    /**
     * Para un cómic que llegó con «Abrir con»: el selector de Android se abre en su carpeta
     * para que el usuario lo vuelva a elegir y la app reciba permiso permanente (ver OrigenComic).
     */
    private var uriInicialComic: Uri? = null
    private val elegirArchivoComic =
        registerForActivityResult(object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: android.content.Context, input: Array<String>): Intent =
                super.createIntent(context, input).apply {
                    uriInicialComic?.let { putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, it) }
                }
        }) { uri ->
            biblioteca.archivoDeComicElegido(uri)
        }

    /**
     * Para quitar el original de un archivo que llegó con «Abrir con»: Android no suele dejar
     * borrarlo con ese permiso, así que se abre el selector en su carpeta para tocarlo una vez.
     */
    private var uriInicialOriginal: Uri? = null
    private var pidiendoOriginal = false
    private val elegirOriginal =
        registerForActivityResult(object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: android.content.Context, input: Array<String>): Intent =
                super.createIntent(context, input).apply {
                    uriInicialOriginal?.let { putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, it) }
                }
        }) { uri ->
            pidiendoOriginal = false
            biblioteca.originalElegido(uri)
        }

    /** Las tarjetas de un libro; con «Ir al libro» se abre el libro justo en esa tarjeta. */
    private val abrirTarjetas = registerForActivityResult(TarjetasActivity.Abrir()) { destino ->
        destino ?: return@registerForActivityResult
        lifecycleScope.launch {
            val libro = app.baseDatos.libros().buscarPorId(destino.idLibro) ?: return@launch
            val tarjeta = app.baseDatos.tarjetas().buscarPorId(destino.idTarjeta)
            abrirLibro(libro, tarjeta)
        }
    }

    /** Selector de Android para elegir una copia .libertalia que importar. */
    private val elegirCopia =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) navegacionActual?.navigate("$RUTA_IMPORTAR?uri=${Uri.encode(uri.toString())}")
        }

    /** Selector de archivos de Android (se pueden elegir varios a la vez). */
    private val elegirLibros =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            biblioteca.importar(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val fondo = ContextCompat.getColor(this, R.color.fondo_app)
        // Iconos de las barras del sistema claros u oscuros según el aspecto de la app.
        val estiloBarras = if (org.libertalia.reader.ui.ajustes.AjustesApp.esOscuro(this)) SystemBarStyle.dark(fondo)
        else SystemBarStyle.light(fondo, fondo)
        enableEdgeToEdge(statusBarStyle = estiloBarras, navigationBarStyle = estiloBarras)
        super.onCreate(savedInstanceState)
        carpetaElegida = app.carpeta.estaElegida()
        // ¿Se ha abierto la app con «Abrir con» desde otra app?
        if (savedInstanceState == null) archivoRecibido(intent)?.let(::recibirArchivo)
        // ¿Hay que abrir un libro en una tarjeta (lo pide el lector, desde una etiqueta)?
        if (savedInstanceState == null) abrirTarjetaPedida(intent)
        if (savedInstanceState == null) abrirCopiaAutomatica = intent.getBooleanExtra(EXTRA_ABRIR_COPIA_AUTOMATICA, false)
        // Si la última copia automática falló, se avisa también dentro de la app.
        if (savedInstanceState == null && app.copiaAutomatica.info().falla) mensaje = getString(R.string.copia_fallo_aviso)
        // «Buscar actualizaciones» (si está activado): como mucho una vez al día, y se avisa
        // una sola vez de cada versión nueva.
        if (savedInstanceState == null && org.libertalia.reader.actualizaciones.Actualizaciones.tocaComprobar(app.preferencias)) {
            lifecycleScope.launch {
                val nueva = org.libertalia.reader.actualizaciones.Actualizaciones.comprobar(this@MainActivity, app.preferencias).getOrNull()
                if (nueva != null && nueva.version != app.preferencias.versionAvisada) versionNueva = nueva
            }
        }

        // Hay que tocar el original en el selector para poder quitarlo.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                biblioteca.pedirOriginal.collect { peticion ->
                    if (peticion != null && !pidiendoOriginal) {
                        pidiendoOriginal = true
                        uriInicialOriginal = peticion.origen
                        android.widget.Toast.makeText(this@MainActivity, R.string.mover_elige, android.widget.Toast.LENGTH_LONG).show()
                        elegirOriginal.launch(arrayOf("*/*"))
                    }
                }
            }
        }

        // Cuando haya que abrir un libro (por ejemplo, tras «Abrir con»), se abre.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                biblioteca.libroParaAbrir.collect { libro ->
                    if (libro != null) {
                        biblioteca.libroAbierto()
                        abrirLibro(libro)
                    }
                }
            }
        }

        setContent {
            TemaLibertalia {
                if (!carpetaElegida) {
                    PantallaElegirCarpeta(
                        alElegir = { elegirCarpeta.launch(app.carpeta.uriInicialSugerida()) },
                    )
                } else {
                    // Las pantallas de la app. Volver siempre regresa a la anterior
                    // conservando su estado.
                    val navegacion = rememberNavController()
                    LaunchedEffect(navegacion) { navegacionActual = navegacion }
                    LaunchedEffect(abrirCopiaAutomatica) {
                        if (abrirCopiaAutomatica) {
                            abrirCopiaAutomatica = false
                            navegacion.navigate(RUTA_COPIA_AUTOMATICA)
                        }
                    }
                    NavHost(navController = navegacion, startDestination = RUTA_BIBLIOTECA) {
                        composable(RUTA_BIBLIOTECA) {
                            val libros by biblioteca.libros.collectAsStateWithLifecycle()
                            val filtro by biblioteca.filtro.collectAsStateWithLifecycle()
                            val seguirLeyendo by biblioteca.seguirLeyendo.collectAsStateWithLifecycle()
                            val importando by biblioteca.importando.collectAsStateWithLifecycle()
                            val resumen by biblioteca.resumen.collectAsStateWithLifecycle()
                            val estanterias by biblioteca.estanterias.collectAsStateWithLifecycle()
                            val librosPorEstanteria by biblioteca.librosPorEstanteria.collectAsStateWithLifecycle()
                            val aviso by biblioteca.aviso.collectAsStateWithLifecycle()
                            val comicSinCarpeta by biblioteca.comicSinCarpeta.collectAsStateWithLifecycle()
                            val comicEnLaNube by biblioteca.comicEnLaNube.collectAsStateWithLifecycle()
                            val modoTarjetas by biblioteca.modoTarjetas.collectAsStateWithLifecycle()
                            val conteoTarjetas by biblioteca.conteoTarjetas.collectAsStateWithLifecycle()
                            // Archivo pesado recién añadido: ¿va en la copia automática? (boceto 14)
                            val pesados by biblioteca.pesados.collectAsStateWithLifecycle()
                            if (pesados.isNotEmpty() && comicSinCarpeta == null && comicEnLaNube == null) {
                                DialogoArchivoPesado(pesados.first(), alResponder = biblioteca::responderPesado)
                            }
                            // El libro añadido ya está en la carpeta: ¿borrar el original (p. ej. de Descargas)?
                            val originales by biblioteca.originalesParaBorrar.collectAsStateWithLifecycle()
                            if (originales.isNotEmpty() && pesados.isEmpty() && comicSinCarpeta == null && comicEnLaNube == null) {
                                DialogoBorrarOriginales(originales, alResponder = biblioteca::responderOriginales)
                            }
                            // Hay una versión nueva en GitHub.
                            versionNueva?.let { nueva ->
                                org.libertalia.reader.actualizaciones.DialogoActualizacion(nueva, alCerrar = {
                                    app.preferencias.versionAvisada = nueva.version
                                    versionNueva = null
                                })
                            }
                            // Un archivo nuevo llegó con «Abrir con»: ¿añadir a la biblioteca o solo leer?
                            val preguntaAnadir by biblioteca.preguntaAnadir.collectAsStateWithLifecycle()
                            preguntaAnadir?.let {
                                org.libertalia.reader.ui.biblioteca.DialogoAnadirOSoloLeer(it.nombre, alResponder = biblioteca::responderAnadir)
                            }
                            // Tras añadirlo: ¿quitar el original de su carpeta?
                            val moverOriginal by biblioteca.moverOriginal.collectAsStateWithLifecycle()
                            moverOriginal?.let {
                                org.libertalia.reader.ui.biblioteca.DialogoMoverOriginal(
                                    it.nombre,
                                    esComic = it.libro.tipo == TipoLibro.COMIC,
                                    alResponder = biblioteca::responderMoverOriginal,
                                )
                            }
                            // Ha llegado una copia .libertalia (con + o «Abrir con»): se ofrece importarla.
                            val copiaRecibida by biblioteca.copiaRecibida.collectAsStateWithLifecycle()
                            LaunchedEffect(copiaRecibida) {
                                copiaRecibida?.let { uri ->
                                    biblioteca.copiaAtendida()
                                    navegacion.navigate("$RUTA_IMPORTAR?uri=${Uri.encode(uri.toString())}")
                                }
                            }
                            // Cómic añadido desde una nube: se recomienda guardarlo en el móvil.
                            if (comicSinCarpeta == null) comicEnLaNube?.let { comic ->
                                AlertDialog(
                                    onDismissRequest = biblioteca::avisoNubeVisto,
                                    containerColor = colorResource(R.color.fondo_tarjeta),
                                    titleContentColor = colorResource(R.color.texto),
                                    textContentColor = colorResource(R.color.texto),
                                    title = { Text(stringResource(R.string.nube_titulo)) },
                                    text = { Text(stringResource(R.string.nube_texto, comic.titulo)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            biblioteca.guardarEnMovil(comic)
                                            biblioteca.avisoNubeVisto()
                                        }) { Text(stringResource(R.string.nube_guardar)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = biblioteca::avisoNubeVisto) {
                                            Text(stringResource(R.string.nube_seguir))
                                        }
                                    },
                                )
                            }
                            comicSinCarpeta?.let { comic ->
                                AlertDialog(
                                    onDismissRequest = { biblioteca.archivoDeComicElegido(null) },
                                    containerColor = colorResource(R.color.fondo_tarjeta),
                                    titleContentColor = colorResource(R.color.texto),
                                    textContentColor = colorResource(R.color.texto),
                                    title = { Text(stringResource(R.string.comic_carpeta_titulo)) },
                                    text = { Text(stringResource(R.string.comic_carpeta_texto, comic.titulo)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            uriInicialComic = comic.uriArchivo?.let(Uri::parse)
                                            elegirArchivoComic.launch(TIPOS_ADMITIDOS)
                                        }) {
                                            Text(stringResource(R.string.comic_carpeta_elegir))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { biblioteca.archivoDeComicElegido(null) }) {
                                            Text(stringResource(R.string.comic_carpeta_ahora_no))
                                        }
                                    },
                                )
                            }
                            LaunchedEffect(aviso) {
                                aviso?.let {
                                    mensaje = getString(it)
                                    biblioteca.avisoMostrado()
                                }
                            }
                            PantallaBiblioteca(
                                libros = libros,
                                estanterias = estanterias,
                                librosPorEstanteria = librosPorEstanteria,
                                filtro = filtro,
                                seguirLeyendo = seguirLeyendo,
                                importando = importando,
                                resumen = resumen,
                                mensaje = mensaje,
                                alAnadir = { elegirLibros.launch(TIPOS_ADMITIDOS) },
                                alAjustes = { navegacion.navigate(RUTA_AJUSTES) },
                                alImportarCopia = { elegirCopia.launch(arrayOf("*/*")) },
                                // En modo Tarjetas, tocar un libro abre sus tarjetas.
                                alAbrir = { libro ->
                                    if (modoTarjetas) {
                                        if (libro.tipo != TipoLibro.COMIC) abrirTarjetas.launch(TarjetasActivity.Abrir.Entrada(libro.id))
                                    } else {
                                        abrirLibro(libro)
                                    }
                                },
                                alElegirFiltro = biblioteca::elegirFiltro,
                                acciones = acciones,
                                accionesEstanterias = accionesEstanterias,
                                alResumenMostrado = biblioteca::resumenMostrado,
                                alMensajeMostrado = { mensaje = null },
                                conteoTarjetas = if (modoTarjetas) conteoTarjetas else null,
                                alAlternarModo = biblioteca::alternarModoTarjetas,
                                alBuscar = { navegacion.navigate(RUTA_BUSCAR) },
                                buscadorEtiquetas = {
                                    val tarjetas by biblioteca.todasLasTarjetas.collectAsStateWithLifecycle()
                                    BuscadorEtiquetas(
                                        tarjetas = tarjetas.orEmpty(),
                                        alElegir = { navegacion.navigate("$RUTA_ETIQUETA/${Uri.encode(it)}") },
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                                    )
                                },
                            )
                        }
                        // Buscar en las tarjetas de todos los libros.
                        composable(RUTA_BUSCAR) {
                            val libros by biblioteca.libros.collectAsStateWithLifecycle()
                            val tarjetas by biblioteca.todasLasTarjetas.collectAsStateWithLifecycle()
                            PantallaBuscarTarjetas(
                                libros = libros.orEmpty(),
                                tarjetas = tarjetas,
                                alVolver = { navegacion.popBackStack() },
                                alAbrir = { tarjeta ->
                                    abrirTarjetas.launch(TarjetasActivity.Abrir.Entrada(tarjeta.idLibro, tarjeta.id))
                                },
                            )
                        }
                        // Todas las tarjetas (de todos los libros) con una etiqueta.
                        composable("$RUTA_ETIQUETA/{nombre}") { entrada ->
                            val libros by biblioteca.libros.collectAsStateWithLifecycle()
                            val tarjetas by biblioteca.todasLasTarjetas.collectAsStateWithLifecycle()
                            PantallaEtiqueta(
                                etiqueta = Uri.decode(entrada.arguments?.getString("nombre").orEmpty()),
                                libros = libros.orEmpty(),
                                tarjetas = tarjetas.orEmpty(),
                                alVolver = { navegacion.popBackStack() },
                                alAbrir = { tarjeta ->
                                    abrirTarjetas.launch(TarjetasActivity.Abrir.Entrada(tarjeta.idLibro, tarjeta.id))
                                },
                                alEtiqueta = { navegacion.navigate("$RUTA_ETIQUETA/${Uri.encode(it)}") },
                            )
                        }
                        composable(RUTA_AJUSTES) {
                            PantallaAjustes(
                                alVolver = { navegacion.popBackStack() },
                                alExportar = { navegacion.navigate(RUTA_EXPORTAR) },
                                alImportar = { elegirCopia.launch(arrayOf("*/*")) },
                                infoCopia = app.copiaAutomatica.info(),
                                alCopiaAutomatica = { navegacion.navigate(RUTA_COPIA_AUTOMATICA) },
                                alLiberarEspacio = { navegacion.navigate("liberar-espacio") },
                                alAcercaDe = { navegacion.navigate("acerca-de") },
                                alEstadisticas = { navegacion.navigate("estadisticas") },
                            )
                        }
                        composable("estadisticas") { PantallaEstadisticas(alVolver = { navegacion.popBackStack() }) }
                        composable("liberar-espacio") { PantallaLiberarEspacio(alVolver = { navegacion.popBackStack() }) }
                        composable("acerca-de") { PantallaAcercaDe(alVolver = { navegacion.popBackStack() }) }
                        // Exportar: desde Ajustes (todos marcados) o desde el menú de un libro (solo ese).
                        composable(
                            "$RUTA_EXPORTAR?libro={libro}",
                            arguments = listOf(navArgument("libro") { type = NavType.StringType; nullable = true; defaultValue = null }),
                        ) { entrada ->
                            RutaExportar(
                                idInicial = entrada.arguments?.getString("libro"),
                                alVolver = { navegacion.popBackStack() },
                            )
                        }
                        // Importar una copia: resumen y, al confirmar, unirla con la biblioteca.
                        composable(
                            "$RUTA_IMPORTAR?uri={uri}",
                            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true; defaultValue = null }),
                        ) { entrada ->
                            // (La navegación ya devuelve el argumento descodificado.)
                            val uri = entrada.arguments?.getString("uri")?.let(Uri::parse)
                            if (uri != null) {
                                RutaImportar(
                                    uri = uri,
                                    alVolver = { navegacion.popBackStack() },
                                    alTerminar = { navegacion.popBackStack(RUTA_BIBLIOTECA, inclusive = false) },
                                )
                            }
                        }
                        composable(RUTA_COPIA_AUTOMATICA) {
                            RutaCopiaAutomatica(alVolver = { navegacion.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    /** Lo que se puede hacer desde el menú al mantener pulsado un libro. */
    private val acciones by lazy {
        AccionesLibro(
            cambiarEstado = biblioteca::cambiarEstado,
            guardarDatos = biblioteca::guardarDatos,
            crearEstanteria = biblioteca::crearEstanteria,
            ponerEnEstanteria = biblioteca::ponerEnEstanteria,
            eliminar = biblioteca::eliminar,
            cambiarTomo = biblioteca::cambiarTomo,
            cambiarIsbn = biblioteca::cambiarIsbn,
            cambiarSentido = biblioteca::cambiarSentido,
            cambiarTipo = biblioteca::cambiarTipo,
            estaEnCarpeta = biblioteca::estaEnCarpeta,
            estaEnLaNube = biblioteca::estaEnLaNube,
            guardarEnMovil = biblioteca::guardarEnMovil,
            verTarjetas = { abrirTarjetas.launch(TarjetasActivity.Abrir.Entrada(it.id)) },
            exportar = { navegacionActual?.navigate("$RUTA_EXPORTAR?libro=${it.id}") },
            cambiarIncluirEnCopia = biblioteca::cambiarIncluirEnCopia,
        )
    }

    /** Ordenar, renombrar y borrar las estanterías propias. */
    private val accionesEstanterias by lazy {
        AccionesEstanterias(
            renombrar = biblioteca::renombrarEstanteria,
            mover = biblioteca::moverEstanteria,
            eliminar = biblioteca::eliminarEstanteria,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // La app ya estaba abierta y llega otro archivo con «Abrir con».
        archivoRecibido(intent)?.let(::recibirArchivo)
        abrirTarjetaPedida(intent)
        if (intent.getBooleanExtra(EXTRA_ABRIR_COPIA_AUTOMATICA, false)) abrirCopiaAutomatica = true
    }

    /** El archivo que nos manda otra app: «Abrir con» (VIEW) o «Compartir» (SEND). */
    private fun archivoRecibido(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }

    private fun recibirArchivo(uri: Uri) {
        if (!carpetaElegida) {
            // Primero hay que elegir la carpeta; el libro se añadirá justo después.
            archivoPendiente = uri
            return
        }
        archivoPendiente = null
        biblioteca.recibirDeOtraApp(uri)
    }

    /** Si otra pantalla pide abrir un libro justo en una tarjeta, se abre. */
    private fun abrirTarjetaPedida(intent: Intent?) {
        val idLibro = intent?.getStringExtra(EXTRA_ABRIR_LIBRO) ?: return
        val idTarjeta = intent.getStringExtra(EXTRA_ABRIR_TARJETA)
        intent.removeExtra(EXTRA_ABRIR_LIBRO)
        lifecycleScope.launch {
            val libro = app.baseDatos.libros().buscarPorId(idLibro) ?: return@launch
            val tarjeta = idTarjeta?.let { app.baseDatos.tarjetas().buscarPorId(it) }
            abrirLibro(libro, tarjeta)
        }
    }

    override fun onResume() {
        super.onResume()
        // Si el usuario quitó el permiso o borró la carpeta, se vuelve a pedir.
        carpetaElegida = app.carpeta.estaElegida()
    }

    /** Si la app tiene permiso permanente sobre el archivo (o sobre la carpeta que lo contiene). */
    private fun tienePermisoPermanente(uri: Uri): Boolean = contentResolver.persistedUriPermissions.any { permiso ->
        permiso.isReadPermission && (permiso.uri == uri || uri.toString().startsWith("${permiso.uri}/document/"))
    }

    private fun sePuedeLeer(uri: Uri): Boolean = runCatching {
        contentResolver.openInputStream(uri)?.use { it.read() }
        true
    }.getOrDefault(false)

    /**
     * Abre un libro en su última página o, si se pasa una tarjeta ([destino]), justo en ella.
     * Los subrayados de un PDF se hicieron en modo texto: se abre en modo texto.
     */
    private fun abrirLibro(libro: Libro, destino: Tarjeta? = null) {
        if (abriendo) return
        val uri = libro.uriArchivo
        if (uri == null) {
            mensaje = getString(R.string.error_libro_sin_archivo)
            return
        }
        // Cómics y manga (también los PDF de cómic): su propio lector. Se abren desde donde
        // están; si la app ya no puede leerlo (el permiso era solo para un momento, o el archivo
        // se ha movido), se pide que se vuelva a elegir.
        // Ojo: abrir el archivo para comprobarlo puede tardar mucho (en Drive, lo descarga
        // entero), así que nunca se hace en el hilo de la pantalla.
        if (libro.tipo == TipoLibro.COMIC) {
            val direccion = Uri.parse(uri)
            if (tienePermisoPermanente(direccion)) {
                LectorComicActivity.abrir(this, libro.id)
            } else {
                lifecycleScope.launch {
                    val legible = withContext(Dispatchers.IO) { sePuedeLeer(direccion) }
                    if (legible) LectorComicActivity.abrir(this@MainActivity, libro.id) else biblioteca.pedirArchivoDeComic(libro)
                }
            }
            return
        }
        abriendo = true
        val sitioTarjeta = destino?.let { runCatching { Locator.fromJSON(JSONObject(it.posicion)) }.getOrNull() }
        val textoPreparado = libro.formato == Formato.PDF && ModoTexto.estaPreparado(this, libro.id)
        // Un PDF que se dejó en modo texto se vuelve a abrir en modo texto (y también para ir
        // a un subrayado, que se hizo en modo texto).
        val enTexto = textoPreparado && (ModoTexto.estaActivo(this, libro.id) || sitioTarjeta != null)
        val mapa = if (enTexto) ModoTexto.cargarMapa(this, libro.id) else null
        if (mapa != null && sitioTarjeta != null) ModoTexto.activar(this, libro.id, true)
        lifecycleScope.launch {
            val abierta = if (mapa != null) {
                app.servicioLectura.abrir(ModoTexto.archivoEpub(this@MainActivity, libro.id))
            } else {
                app.servicioLectura.abrir(Uri.parse(uri))
            }
            abierta
                .onSuccess { publicacion ->
                    // Se abre siempre en la última página leída (el marcapáginas
                    // se alcanza desde la barra inferior, tras confirmarlo).
                    val guardada = libro.posicion?.let {
                        runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
                    }
                    val posicion = if (sitioTarjeta != null && (mapa != null || libro.formato != Formato.PDF)) {
                        // Justo en la tarjeta.
                        sitioTarjeta
                    } else if (sitioTarjeta != null && destino != null) {
                        // PDF sin modo texto preparado: a la página del PDF de la tarjeta.
                        destino.pagina?.let { pagina -> publicacion.positions().getOrNull(pagina - 1) } ?: guardada
                    } else if (mapa != null) {
                        // En la base de datos está la página del PDF; si la posición exacta
                        // del modo texto es de esa misma página, se usa (es más precisa).
                        val pagina = guardada?.locations?.position ?: 1
                        ModoTexto.posicionGuardada(this@MainActivity, libro.id)
                            ?.takeIf { mapa.paginaDe(it) == pagina }
                            ?: mapa.posicionTexto(pagina, publicacion)
                    } else {
                        guardada
                    }
                    LibroAbierto.abrir(publicacion, libro, posicion, enModoTexto = mapa != null, mapa = mapa)
                    app.alcanceApp.launch {
                        app.baseDatos.libros().marcarAbierto(libro.id, System.currentTimeMillis())
                    }
                    startActivity(Intent(this@MainActivity, LectorActivity::class.java))
                }
                .onFailure { mensaje = getString(R.string.error_abrir_libro, it.message.orEmpty()) }
            abriendo = false
        }
    }

    companion object {
        private const val EXTRA_ABRIR_LIBRO = "abrir_libro"
        private const val EXTRA_ABRIR_TARJETA = "abrir_tarjeta"

        /** Para abrir un libro desde fuera (el widget «Seguir leyendo»), en su última página. */
        fun abrirLibroIntent(contexto: android.content.Context, idLibro: String): Intent =
            Intent(contexto, MainActivity::class.java)
                .setAction("org.libertalia.reader.ABRIR_LIBRO.$idLibro")
                .putExtra(EXTRA_ABRIR_LIBRO, idLibro)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Para que otra pantalla pida abrir un libro justo en una de sus tarjetas. */
        fun abrirTarjetaEnLibro(contexto: android.content.Context, idLibro: String, idTarjeta: String): Intent =
            Intent(contexto, MainActivity::class.java)
                .putExtra(EXTRA_ABRIR_LIBRO, idLibro)
                .putExtra(EXTRA_ABRIR_TARJETA, idTarjeta)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private const val RUTA_BIBLIOTECA = "biblioteca"
        private const val RUTA_COPIA_AUTOMATICA = "copia-automatica"

        /** Extra de la notificación de la copia automática: abrir su pantalla. */
        const val EXTRA_ABRIR_COPIA_AUTOMATICA = "abrir_copia_automatica"
        private const val RUTA_AJUSTES = "ajustes"
        private const val RUTA_EXPORTAR = "exportar"
        private const val RUTA_IMPORTAR = "importar"
        private const val RUTA_BUSCAR = "buscar-tarjetas"
        private const val RUTA_ETIQUETA = "etiqueta"

        /**
         * Tipos de archivo que ofrece el selector: todos. Cada app y cada nube llama de una
         * forma distinta a los CBR y CBZ («application/x-rar», «application/rar»...), y si
         * no se acierta el selector los muestra en gris. La app comprueba después qué es en
         * realidad cada archivo y avisa si no es un libro ni un cómic.
         */
        private val TIPOS_ADMITIDOS = arrayOf("*/*")
    }
}
