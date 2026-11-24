package org.libertalia.reader.tarjetas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.datos.ColorSubrayado
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Tarjeta

/**
 * Las tarjetas (subrayados) de un libro: la lista, el filtro por color y los cambios que
 * se hacen desde la tarjeta abierta. Las páginas destacadas no salen aquí: van aparte.
 */
class TarjetasViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as LibertaliaApp
    private val tarjetasDao = app.baseDatos.tarjetas()

    var libro by mutableStateOf<Libro?>(null)
        private set

    /** Todas las tarjetas del libro, en el orden del libro (null mientras se cargan). */
    var todas by mutableStateOf<List<Tarjeta>?>(null)
        private set

    /** Color elegido en los filtros (null = todas). */
    var filtroColor by mutableStateOf<ColorSubrayado?>(null)

    /** Etiqueta elegida en los filtros (null = todas). */
    var filtroEtiqueta by mutableStateOf<String?>(null)

    /** Texto buscado dentro de las tarjetas de este libro (vacío = sin buscar). */
    var consulta by mutableStateOf("")

    /** Las etiquetas que se usan en las tarjetas de este libro, de la más usada a la menos. */
    val etiquetas: List<String>
        get() = TodasLasEtiquetas.de(todas.orEmpty()).map { it.first }

    /** Las tarjetas que se ven con los filtros y la búsqueda elegidos. */
    val visibles: List<Tarjeta>
        get() = todas.orEmpty().filter { tarjeta ->
            (filtroColor == null || tarjeta.color == filtroColor) &&
                (filtroEtiqueta == null || filtroEtiqueta in Etiquetas.de(tarjeta)) &&
                BuscarTexto.coincide(tarjeta, consulta)
        }

    /** Todos los libros y todas las tarjetas: para la pantalla de una etiqueta (de todos los libros). */
    var libros by mutableStateOf<List<Libro>>(emptyList())
        private set
    var todasLasTarjetas by mutableStateOf<List<Tarjeta>>(emptyList())
        private set

    private var cargado = false

    fun cargar(idLibro: String) {
        if (cargado) return
        cargado = true
        // El título y el autor se leen siempre del libro: si se editan, las tarjetas cambian solas.
        viewModelScope.launch { app.baseDatos.libros().observar(idLibro).collect { libro = it } }
        viewModelScope.launch { tarjetasDao.observarSubrayados(idLibro).collect { todas = it } }
        viewModelScope.launch { app.baseDatos.libros().observarTodos().collect { libros = it } }
        viewModelScope.launch { tarjetasDao.observarTodosLosSubrayados().collect { todasLasTarjetas = it } }
    }

    fun tarjeta(id: String): Tarjeta? = todas?.firstOrNull { it.id == id }

    fun cambiarColor(tarjeta: Tarjeta, color: ColorSubrayado) = guardar {
        tarjetasDao.cambiarColor(tarjeta.id, color, System.currentTimeMillis())
    }

    /** Corrige el texto subrayado y la frase. Si la frase queda vacía, se usa solo el subrayado. */
    fun cambiarTexto(tarjeta: Tarjeta, texto: String, frase: String) {
        val subrayado = texto.trim().ifEmpty { return }
        val contexto = frase.trim().takeIf { it.isNotEmpty() && it != subrayado }
        guardar { tarjetasDao.cambiarTexto(tarjeta.id, subrayado, contexto, System.currentTimeMillis()) }
    }

    fun guardarNota(tarjeta: Tarjeta, nota: String?) {
        val limpia = nota?.trim()?.takeIf { it.isNotEmpty() }
        if (limpia == tarjeta.nota) return
        guardar { tarjetasDao.guardarNota(tarjeta.id, limpia, System.currentTimeMillis()) }
    }

    fun anadirEtiqueta(tarjeta: Tarjeta, etiqueta: String) {
        val texto = ReglasEtiquetas.anadir(tarjeta, etiqueta) ?: return
        guardar { tarjetasDao.guardarEtiquetas(tarjeta.id, texto, System.currentTimeMillis()) }
    }

    fun quitarEtiqueta(tarjeta: Tarjeta, etiqueta: String) {
        val texto = ReglasEtiquetas.quitar(tarjeta, etiqueta)
        guardar { tarjetasDao.guardarEtiquetas(tarjeta.id, texto, System.currentTimeMillis()) }
    }

    /** Borra la tarjeta (y, con ella, el subrayado del libro). */
    fun eliminar(tarjeta: Tarjeta) = guardar {
        tarjetasDao.eliminar(tarjeta.id, System.currentTimeMillis())
    }

    /** Se guarda aunque se cierre la pantalla justo después. */
    private fun guardar(accion: suspend () -> Unit) {
        app.alcanceApp.launch { accion() }
    }
}

/** Las etiquetas de una tarjeta, como lista. */
object Etiquetas {
    fun de(tarjeta: Tarjeta): List<String> =
        tarjeta.etiquetas?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
