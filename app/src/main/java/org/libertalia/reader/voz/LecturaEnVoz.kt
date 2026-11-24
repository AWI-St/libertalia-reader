package org.libertalia.reader.voz

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.R
import org.libertalia.reader.racha.MedidorLectura
import org.readium.navigator.media.common.MediaNavigator
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.getOrElse
import java.util.Locale

/** Por qué no se ha podido leer en voz alta. */
sealed interface ProblemaVoz {
    /**
     * El móvil no tiene voz para el idioma del libro ([idioma]: su nombre, p. ej. «francés»).
     * [alternativa]: el idioma de la app, si para él sí hay voz (algunos libros dicen mal su
     * idioma); se puede leer con esa voz.
     */
    data class FaltaVoz(val idioma: String, val alternativa: String?) : ProblemaVoz

    /** Cualquier otro fallo (sin motor de voz, libro sin texto...). */
    data object Error : ProblemaVoz
}

/**
 * Lectura en voz alta con las voces de Android (Readium TTS).
 *
 * Vive fuera de la pantalla del lector para poder seguir leyendo con la pantalla apagada o
 * en otra app: entonces la mantiene viva [ServicioVoz], que muestra la notificación con los
 * controles. Solo hay una lectura a la vez.
 */
@OptIn(ExperimentalReadiumApi::class)
object LecturaEnVoz {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var navegador: TtsNavigator<*, AndroidTtsPreferences, AndroidTtsEngine.Error, *>? = null
    private var controlador: ListenableFuture<MediaController>? = null
    private var trabajos: Job? = null
    private var medidor: MedidorLectura? = null

    /** El servicio de la notificación, mientras exista. */
    internal var servicio: ServicioVoz? = null

    /** El libro que se está leyendo en voz alta (para que su lector resalte la frase). */
    var publicacion: Publication? = null
        private set

    private val _activa = MutableStateFlow(false)

    /** Hay una lectura en marcha (sonando o en pausa): se ve el panel de control. */
    val activa: StateFlow<Boolean> = _activa.asStateFlow()

    private val _sonando = MutableStateFlow(false)
    val sonando: StateFlow<Boolean> = _sonando.asStateFlow()

    private val _frase = MutableStateFlow<Locator?>(null)

    /** La frase que se está leyendo, para resaltarla y llevar el libro hasta ella. */
    val frase: StateFlow<Locator?> = _frase.asStateFlow()

    private val _velocidad = MutableStateFlow(1.0)
    val velocidad: StateFlow<Double> = _velocidad.asStateFlow()

    private val _problema = MutableStateFlow<ProblemaVoz?>(null)
    val problema: StateFlow<ProblemaVoz?> = _problema.asStateFlow()

    /** Velocidades que se van eligiendo al tocar el botón de la velocidad. */
    val VELOCIDADES = listOf(0.75, 1.0, 1.25, 1.5, 1.75, 2.0)

    /** El reproductor que maneja la notificación (o null si no hay lectura). */
    internal fun jugador() = navegador?.asMedia3Player()

    /** Lo necesario para volver a intentarlo con la voz del idioma de la app. */
    private var ultimoIntento: Triple<Publication, Locator?, String>? = null

    /**
     * Empieza a leer [libro] en voz alta desde [desde] (la primera frase de la pantalla).
     * [idLibro]: para recordar si este libro se lee con la voz de otro idioma.
     */
    suspend fun empezar(app: LibertaliaApp, libro: Publication, desde: Locator?, idLibro: String) {
        parar()
        _problema.value = null
        _velocidad.value = velocidadGuardada(app)
        ultimoIntento = Triple(libro, desde, idLibro)
        val fabrica = TtsNavigatorFactory(app, libro, voiceSelector = ::elegirVoz) ?: run {
            _problema.value = ProblemaVoz.Error
            return
        }
        // El idioma del libro, salvo que se haya elegido leerlo con la voz del idioma de la app.
        val idiomaApp = Language(app.getString(R.string.codigo_idioma))
        val forzado = preferencias(app).getString(CLAVE_IDIOMA + idLibro, null)?.let(::Language)
        val idioma = forzado ?: libro.metadata.language ?: idiomaApp
        val nuevo = fabrica.createNavigator(
            listener = object : TtsNavigator.Listener {
                override fun onStopRequested() = parar()
            },
            initialLocator = desde,
            initialPreferences = AndroidTtsPreferences(language = idioma, speed = _velocidad.value),
        ).getOrElse {
            _problema.value = ProblemaVoz.Error
            return
        }
        // Solo voces que están en el móvil: las «de internet» mandarían el texto del libro a un
        // servidor. Si no hay ninguna para el idioma del libro, se avisa para descargarla.
        fun hayVoz(de: Language) = nuevo.voices.any { it.esLocal() && it.language.removeRegion() == de.removeRegion() }
        if (!hayVoz(idioma)) {
            nuevo.close()
            // El nombre del idioma en el idioma de la app; el castellano, como lo llama la app.
            fun nombre(de: Language) =
                if (de.removeRegion().code == "es") app.getString(R.string.nombre_castellano)
                else de.locale.getDisplayLanguage(idiomaApp.locale)
            _problema.value = ProblemaVoz.FaltaVoz(
                idioma = nombre(idioma),
                alternativa = nombre(idiomaApp).takeIf { hayVoz(idiomaApp) && idiomaApp.removeRegion() != idioma.removeRegion() },
            )
            return
        }
        navegador = nuevo
        publicacion = libro
        medidor = MedidorLectura(app)
        _activa.value = true

        // Conectarse al servicio lo arranca: así la lectura sigue con la pantalla apagada.
        val token = SessionToken(app, ComponentName(app, ServicioVoz::class.java))
        controlador = MediaController.Builder(app, token).buildAsync()

        trabajos = alcance.launch {
            launch {
                nuevo.playback.collect { reproduccion ->
                    when (val estado = reproduccion.state) {
                        is MediaNavigator.State.Ended -> parar()
                        is TtsNavigator.State.Failure -> {
                            val causa = (estado.error as? TtsNavigator.Error.EngineError<*>)?.cause
                            _problema.value = if (causa is AndroidTtsEngine.Error.LanguageMissingData) {
                                ProblemaVoz.FaltaVoz(causa.language.locale.displayLanguage, alternativa = null)
                            } else {
                                ProblemaVoz.Error
                            }
                            parar()
                        }
                        else -> _sonando.value = reproduccion.playWhenReady
                    }
                }
            }
            launch {
                nuevo.location.collect {
                    _frase.value = it.utteranceLocator
                    // Escuchar cuenta como leer (para la racha), también con la pantalla apagada.
                    medidor?.actividad()
                }
            }
            launch {
                _sonando.map { it }.distinctUntilChanged().collect { suena ->
                    if (suena) medidor?.empezar() else medidor?.parar()
                }
            }
        }
        nuevo.play()
    }

    fun reproducirOPausar() {
        val nav = navegador ?: return
        if (_sonando.value) nav.pause() else nav.play()
    }

    fun fraseAnterior() {
        navegador?.skipToPreviousUtterance()
    }

    fun fraseSiguiente() {
        navegador?.skipToNextUtterance()
    }

    /** Pasa a la velocidad siguiente de la lista (después de la más rápida, vuelve a la más lenta). */
    fun cambiarVelocidad(contexto: Context) {
        val actual = VELOCIDADES.indexOfFirst { it >= _velocidad.value - 0.01 }.coerceAtLeast(0)
        val nueva = VELOCIDADES[(actual + 1) % VELOCIDADES.size]
        _velocidad.value = nueva
        contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).edit().putFloat(CLAVE_VELOCIDAD, nueva.toFloat()).apply()
        navegador?.submitPreferences(AndroidTtsPreferences(speed = nueva))
    }

    /** Ya se ha mostrado el aviso del problema. */
    fun problemaVisto() {
        _problema.value = null
    }

    /**
     * El libro dice estar en un idioma sin voz en el móvil: se lee con la voz del idioma de la
     * app, y se recuerda para este libro.
     */
    fun leerConIdiomaDeLaApp(app: LibertaliaApp) {
        _problema.value = null
        val (libro, desde, idLibro) = ultimoIntento ?: return
        preferencias(app).edit().putString(CLAVE_IDIOMA + idLibro, app.getString(R.string.codigo_idioma)).apply()
        alcance.launch { empezar(app, libro, desde, idLibro) }
    }

    /** Deja de leer: quita la notificación y el panel. */
    fun parar() {
        trabajos?.cancel()
        trabajos = null
        medidor?.parar()
        medidor = null
        navegador?.let {
            it.pause()
            it.close()
        }
        navegador = null
        publicacion = null
        servicio?.cerrar()
        controlador?.let { MediaController.releaseFuture(it) }
        controlador = null
        _sonando.value = false
        _frase.value = null
        _activa.value = false
    }

    /**
     * Qué voz usar para un idioma: solo voces del propio móvil (nunca de internet). Primero la
     * del país exacto del libro; si el libro no lo dice, la del país del móvil (castellano de
     * España en un móvil de España); si no, cualquiera de ese idioma. Entre ellas, la de más calidad.
     */
    private fun elegirVoz(idioma: Language?, voces: Set<AndroidTtsEngine.Voice>): AndroidTtsEngine.Voice? {
        val buscado = idioma ?: Language(Locale.getDefault())
        val delIdioma = voces.filter { it.esLocal() && it.language.removeRegion() == buscado.removeRegion() }
        val pais = buscado.locale.country.ifEmpty { Locale.getDefault().country }
        val mismoPais = delIdioma.filter { it.language.locale.country.equals(pais, ignoreCase = true) }
        return (mismoPais.ifEmpty { delIdioma }).maxByOrNull { it.quality.ordinal }
    }

    private fun AndroidTtsEngine.Voice.esLocal() = !requiresNetwork

    private fun preferencias(contexto: Context) = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)

    private fun velocidadGuardada(contexto: Context): Double = preferencias(contexto).getFloat(CLAVE_VELOCIDAD, 1f).toDouble()

    private const val PREFERENCIAS = "voz"
    private const val CLAVE_VELOCIDAD = "velocidad"

    /** «idioma_<id del libro>»: leer ese libro con la voz de este idioma. */
    private const val CLAVE_IDIOMA = "idioma_"
}
