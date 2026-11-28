package org.libertalia.reader.lector

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.R
import org.libertalia.reader.biblioteca.LibroDePaso
import org.libertalia.reader.biblioteca.esDePaso
import org.libertalia.reader.datos.ColorSubrayado
import org.libertalia.reader.datos.TipoTarjeta
import org.libertalia.reader.tarjetas.TarjetasActivity
import org.libertalia.reader.racha.MedidorLectura
import org.libertalia.reader.MainActivity
import org.libertalia.reader.ui.tema.TemaLibertalia
import org.libertalia.reader.lector.modotexto.ModoTexto
import org.libertalia.reader.voz.LecturaEnVoz
import org.readium.r2.navigator.VisualNavigator
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Pantalla del lector de libros (EPUB y PDF).
 * Readium dibuja el libro dentro de "contenedor_navegador".
 */
@OptIn(ExperimentalReadiumApi::class)
class LectorActivity : AppCompatActivity() {

    /** Estado de la pantalla: barras, posición, índice, búsqueda y ajustes. */
    private val estado: LectorViewModel by viewModels()

    /** El "navegador" de Readium, que es el que muestra el libro y pasa las páginas. */
    private var navegador: OverflowableNavigator? = null

    /** La vista del PDF, para ponerle o quitarle el filtro de color. */
    private var vistaPdf: View? = null

    /** Los últimos ajustes aplicados, para saber qué ha cambiado. */
    private var ajustesAplicados: AjustesLectura? = null

    private var trabajoMedida: Job? = null

    /** La publicación que muestra este lector (para cerrarla al cambiar de modo). */
    private var publicacionPropia: Publication? = null

    /** Este lector se cierra para abrir el mismo libro en el otro modo (PDF ↔ texto). */
    private var cambiandoDeModo = false

    /** Mide el tiempo que se pasa leyendo (para la racha). */
    private val medidor by lazy { MedidorLectura(application as LibertaliaApp, comic = estado.esComic) }

    /** Al abrir: empezar a leer en voz alta en cuanto se vea el libro (se pulsó «Escuchar» en un PDF). */
    private var escucharAlAbrir = false

    override fun onResume() {
        super.onResume()
        // Mientras suena la lectura en voz alta, el tiempo ya lo cuenta ella (no dos veces).
        // Con «Solo leer» no se cuenta nada (ni racha ni estadísticas).
        if (!LecturaEnVoz.activa.value && !estado.soloLeer) medidor.empezar()
    }

    override fun onPause() {
        medidor.parar()
        super.onPause()
    }

    /** Las tarjetas del libro; al volver con «Ir al libro», se va justo a esa tarjeta. */
    private val abrirTarjetas = registerForActivityResult(TarjetasActivity.Abrir()) { destino ->
        destino ?: return@registerForActivityResult
        if (destino.idLibro != LibroAbierto.libro?.id) {
            // Es de otro libro (llegó desde una etiqueta): lo abre la biblioteca y este lector se cierra.
            startActivity(MainActivity.abrirTarjetaEnLibro(this, destino.idLibro, destino.idTarjeta))
            finish()
            return@registerForActivityResult
        }
        estado.destinoDeTarjeta(destino.idTarjeta)?.let { navegador?.go(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val publicacion = LibroAbierto.publicacion
        if (publicacion == null) {
            // Android cerró la app en segundo plano y el libro ya no está en memoria.
            super.onCreate(null)
            finish()
            return
        }
        publicacionPropia = publicacion
        escucharAlAbrir = savedInstanceState == null && intent.getBooleanExtra(EXTRA_ESCUCHAR, false)
        val esPdf = publicacion.conformsTo(Publication.Profile.PDF)
        // Empieza donde se dejó la lectura (o al girar el móvil, donde se estaba).
        val posicionInicial = LibroAbierto.ultimaPosicion
        val ajustesIniciales = AjustesLectura.paraLibro(this, LibroAbierto.libro?.ajustesTexto)

        // Readium necesita esta "fábrica" antes de crear la pantalla.
        supportFragmentManager.fragmentFactory =
            if (esPdf) fabricaPdf(publicacion, posicionInicial) else fabricaEpub(publicacion, posicionInicial, ajustesIniciales)

        // Cuando Readium termina de crear su vista, la preparamos (gestos y filtro de color).
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?,
                ) {
                    if (f !is OverflowableNavigator) return
                    navegador = f
                    // Tocar el borde derecho o izquierdo pasa de página.
                    f.addInputListener(DirectionalNavigationAdapter(f))
                    // Tocar en el centro muestra u oculta la barra inferior.
                    f.addInputListener(object : InputListener {
                        override fun onTap(event: TapEvent): Boolean {
                            estado.alternarBarraInferior()
                            return true
                        }
                    })
                    if (esPdf) {
                        vistaPdf = v
                        aplicarFiltroPdf(v, estado.ajustes)
                    }
                    seguirPosicion(f)
                    if (!esPdf) {
                        mostrarMarcapaginas()
                        if (estado.sePuedeSubrayar) mostrarSubrayados()
                        seguirVoz()
                        if (escucharAlAbrir) {
                            escucharAlAbrir = false
                            // Un momento, para que el texto esté ya colocado en la pantalla.
                            lifecycleScope.launch {
                                delay(600)
                                escuchar()
                            }
                        }
                    }
                }
            },
            false,
        )

        aplicarBarrasDelSistema(ajustesIniciales.tema)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lector)

        ajustarLibroAlHueco()

        // Cada vez que cambia algo en el panel Aa, se aplica al momento.
        lifecycleScope.launch {
            snapshotFlow { estado.ajustes }.collect(::aplicarAjustes)
        }

        // Capa de controles (barras, índice, búsqueda, ajustes) encima del libro.
        val libro = LibroAbierto.libro
        findViewById<ComposeView>(R.id.capa_controles).setContent {
            TemaLibertalia {
                TemaControlesLector(estado.ajustes.tema) {
                    CapaLector(
                        estado = estado,
                        titulo = libro?.titulo ?: publicacion.metadata.title.orEmpty(),
                        autor = libro?.autor.orEmpty(),
                        acciones = AccionesLector(
                            volver = ::finish,
                            irA = ::irAResultado,
                            irAEnlace = { navegador?.go(it) },
                            irAPagina = { pagina -> estado.posicionDePagina(pagina)?.let { navegador?.go(it) } },
                            irAMarcapaginas = { estado.marcapaginas?.let { navegador?.go(it) } },
                            irAPosicion = { navegador?.go(it) },
                            abrirTarjetas = {
                                LibroAbierto.libro?.id?.let { abrirTarjetas.launch(TarjetasActivity.Abrir.Entrada(it)) }
                            },
                            cambiarModo = { cambiarModo() },
                            cambiarNotas = ::cambiarNotas,
                            escuchar = ::escuchar,
                            seleccion = { id -> accionSeleccion(id, null) },
                        ),
                    )
                }
            }
        }

        if (savedInstanceState == null) {
            val claseNavegador =
                if (esPdf) PdfNavigatorFragment::class.java else EpubNavigatorFragment::class.java
            supportFragmentManager.commit {
                add(R.id.contenedor_navegador, claseNavegador, Bundle(), ETIQUETA_NAVEGADOR)
            }
        }
    }

    /**
     * Botones de volumen: bajar = página siguiente, subir = página anterior
     * (si está activado en el panel Aa y no hay una ventana abierta encima).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val esVolumen = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val nav = navegador
        if (esVolumen && nav != null && estado.ajustes.pasarConVolumen && !hayVentanaAbierta()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) nav.goForward(animated = true)
                else nav.goBackward(animated = true)
            }
            // Se "consume" la tecla para que no cambie el volumen del móvil.
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hayVentanaAbierta(): Boolean =
        estado.busquedaAbierta || estado.indiceAbierto || estado.ajustesAbiertos || estado.confirmarMarcapaginas ||
            estado.subrayadoAbierto != null || estado.notaAbierta != null || estado.destacadaAbierta != null ||
            estado.listaDestacadasAbierta

    /** Aplica los ajustes del panel Aa al libro, a la ventana y a las barras del sistema. */
    private fun aplicarAjustes(ajustes: AjustesLectura) {
        // Si cambia algo que mueve el texto, hay que volver a contar las páginas.
        val primeraVez = ajustesAplicados == null
        ajustesAplicados = ajustes
        if (estado.usarFirma(firmaDe(ajustes)) && !primeraVez) medirCapitulo(espera = 700)

        val fondo = ContextCompat.getColor(this, ajustes.tema.fondo)
        findViewById<View>(R.id.raiz_lector).setBackgroundColor(fondo)
        window.setBackgroundDrawable(ColorDrawable(fondo))
        aplicarBarrasDelSistema(ajustes.tema)

        // Brillo solo dentro de la app (no cambia el del móvil).
        window.attributes = window.attributes.apply {
            screenBrightness = ajustes.brillo ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (ajustes.pantallaEncendida) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        vistaPdf?.let { aplicarFiltroPdf(it, ajustes) }
        (navegador as? EpubNavigatorFragment)?.submitPreferences(preferenciasEpub(ajustes))
    }

    /** Todo lo que cambia cuántas pantallas ocupa el texto. */
    private fun firmaDe(a: AjustesLectura): String {
        val pantalla = resources.displayMetrics
        return "${a.tamanoLetra}|${a.tipografia}|${a.margenes}|${a.interlineado}|" +
            "${pantalla.widthPixels}x${pantalla.heightPixels}"
    }

    /**
     * Cuenta cuántas pantallas ocupa el capítulo abierto, con la letra actual,
     * preguntándoselo a la página web en la que Readium muestra el EPUB.
     * Se espera un momento para que el texto esté ya colocado.
     */
    private fun medirCapitulo(espera: Long = 200) {
        val epub = navegador as? EpubNavigatorFragment ?: return
        trabajoMedida?.cancel()
        trabajoMedida = lifecycleScope.launch {
            delay(espera)
            // Que la página no tiemble al seleccionar (ver JS_SIN_TEMBLOR). Cada capítulo es una
            // página web nueva, así que se mira en cada cambio de sitio (solo se instala una vez).
            runCatching { epub.evaluateJavascript(JS_SIN_TEMBLOR) }
            val href = estado.posicion?.href?.toString() ?: return@launch
            val respuesta = runCatching { epub.evaluateJavascript(JS_PANTALLAS) }.getOrNull()
            val pantallas = respuesta?.trim('"', ' ')?.toDoubleOrNull()?.toInt() ?: return@launch
            estado.capituloMedido(href, pantallas)
        }
    }

    /** Iconos de la barra de estado oscuros sobre fondo claro, o claros en el tema oscuro. */
    private fun aplicarBarrasDelSistema(tema: TemaLector) {
        val barra = ContextCompat.getColor(this, tema.barra)
        val estilo = if (tema.oscuro) SystemBarStyle.dark(barra) else SystemBarStyle.light(barra, barra)
        enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
    }

    override fun onStop() {
        super.onStop()
        // Al salir del lector o de la app, se guarda la posición en ese mismo momento.
        if (!cambiandoDeModo) LibroAbierto.ultimaPosicion?.let(::guardarPosicion)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Al salir del lector (no al girar el móvil), se cierra el libro. Si ya hay otro abierto
        // (se ha cambiado de modo, o se va a otro libro desde una etiqueta), se cierra
        // únicamente lo que mostraba este lector, sin tocar el nuevo.
        if (isFinishing) {
            // Al salir del lector se deja de leer en voz alta (el libro se cierra).
            if (LecturaEnVoz.publicacion === publicacionPropia) LecturaEnVoz.parar()
            val libro = LibroAbierto.libro
            if (cambiandoDeModo || LibroAbierto.publicacion !== publicacionPropia) {
                publicacionPropia?.close()
            } else {
                LibroAbierto.cerrar()
                // «Solo leer»: al cerrar no queda nada (ni su copia ni lo que preparó el modo texto).
                if (libro?.esDePaso == true) LibroDePaso.limpiar(this, libro.id)
            }
        }
    }

    /**
     * Botón de la barra superior en los PDF: PDF original ↔ modo texto, en la misma página.
     * [conVoz]: al abrir el modo texto, empezar a leer en voz alta (se pulsó «Escuchar» en el PDF).
     */
    private fun cambiarModo(conVoz: Boolean = false) {
        val libro = LibroAbierto.libro ?: return
        if (LibroAbierto.modoTexto) {
            val pagina = LibroAbierto.ultimaPosicion?.let { LibroAbierto.mapaTexto?.paginaDe(it) } ?: 1
            abrirEnModo(texto = false, pagina = pagina)
            return
        }
        val pagina = estado.posicion?.locations?.position ?: 1
        if (ModoTexto.estaPreparado(this, libro.id)) {
            abrirEnModo(texto = true, pagina = pagina, conVoz = conVoz)
        } else {
            val hrefPdf = publicacionPropia?.readingOrder?.firstOrNull()?.href?.toString() ?: return
            estado.prepararModoTexto(hrefPdf) { abrirEnModo(texto = true, pagina = pagina, conVoz = conVoz) }
        }
    }

    /**
     * Botón «Escuchar»: lee en voz alta desde la primera frase que se ve. Readium no sabe leer
     * en voz alta el PDF original, así que en un PDF se pasa antes a su modo texto.
     */
    private fun escuchar() {
        if (estado.esPdf) {
            cambiarModo(conVoz = true)
            return
        }
        val publicacion = publicacionPropia ?: return
        val idLibro = LibroAbierto.libro?.id ?: return
        lifecycleScope.launch {
            val desde = (navegador as? VisualNavigator)?.firstVisibleElementLocator() ?: LibroAbierto.ultimaPosicion
            medidor.parar()
            LecturaEnVoz.empezar(application as LibertaliaApp, publicacion, desde, idLibro)
            // Se pliega la barra inferior para dejar ver el texto y el panel de la voz.
            estado.plegarInferior(true)
        }
    }

    /**
     * Mientras se lee en voz alta: la frase que suena se resalta y el libro pasa de página
     * solo para seguirla. Al parar, se quita el resaltado y vuelve a contar el tiempo el lector.
     */
    private fun seguirVoz() {
        val decorable = navegador as? DecorableNavigator ?: return
        val color = ContextCompat.getColor(this, R.color.voz_frase)
        lifecycleScope.launch {
            LecturaEnVoz.frase.collect { frase ->
                if (frase == null || LecturaEnVoz.publicacion !== publicacionPropia) {
                    decorable.applyDecorations(emptyList(), group = GRUPO_VOZ)
                } else {
                    decorable.applyDecorations(
                        listOf(Decoration(id = "frase", locator = frase, style = Decoration.Style.Highlight(tint = color))),
                        group = GRUPO_VOZ,
                    )
                    navegador?.go(frase, animated = false)
                }
            }
        }
        lifecycleScope.launch {
            LecturaEnVoz.activa.collect { activa ->
                val delante = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                if (!activa && delante && !estado.soloLeer) medidor.empezar()
            }
        }
    }

    /**
     * Interruptor «notas al pie» del panel Aa (modo texto): se guarda para este libro y se
     * vuelve a abrir el texto con o sin notas separadas, en la misma página del PDF.
     */
    private fun cambiarNotas(activas: Boolean) {
        val libro = LibroAbierto.libro ?: return
        val mapa = LibroAbierto.mapaTexto ?: return
        val pagina = LibroAbierto.ultimaPosicion?.let { mapa.paginaDe(it) } ?: 1
        ModoTexto.activarNotas(this, libro.id, activas)
        estado.notasActivas = activas
        if (ModoTexto.estaPreparado(this, libro.id)) {
            abrirEnModo(texto = true, pagina = pagina)
        } else {
            estado.prepararModoTexto(mapa.hrefPdf) { abrirEnModo(texto = true, pagina = pagina) }
        }
    }

    /** Abre el mismo libro en el otro modo y cierra este lector. */
    private fun abrirEnModo(texto: Boolean, pagina: Int, conVoz: Boolean = false) {
        val libro = LibroAbierto.libro ?: return
        val app = application as LibertaliaApp
        lifecycleScope.launch {
            val mapa = if (texto) ModoTexto.cargarMapa(this@LectorActivity, libro.id) else LibroAbierto.mapaTexto
            if (mapa == null) {
                estado.avisoModoTexto = R.string.modo_texto_error
                return@launch
            }
            val abierta = if (texto) {
                app.servicioLectura.abrir(ModoTexto.archivoEpub(this@LectorActivity, libro.id))
            } else {
                app.servicioLectura.abrir(Uri.parse(libro.uriArchivo ?: return@launch))
            }
            val nueva = abierta.getOrElse {
                estado.avisoModoTexto = R.string.modo_texto_error
                return@launch
            }
            // Se guarda por dónde se iba antes de cambiar.
            LibroAbierto.ultimaPosicion?.let(::guardarPosicion)
            val posicion = if (texto) mapa.posicionTexto(pagina, nueva) else mapa.posicionPdf(pagina)
            ModoTexto.activar(this@LectorActivity, libro.id, texto)
            cambiandoDeModo = true
            LibroAbierto.cambiarModo(nueva, posicion, texto, mapa)
            startActivity(Intent(this@LectorActivity, LectorActivity::class.java).putExtra(EXTRA_ESCUCHAR, conVoz))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /**
     * Cada vez que cambia la página, se apunta la posición. A la base de datos
     * se guarda como mucho una vez por segundo, para no escribir a cada paso.
     */
    @OptIn(FlowPreview::class)
    private fun seguirPosicion(navegador: Navigator) {
        lifecycleScope.launch {
            navegador.currentLocator
                // Al cambiar de modo, lo que llegue del lector viejo ya no cuenta.
                .filter { !cambiandoDeModo }
                .onEach {
                    LibroAbierto.ultimaPosicion = it
                    // Para la velocidad de lectura: si se ha pasado justo a la página siguiente,
                    // cuentan las palabras de la página que se acaba de leer.
                    val antes = estado.paginacion
                    val sitioAntes = estado.posicion
                    estado.posicionCambiada(it)
                    val despues = estado.paginacion
                    val palabras = if (antes != null && despues != null && sitioAntes != null &&
                        despues.pagina == antes.pagina + 1
                    ) estado.palabrasDePagina(sitioAntes) else 0
                    if (!LecturaEnVoz.activa.value) medidor.actividad(palabras)
                    medirCapitulo()
                }
                .debounce(1_000)
                .collect(::guardarPosicion)
        }
    }

    /**
     * Hueco fijo para el libro: desde debajo de la barra superior (siempre visible) hasta
     * encima de la pestañita. La barra inferior, al desplegarse, sube por encima del texto
     * sin moverlo: así el texto nunca se reordena y no se pierde la línea que se lee.
     */
    private fun ajustarLibroAlHueco() {
        val marco = findViewById<View>(R.id.marco_libro)
        val densidad = resources.displayMetrics.density
        val altoBarra = ((ALTO_BARRA_SUPERIOR_DP + ALTO_LINEA_BARRA_DP) * densidad).toInt()
        val altoPestana = (ALTO_PESTANA_DP * densidad).toInt()
        // Mientras se escucha, se deja sitio también al panel de la voz, para que la frase que
        // suena nunca quede debajo (el texto se recoloca solo al empezar y al parar de escuchar).
        val altoPanelVoz = (ALTO_PANEL_VOZ_DP * densidad).toInt()
        var sistemaArriba = 0
        var sistemaAbajo = 0
        var conVoz = LecturaEnVoz.activa.value

        fun aplicar() {
            val arriba = sistemaArriba + altoBarra
            val abajo = sistemaAbajo + altoPestana + if (conVoz) altoPanelVoz else 0
            if (marco.paddingTop != arriba || marco.paddingBottom != abajo) {
                marco.setPadding(0, arriba, 0, abajo)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(marco) { _, insets ->
            val sistema = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            sistemaArriba = sistema.top
            sistemaAbajo = sistema.bottom
            aplicar()
            insets
        }
        lifecycleScope.launch {
            LecturaEnVoz.activa.collect { activa ->
                conVoz = activa && LecturaEnVoz.publicacion === publicacionPropia
                aplicar()
            }
        }
    }

    /**
     * Menú que sale al seleccionar texto en un EPUB: los cuatro colores de subrayado
     * (solo donde se puede subrayar), diccionario, copiar y marcapáginas.
     * El diccionario y copiar no necesitan subrayar antes. Las notas se ponen tocando
     * un texto ya subrayado.
     */
    private val menuSeleccion = object : ActionMode.Callback {
        override fun onCreateActionMode(modo: ActionMode, menu: Menu): Boolean {
            var orden = 0
            if (estado.sePuedeSubrayar) {
                ColorSubrayado.entries.forEachIndexed { i, color ->
                    menu.add(Menu.NONE, ID_PRIMER_COLOR + i, orden++, nombreConPunto(color))
                }
            }
            menu.add(Menu.NONE, ID_DICCIONARIO, orden++, R.string.seleccion_diccionario)
            menu.add(Menu.NONE, ID_COPIAR, orden++, R.string.seleccion_copiar)
            // («Solo leer»: no se guarda nada, tampoco el marcapáginas.)
            if (!estado.soloLeer) menu.add(Menu.NONE, ID_MARCAPAGINAS, orden, R.string.seleccion_marcapaginas)
            return true
        }

        override fun onPrepareActionMode(modo: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(modo: ActionMode, opcion: MenuItem): Boolean =
            accionSeleccion(opcion.itemId, modo)

        override fun onDestroyActionMode(modo: ActionMode) {}
    }

    /**
     * Lo que hace cada opción con el texto seleccionado (subrayar en un color, diccionario,
     * copiar, marcapáginas). La usan el menú de Android y la barra propia ([LectorViewModel.barraSeleccion]).
     */
    private fun accionSeleccion(id: Int, modo: ActionMode?): Boolean {
        val seleccionable = navegador as? SelectableNavigator ?: return false
        val color = ColorSubrayado.entries.getOrNull(id - ID_PRIMER_COLOR)
        if (color == null && id != ID_DICCIONARIO && id != ID_COPIAR && id != ID_MARCAPAGINAS) return false
        estado.barraSeleccion = false
        lifecycleScope.launch {
            // Solo cuenta lo que se ve en la pantalla y sin espacios en los extremos
            // (ver JS_RECORTAR_SELECCION).
            runCatching { (navegador as? EpubNavigatorFragment)?.evaluateJavascript(JS_RECORTAR_SELECCION) }
            // Primero se lee lo seleccionado y después se cierra el menú.
            val seleccion = seleccionable.currentSelection()
            // El párrafo donde empieza la selección (para unir solos los subrayados de un
            // mismo párrafo). Hay que preguntarlo antes de quitar la selección.
            val parrafo = if (color != null) {
                runCatching { (navegador as? EpubNavigatorFragment)?.evaluateJavascript(JS_PARRAFO) }
                    .getOrNull()?.trim('"', ' ')?.toDoubleOrNull()?.toInt()
            } else null
            modo?.finish()
            seleccionable.clearSelection()
            seleccion ?: return@launch
            val texto = seleccion.locator.text.highlight.orEmpty().trim()
            when {
                color != null -> {
                    val alto = (navegador as? Fragment)?.view?.height?.takeIf { it > 0 }
                    val altura = seleccion.rect?.top?.let { arriba -> alto?.let { arriba.toDouble() / it } } ?: 0.0
                    estado.subrayar(seleccion.locator, color, altura, parrafo)
                }
                id == ID_DICCIONARIO -> abrirDiccionario(texto)
                id == ID_COPIAR -> getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), texto))
                else -> {
                    estado.ponerMarcapaginas(seleccion.locator)
                    Toast.makeText(this@LectorActivity, R.string.marcapaginas_puesto, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    /**
     * Avisos que manda la página del libro (JS_SIN_TEMBLOR). Cuando la selección se sale de la
     * pantalla, la página la recorta a lo que se ve; al hacerlo, Android quita sus tiradores y su
     * menú, así que la app enseña su propia barra con las mismas opciones.
     */
    inner class PuenteSeleccion {
        @android.webkit.JavascriptInterface
        fun seleccionRecortada() {
            runOnUiThread { estado.barraSeleccion = true }
        }

        @android.webkit.JavascriptInterface
        fun seleccionQuitada() {
            runOnUiThread { estado.barraSeleccion = false }
        }
    }

    /** «● Amarillo», con el punto pintado de su color, para el menú de selección. */
    private fun nombreConPunto(color: ColorSubrayado): CharSequence {
        val texto = SpannableString("● " + getString(color.nombre))
        texto.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, color.color)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return texto
    }

    /**
     * Busca la palabra en un diccionario del móvil, con las «opciones de texto» de Android
     * (las mismas que salen al seleccionar texto en otras apps). Se dejan fuera los traductores.
     */
    private fun abrirDiccionario(texto: String) {
        if (texto.isEmpty()) return
        val peticion = Intent(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, texto)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        val apps = packageManager.queryIntentActivities(peticion, 0)
            .filterNot { it.activityInfo.packageName == packageName }
        val (traductores, otras) = apps.partition { app ->
            val nombre = app.loadLabel(packageManager).toString().lowercase() + " " + app.activityInfo.packageName.lowercase()
            listOf("translat", "traduc", "tradu").any { it in nombre }
        }
        // Siempre se muestra la lista y elige el usuario: aunque haya una sola app, puede no ser
        // un diccionario (por ejemplo, un asistente) y no se le debe mandar el texto sin preguntar.
        when {
            otras.isEmpty() -> Toast.makeText(this, R.string.diccionario_ninguno, Toast.LENGTH_LONG).show()
            else -> {
                val eleccion = Intent.createChooser(peticion, getString(R.string.diccionario_elegir))
                    .putExtra(
                        Intent.EXTRA_EXCLUDE_COMPONENTS,
                        traductores.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toTypedArray(),
                    )
                runCatching { startActivity(eleccion) }
            }
        }
    }

    /** Dibuja los subrayados del libro (y los vuelve a dibujar cuando cambian). Tocar uno abre su menú. */
    private fun mostrarSubrayados() {
        val decorable = navegador as? DecorableNavigator ?: return
        decorable.addDecorationListener(
            GRUPO_SUBRAYADOS,
            object : DecorableNavigator.Listener {
                override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                    estado.subrayadoTocado(event.decoration.id)
                    return true
                }
            },
        )
        lifecycleScope.launch {
            snapshotFlow { estado.tarjetas }.collect { tarjetas ->
                val subrayados = tarjetas.filter { it.tipo == TipoTarjeta.SUBRAYADO && it.color != null }.mapNotNull { tarjeta ->
                    val sitio = estado.locatorDe(tarjeta) ?: return@mapNotNull null
                    Decoration(
                        id = tarjeta.id,
                        locator = sinBordesVacios(sitio),
                        style = Decoration.Style.Highlight(
                            tint = ContextCompat.getColor(this@LectorActivity, tarjeta.color!!.color),
                        ),
                    )
                }
                decorable.applyDecorations(subrayados, group = GRUPO_SUBRAYADOS)
            }
        }
    }

    /**
     * Los espacios y saltos de línea de los bordes del texto subrayado pasan al texto de antes
     * o de después. Si no, Readium dibuja un recuadro vacío en la línea siguiente (pasaba con
     * subrayados guardados antes de recortar la selección al subrayar).
     */
    private fun sinBordesVacios(sitio: Locator): Locator {
        val texto = sitio.text
        val subrayado = texto.highlight ?: return sitio
        val recortado = subrayado.trim()
        if (recortado.isEmpty() || recortado.length == subrayado.length) return sitio
        val inicio = subrayado.indexOf(recortado)
        return sitio.copy(
            text = texto.copy(
                before = texto.before.orEmpty() + subrayado.substring(0, inicio),
                highlight = recortado,
                after = subrayado.substring(inicio + recortado.length) + texto.after.orEmpty(),
            ),
        )
    }

    /** Dibuja la cintita del marcapáginas (y la mueve cuando se pone en otra frase). */
    private fun mostrarMarcapaginas() {
        val decorable = navegador as? DecorableNavigator ?: return
        val color = ContextCompat.getColor(this, R.color.rojo)
        lifecycleScope.launch {
            snapshotFlow { estado.marcapaginas }.collect { frase ->
                val marcas = frase?.let {
                    listOf(Decoration(id = "marcapaginas", locator = it, style = EstiloMarcapaginas(color)))
                }.orEmpty()
                decorable.applyDecorations(marcas, group = "marcapaginas")
            }
        }
    }

    /** Va a un resultado de la búsqueda y lo resalta en amarillo. */
    private fun irAResultado(resultado: Locator) {
        val nav = navegador ?: return
        // Se pliega la barra inferior para que no tape el resultado si está al final de la página.
        estado.plegarInferior(true)
        nav.go(resultado)
        (nav as? DecorableNavigator)?.let { decorable ->
            lifecycleScope.launch {
                decorable.applyDecorations(
                    listOf(
                        Decoration(
                            id = "resultado",
                            locator = resultado,
                            style = Decoration.Style.Highlight(
                                tint = ContextCompat.getColor(this@LectorActivity, R.color.estado_leyendo),
                            ),
                        )
                    ),
                    group = "busqueda",
                )
            }
        }
    }

    private fun guardarPosicion(posicion: Locator) {
        val libro = LibroAbierto.libro ?: return
        val app = application as LibertaliaApp
        // En modo texto se guarda la página del PDF (así la biblioteca y «Seguir leyendo»
        // siguen igual) y, aparte, la posición exacta en el texto.
        val mapa = LibroAbierto.mapaTexto
        var paginacion = estado.paginacion
        val (aGuardar, progreso) = if (LibroAbierto.modoTexto && mapa != null) {
            ModoTexto.guardarPosicion(this, libro.id, posicion)
            val pagina = mapa.paginaDe(posicion) ?: return
            val pdf = mapa.posicionPdf(pagina) ?: return
            // En modo texto, «Seguir leyendo» muestra las páginas del PDF.
            paginacion = Paginacion(pagina, mapa.totalPaginas)
            pdf to (pagina - 1).toDouble() / mapa.totalPaginas.coerceAtLeast(1)
        } else {
            posicion to (posicion.locations.totalProgression ?: 0.0)
        }
        app.alcanceApp.launch {
            app.baseDatos.libros().guardarPosicion(
                id = libro.id,
                posicion = aGuardar.toJSON().toString(),
                progreso = progreso,
                capitulo = posicion.title ?: estado.capituloActual,
                fecha = System.currentTimeMillis(),
                pagina = paginacion?.pagina,
                paginas = paginacion?.total,
            )
        }
    }

    /** Preferencias de Readium para EPUB a partir de los ajustes del panel Aa. */
    private fun preferenciasEpub(ajustes: AjustesLectura): EpubPreferences = EpubPreferences(
        theme = when (ajustes.tema) {
            TemaLector.CREMA -> Theme.LIGHT
            TemaLector.SEPIA -> Theme.SEPIA
            TemaLector.OSCURO -> Theme.DARK
        },
        backgroundColor = Color(ContextCompat.getColor(this, ajustes.tema.fondo)),
        textColor = Color(ContextCompat.getColor(this, ajustes.tema.texto)),
        fontFamily = FontFamily(ajustes.tipografia.nombre),
        fontSize = ajustes.tamanoLetra,
        pageMargins = ajustes.margenesEpub,
        lineHeight = ajustes.interlineadoEpub,
        publisherStyles = false,
        textAlign = TextAlign.JUSTIFY,
        hyphens = true,
    )

    /** Lector de EPUB con los ajustes guardados y las cuatro tipografías de la app. */
    private fun fabricaEpub(publicacion: Publication, inicio: Locator?, ajustes: AjustesLectura): FragmentFactory {
        val configuracion = EpubNavigatorFragment.Configuration {
            // Permite al lector usar las tipografías guardadas en assets/fonts.
            servedAssets = servedAssets + "fonts/.*"
            // Menú propio al seleccionar texto (de momento: marcapáginas y copiar).
            selectionActionModeCallback = menuSeleccion
            // Avisos de la página cuando recorta una selección que se salía de la pantalla.
            registerJavascriptInterface("Libertalia") { PuenteSeleccion() }
            // Cómo se dibuja el marcapáginas.
            decorationTemplates.set(EstiloMarcapaginas::class, EstiloMarcapaginas.plantilla())
            TipografiaLector.entries.forEach { tipografia ->
                addFontFamilyDeclaration(FontFamily(tipografia.nombre)) {
                    tipografia.caras.forEach { cara ->
                        addFontFace {
                            addSource(cara.ruta)
                            setFontStyle(if (cara.cursiva) FontStyle.ITALIC else FontStyle.NORMAL)
                            setFontWeight(cara.grosores)
                        }
                    }
                }
            }
        }
        return EpubNavigatorFactory(publicacion).createFragmentFactory(
            initialLocator = inicio,
            initialPreferences = preferenciasEpub(ajustes),
            configuration = configuracion,
        )
    }

    /**
     * Filtro de color para PDF según el tema:
     * - crema y sepia: se "multiplica" cada píxel por el color del fondo, así el blanco
     *   del papel pasa a crema (o sepia) y el negro del texto sigue negro;
     * - oscuro: se invierte suavemente, el blanco pasa al fondo oscuro y el negro al
     *   color claro del texto (nunca blanco puro).
     * Sin filtro, la página se ve tal cual.
     */
    private fun aplicarFiltroPdf(vista: View, ajustes: AjustesLectura) {
        if (!ajustes.filtroCalidoPdf) {
            vista.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        val fondo = ContextCompat.getColor(this, ajustes.tema.fondo)
        val filtro = if (ajustes.tema.oscuro) {
            val texto = ContextCompat.getColor(this, ajustes.tema.texto)
            // Cada canal: resultado = texto + entrada × (fondo − texto) / 255.
            fun fila(canal: (Int) -> Int, posicion: Int): FloatArray {
                val f = canal(fondo).toFloat()
                val t = canal(texto).toFloat()
                return FloatArray(5).also {
                    it[posicion] = (f - t) / 255f
                    it[4] = t
                }
            }
            val r = fila(android.graphics.Color::red, 0)
            val g = fila(android.graphics.Color::green, 1)
            val b = fila(android.graphics.Color::blue, 2)
            ColorMatrixColorFilter(ColorMatrix(r + g + b + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
        } else {
            PorterDuffColorFilter(fondo, PorterDuff.Mode.MULTIPLY)
        }
        vista.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = filtro })
    }

    /** Lector de PDF: cada página ocupa todo el ancho. */
    private fun fabricaPdf(publicacion: Publication, inicio: Locator?): FragmentFactory =
        PdfNavigatorFactory(publicacion, PdfiumEngineProvider()).createFragmentFactory(
            initialLocator = inicio,
            initialPreferences = PdfiumPreferences(fit = Fit.WIDTH),
        )

    private companion object {
        const val ETIQUETA_NAVEGADOR = "navegador"
        const val GRUPO_SUBRAYADOS = "subrayados"
        const val GRUPO_VOZ = "voz"

        /** Empezar a leer en voz alta al abrir (al pulsar «Escuchar» en un PDF). */
        const val EXTRA_ESCUCHAR = "escuchar"

        /**
         * Número del párrafo (o título, elemento de lista, cita...) donde empieza la selección,
         * contando desde el principio del capítulo; -1 si no se sabe.
         */
        const val JS_PARRAFO =
            "(function(){var s=window.getSelection();if(!s||s.rangeCount==0)return -1;" +
                "var n=s.getRangeAt(0).startContainer;var e=n.nodeType==1?n:n.parentElement;if(!e)return -1;" +
                "var q='p,li,blockquote,h1,h2,h3,h4,h5,h6,dd,dt,pre,figcaption';" +
                "var b=e.closest(q);if(!b)return -1;" +
                "return Array.prototype.indexOf.call(document.querySelectorAll(q),b);})()"

        /**
         * Readium coloca las páginas del capítulo en columnas, una al lado de otra. Si un tirador de
         * la selección se lleva al borde de la pantalla, el navegador lo pone en la página de al
         * lado (que no se ve) y la selección incluye texto de otras páginas. Esto la recorta a lo
         * que se ve en la pantalla y quita los espacios y saltos de línea de los extremos (si no,
         * el subrayado dibuja un recuadro vacío en la línea siguiente). Devuelve 1 si la cambió.
         */
        private const val FUNCION_RECORTAR =
            "function(soloFuera){var s=getSelection();if(!s||!s.rangeCount||s.isCollapsed)return 0;" +
                "var r=s.getRangeAt(0),W=innerWidth,c=r.commonAncestorContainer;" +
                "if(c.nodeType==3)c=c.parentNode;" +
                "var w=document.createTreeWalker(c,NodeFilter.SHOW_TEXT),n,ns=[];" +
                "while(n=w.nextNode()){if(r.intersectsNode(n))ns.push(n);}" +
                "function lim(n){return[n===r.startContainer?r.startOffset:0,n===r.endContainer?r.endOffset:n.length];}" +
                // ¿Es una letra (no un espacio) y, si se pide, se ve en la pantalla?
                "function vale(n,k,vista){if(/\\s/.test(n.data[k]))return false;if(!vista)return true;" +
                "var g=document.createRange();g.setStart(n,k);g.setEnd(n,k+1);var b=g.getClientRects();" +
                "return b.length>0&&b[0].left>=-1&&b[0].right<=W+1;}" +
                // La primera (o la última) letra que vale, como [texto, posición].
                "function busca(vista,alPrincipio){var i,k,l;" +
                "if(alPrincipio){for(i=0;i<ns.length;i++){l=lim(ns[i]);for(k=l[0];k<l[1];k++){if(vale(ns[i],k,vista))return[ns[i],k];}}}" +
                "else{for(i=ns.length-1;i>=0;i--){l=lim(ns[i]);for(k=l[1]-1;k>=l[0];k--){if(vale(ns[i],k,vista))return[ns[i],k+1];}}}" +
                "return null;}" +
                "var a=busca(true,true),z=busca(true,false);if(!a||!z)return 0;" +
                "function igual(x,y){return x&&y&&x[0]===y[0]&&x[1]==y[1];}" +
                // Con soloFuera, solo se toca si hay letras fuera de la pantalla (no por los espacios).
                "if(soloFuera&&igual(a,busca(false,true))&&igual(z,busca(false,false)))return 0;" +
                "if(igual(a,[r.startContainer,r.startOffset])&&igual(z,[r.endContainer,r.endOffset]))return 0;" +
                "s.setBaseAndExtent(a[0],a[1],z[0],z[1]);return 1;}"

        /** Recorta la selección justo antes de subrayar (ver [FUNCION_RECORTAR]). */
        const val JS_RECORTAR_SELECCION = "($FUNCION_RECORTAR)(false)"

        /**
         * Mientras hay texto seleccionado, la página no se mueve: si el navegador la desplaza
         * para enseñar la parte de la selección que está en otra página, se devuelve a su sitio.
         * Sin esto, al llevar un tirador al borde la página «tiembla» (el navegador la mueve y
         * Readium la vuelve a colocar).
         * Además, si al soltar el tirador la selección se ha ido a otra página (el navegador a
         * veces lleva el inicio a la página anterior cuando el tirador sube a la primera línea),
         * un segundo después se recorta a lo que se ve y se avisa a la app ([PuenteSeleccion]):
         * al cambiarla así, Android quita sus tiradores y su menú, y la app enseña su propia barra.
         * Se instala una vez en cada capítulo.
         */
        const val JS_SIN_TEMBLOR =
            "(function(){if(window.__libertaliaSinTemblor)return 0;window.__libertaliaSinTemblor=1;" +
                "var recortar=$FUNCION_RECORTAR,espera=null,recortada=false;" +
                "function avisar(f){try{if(window.Libertalia)window.Libertalia[f]();}catch(x){}}" +
                "var fijo=null;function e(){return document.scrollingElement||document.documentElement;}" +
                "document.addEventListener('selectionchange',function(){var s=getSelection();clearTimeout(espera);" +
                "if(s&&!s.isCollapsed){if(fijo===null)fijo=e().scrollLeft;" +
                "espera=setTimeout(function(){if(recortar(true)){recortada=true;avisar('seleccionRecortada');}},1000);}" +
                "else{fijo=null;if(recortada){recortada=false;avisar('seleccionQuitada');}}});" +
                "window.addEventListener('scroll',function(){" +
                "if(fijo!==null&&Math.abs(e().scrollLeft-fijo)>1)e().scrollLeft=fijo;},true);" +
                "return 1;})()"

        /** Ancho total del capítulo dividido entre el ancho de una pantalla. */
        const val JS_PANTALLAS =
            "(function(){var e=document.scrollingElement||document.documentElement;" +
                "return Math.max(1, Math.round(e.scrollWidth / window.innerWidth));})()"
    }
}

/** Opciones del menú de selección (y de la barra propia, en CapaLector). */
const val ID_MARCAPAGINAS = 1
const val ID_COPIAR = 2
const val ID_DICCIONARIO = 3
/** Los cuatro colores de subrayado van del 10 al 13. */
const val ID_PRIMER_COLOR = 10
