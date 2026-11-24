package org.libertalia.reader.copias

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.datos.Libro
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lo que tiene que hacer la pantalla cuando la copia está lista. */
sealed interface EventoExportar {
    /** Abrir el menú de compartir de Android con este archivo. */
    data class Compartir(val archivo: File) : EventoExportar

    /** La copia se ha guardado donde dijo el usuario. */
    data class Guardada(val resumen: ResumenExportacion) : EventoExportar

    /** No se ha podido escribir la copia. */
    data object Error : EventoExportar
}

/**
 * Pantalla Exportar (boceto 11): qué libros van en la copia, si van con sus archivos,
 * y guardarla o compartirla.
 */
class ExportarViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as LibertaliaApp
    private val exportador = Exportador(app)

    val libros: StateFlow<List<Libro>?> = app.baseDatos.libros().observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cuántas tarjetas (subrayados) tiene cada libro. */
    val conteoTarjetas: StateFlow<Map<String, Int>> = app.baseDatos.tarjetas().observarConteo()
        .map { lista -> lista.associate { it.idLibro to it.numero } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** «Incluir los archivos»: copia completa para cambiar de móvil, o solo los datos. */
    var incluirArchivos by mutableStateOf(true)

    /** Los libros marcados. null hasta que se cargue la lista la primera vez. */
    var seleccion by mutableStateOf<Set<String>?>(null)
        private set

    /** De 0 a 1 mientras se prepara la copia; null si no se está preparando. */
    var progreso by mutableStateOf<Float?>(null)
        private set

    private val canalEventos = Channel<EventoExportar>(Channel.BUFFERED)
    val eventos = canalEventos.receiveAsFlow()

    private var trabajo: Job? = null

    /** Al abrir la pantalla: desde el menú de un libro, solo ese; si no, todos. */
    fun iniciar(libros: List<Libro>, idInicial: String?) {
        if (seleccion != null) return
        seleccion = if (idInicial != null && libros.any { it.id == idInicial }) setOf(idInicial) else libros.map { it.id }.toSet()
    }

    fun alternar(id: String) {
        val actual = seleccion.orEmpty()
        seleccion = if (id in actual) actual - id else actual + id
    }

    fun seleccionarTodos(todos: Boolean) {
        seleccion = if (todos) libros.value.orEmpty().map { it.id }.toSet() else emptySet()
    }

    fun pesoEstimado(libro: Libro): Long = exportador.pesoEstimado(libro, incluirArchivos)

    /** Nombre propuesto: «libertalia-2026-11-23.libertalia» (o el título, si es un solo libro). */
    fun nombreSugerido(): String {
        val elegidos = elegidos()
        val base = if (elegidos.size == 1) {
            FormatoCopia.carpetaDe(elegidos.first()).removePrefix(FormatoCopia.CARPETA_LIBROS).trimEnd('/')
                .substringBeforeLast('-')
        } else {
            "libertalia-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        }
        return "$base.${FormatoCopia.EXTENSION}"
    }

    /** «Guardar en…»: prepara la copia y la escribe en el archivo que eligió el usuario. */
    fun guardarEn(uri: Uri) = preparar { archivo, resumen ->
        withContext(Dispatchers.IO) { Exportador.escribirEn(app, uri, archivo) }
        archivo.delete()
        canalEventos.send(EventoExportar.Guardada(resumen))
    }

    /** «Compartir»: prepara la copia y abre el menú de compartir de Android. */
    fun compartir() = preparar { archivo, _ ->
        canalEventos.send(EventoExportar.Compartir(archivo))
    }

    private fun preparar(alTerminar: suspend (File, ResumenExportacion) -> Unit) {
        if (trabajo?.isActive == true) return
        val elegidos = elegidos()
        if (elegidos.isEmpty()) return
        val incluir = incluirArchivos
        trabajo = viewModelScope.launch {
            progreso = 0f
            try {
                val carpeta = File(app.cacheDir, CARPETA_COMPARTIR)
                // Las copias compartidas antes ya no hacen falta.
                withContext(Dispatchers.IO) { carpeta.listFiles()?.forEach { it.delete() } }
                val archivo = File(carpeta, nombreSugerido())
                var ultimo = 0f
                val resumen = exportador.exportar(
                    libros = elegidos,
                    incluirArchivo = { incluir },
                    destino = archivo,
                    // La barra se mueve de 1 % en 1 % (no en cada trocito copiado).
                    alAvanzar = { valor ->
                        if (valor - ultimo >= 0.01f || valor >= 1f) {
                            ultimo = valor
                            progreso = valor
                        }
                    },
                )
                alTerminar(archivo, resumen)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                canalEventos.send(EventoExportar.Error)
            } finally {
                progreso = null
            }
        }
    }

    private fun elegidos(): List<Libro> {
        val marcados = seleccion.orEmpty()
        return libros.value.orEmpty().filter { it.id in marcados }
    }

    companion object {
        /** Carpeta (en la caché) de las copias preparadas para compartir. La usa el FileProvider. */
        const val CARPETA_COMPARTIR = "compartir"
    }
}
