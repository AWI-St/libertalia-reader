package org.libertalia.reader.ui.ajustes

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.comic.ModoComic
import org.libertalia.reader.datos.EstadoLectura
import org.libertalia.reader.lector.TipografiaLector
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Pantalla Ajustes: estadísticas rápidas y todos los ajustes de la app. */
class AjustesViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as LibertaliaApp
    private val prefs = app.preferencias

    /** Libros y cómics terminados este año (null mientras se calcula). */
    var terminadosEsteAnio by mutableStateOf<Int?>(null)
        private set

    /** Segundos leídos esta semana, de lunes a hoy (null mientras se calcula). */
    var segundosSemana by mutableStateOf<Long?>(null)
        private set

    var limitePesadoMb by mutableStateOf(prefs.limitePesadoMb)
        private set
    var borrarOriginales by mutableStateOf(prefs.borrarOriginales)
        private set
    var incluirPesados by mutableStateOf(prefs.incluirPesados)
        private set
    var recortarComics by mutableStateOf(AjustesApp.recortarComics(app))
        private set
    var modoComic by mutableStateOf(AjustesApp.modoComicPorDefecto(app))
        private set

    val idioma: String? get() = AjustesApp.idioma()
    val tipografia: TipografiaLector get() = AjustesApp.tipografia

    /** La carpeta de la app tal como la ve el usuario («Documents/Libertalia»). */
    val carpeta: String? =
        prefs.uriCarpeta?.let { runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(it)).substringAfter(':') }.getOrNull() }

    init {
        viewModelScope.launch {
            val zona = ZoneId.systemDefault()
            val hoy = LocalDate.now(zona)
            terminadosEsteAnio = app.baseDatos.libros().todos().count { libro ->
                val fecha = fechaTerminado(libro)
                fecha != null && Instant.ofEpochMilli(fecha).atZone(zona).year == hoy.year
            }
            val lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            segundosSemana = app.baseDatos.diasLectura().desde(lunes.toString()).sumOf { it.segundos }
        }
    }

    fun cambiarIdioma(etiqueta: String?) = AjustesApp.cambiarIdioma(etiqueta)

    fun cambiarTipografia(nueva: TipografiaLector) = AjustesApp.cambiarTipografia(app, nueva)

    fun cambiarLimitePesado(mb: Int) {
        prefs.limitePesadoMb = mb
        limitePesadoMb = mb
    }

    fun cambiarBorrarOriginales(valor: Boolean?) {
        prefs.borrarOriginales = valor
        borrarOriginales = valor
    }

    fun cambiarIncluirPesados(valor: Boolean?) {
        prefs.incluirPesados = valor
        incluirPesados = valor
    }

    fun cambiarRecortarComics(valor: Boolean) {
        AjustesApp.cambiarRecortarComics(app, valor)
        recortarComics = valor
    }

    fun cambiarModoComic(modo: ModoComic?) {
        AjustesApp.cambiarModoComicPorDefecto(app, modo)
        modoComic = modo
    }

    companion object {
        /** Límites que se pueden elegir para el aviso de archivos pesados (en MB). */
        val LIMITES_PESADO = listOf(25, 50, 100, 200, 500)
    }
}
