package org.libertalia.reader.actualizaciones

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.libertalia.reader.datos.Preferencias
import java.net.HttpURLConnection
import java.net.URL

/** Una versión de la app publicada en GitHub que es más nueva que la instalada. */
data class VersionNueva(
    val version: String,
    /** El APK para descargar (o null si la versión no lo trae). */
    val urlApk: String?,
    /** La página de la versión en GitHub. */
    val urlPagina: String,
)

/**
 * «Buscar actualizaciones» (desactivado de entrada): pregunta a GitHub cuál es la última
 * versión publicada de la app. Es lo único para lo que la app usa internet. No se envía
 * ningún dato del usuario: solo se pide la página pública de la última versión.
 */
object Actualizaciones {

    private const val ULTIMA_VERSION = "https://api.github.com/repos/AWI-St/libertalia-reader/releases/latest"
    private const val UN_DIA = 24L * 60 * 60 * 1000

    /** La versión instalada («0.2.0»). */
    fun versionActual(contexto: Context): String =
        runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName }.getOrNull() ?: "0"

    /** Si toca comprobar sola (activado y hace más de un día de la última vez). */
    fun tocaComprobar(preferencias: Preferencias): Boolean =
        preferencias.buscarActualizaciones &&
            System.currentTimeMillis() - preferencias.ultimaComprobacion > UN_DIA

    /**
     * Pregunta a GitHub. Devuelve la versión nueva, null si ya se tiene la última, o un fallo
     * (sin conexión, GitHub no responde...).
     */
    suspend fun comprobar(contexto: Context, preferencias: Preferencias): Result<VersionNueva?> = withContext(Dispatchers.IO) {
        runCatching {
            val conexion = URL(ULTIMA_VERSION).openConnection() as HttpURLConnection
            val texto = try {
                conexion.connectTimeout = 10_000
                conexion.readTimeout = 10_000
                conexion.setRequestProperty("Accept", "application/vnd.github+json")
                conexion.setRequestProperty("User-Agent", "Libertalia-Reader")
                // (Sin ninguna versión publicada todavía, GitHub responde «no encontrado».)
                if (conexion.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    preferencias.ultimaComprobacion = System.currentTimeMillis()
                    return@runCatching null
                }
                if (conexion.responseCode != HttpURLConnection.HTTP_OK) error("GitHub: ${conexion.responseCode}")
                conexion.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conexion.disconnect()
            }
            preferencias.ultimaComprobacion = System.currentTimeMillis()
            val datos = JSONObject(texto)
            val version = datos.getString("tag_name").removePrefix("v").removePrefix("V")
            if (!esMasNueva(version, versionActual(contexto))) return@runCatching null
            val activos = datos.optJSONArray("assets")
            val apk = (0 until (activos?.length() ?: 0))
                .map { activos!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
            VersionNueva(version, apk, datos.optString("html_url"))
        }
    }

    /** Si [nueva] («0.10.0») es posterior a [actual] («0.9.2»), comparando número a número. */
    fun esMasNueva(nueva: String, actual: String): Boolean {
        fun partes(v: String) = v.split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = partes(nueva)
        val b = partes(actual)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
