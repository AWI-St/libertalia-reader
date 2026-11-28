package org.libertalia.reader.ui.ajustes

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import org.libertalia.reader.comic.ModoComic
import org.libertalia.reader.lector.TipografiaLector

/**
 * Ajustes de toda la app que no son del lector de libros: tipografía de la app, idioma y
 * los ajustes por defecto del lector de cómics. Se guardan en el móvil (nunca fuera).
 */
object AjustesApp {

    /**
     * El idioma en el que se ve la app ahora mismo (el elegido en Ajustes, el del móvil si es
     * uno de los seis o, si no, castellano). Para escribir fechas y números en ese idioma, y no
     * en el del móvil.
     */
    fun localeApp(contexto: android.content.Context): java.util.Locale =
        java.util.Locale(contexto.getString(org.libertalia.reader.R.string.codigo_idioma))

    /** Una fecha con el formato del idioma de la app ([estilo]: java.text.DateFormat.LONG, MEDIUM...). */
    fun fecha(contexto: android.content.Context, milisegundos: Long, estilo: Int = java.text.DateFormat.LONG): String =
        java.text.DateFormat.getDateInstance(estilo, localeApp(contexto)).format(java.util.Date(milisegundos))

    /** Fecha y hora con el formato del idioma de la app. */
    fun fechaHora(contexto: android.content.Context, milisegundos: Long): String =
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.LONG, java.text.DateFormat.SHORT, localeApp(contexto))
            .format(java.util.Date(milisegundos))

    /** La tipografía de toda la app (al cambiarla, las pantallas se redibujan solas). */
    var tipografia by mutableStateOf(TipografiaLector.LITERATA)
        private set

    fun cargar(contexto: Context) {
        val nombre = prefs(contexto).getString(CLAVE_TIPOGRAFIA, null)
        tipografia = nombre?.let { runCatching { TipografiaLector.valueOf(it) }.getOrNull() } ?: TipografiaLector.LITERATA
        aspecto = prefs(contexto).getString(CLAVE_ASPECTO, null)
            ?.let { runCatching { AspectoApp.valueOf(it) }.getOrNull() } ?: AspectoApp.SISTEMA
        aplicarAspecto()
    }

    // ---- Aspecto (claro / oscuro) ----

    /**
     * Claro, oscuro o como el móvil. Los colores oscuros están en res/values-night/colors.xml;
     * Android elige solo unos u otros según lo que se diga aquí.
     */
    var aspecto by mutableStateOf(AspectoApp.SISTEMA)
        private set

    fun cambiarAspecto(contexto: Context, nuevo: AspectoApp) {
        aspecto = nuevo
        prefs(contexto).edit { putString(CLAVE_ASPECTO, nuevo.name) }
        // Las pantallas abiertas se vuelven a crear con los colores nuevos.
        aplicarAspecto()
    }

    private fun aplicarAspecto() {
        AppCompatDelegate.setDefaultNightMode(
            when (aspecto) {
                AspectoApp.CLARO -> AppCompatDelegate.MODE_NIGHT_NO
                AspectoApp.OSCURO -> AppCompatDelegate.MODE_NIGHT_YES
                AspectoApp.SISTEMA -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }

    /** Si la pantalla [contexto] se está viendo con los colores oscuros. */
    fun esOscuro(contexto: Context): Boolean =
        (contexto.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    fun cambiarTipografia(contexto: Context, nueva: TipografiaLector) {
        tipografia = nueva
        prefs(contexto).edit { putString(CLAVE_TIPOGRAFIA, nueva.name) }
    }

    // ---- Idioma ----

    /** Los seis idiomas de la app (null = el del sistema). */
    val IDIOMAS: List<String?> = listOf(null, "es", "gl", "eu", "ca", "en", "fr")

    /** El idioma elegido en la app (null = el del sistema). */
    fun idioma(): String? =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotEmpty() }?.substringBefore('-')

    /**
     * Cambia el idioma solo de esta app (no el del móvil). Android vuelve a crear las
     * pantallas con los textos del idioma nuevo.
     */
    fun cambiarIdioma(etiqueta: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (etiqueta == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(etiqueta),
        )
    }

    // ---- Lector de cómics ----

    private fun prefsComic(contexto: Context) = contexto.getSharedPreferences("comic", Context.MODE_PRIVATE)

    /** Recortar los márgenes blancos de los escaneos (para todos los cómics). */
    fun recortarComics(contexto: Context): Boolean = prefsComic(contexto).getBoolean("recortar", true)

    fun cambiarRecortarComics(contexto: Context, recortar: Boolean) {
        prefsComic(contexto).edit { putBoolean("recortar", recortar) }
    }

    /** Modo con el que se abren los cómics que no tienen uno elegido (null = automático). */
    fun modoComicPorDefecto(contexto: Context): ModoComic? =
        prefsComic(contexto).getString(CLAVE_MODO_DEFECTO, null)?.let { runCatching { ModoComic.valueOf(it) }.getOrNull() }

    fun cambiarModoComicPorDefecto(contexto: Context, modo: ModoComic?) {
        prefsComic(contexto).edit { if (modo == null) remove(CLAVE_MODO_DEFECTO) else putString(CLAVE_MODO_DEFECTO, modo.name) }
    }

    private fun prefs(contexto: Context) = contexto.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private const val CLAVE_TIPOGRAFIA = "tipografia_app"
    private const val CLAVE_ASPECTO = "aspecto_app"
    private const val CLAVE_MODO_DEFECTO = "modo_defecto"
}

/** Aspecto de la interfaz: claro (crema), oscuro (marrón oscuro) o el del móvil. */
enum class AspectoApp { CLARO, OSCURO, SISTEMA }
