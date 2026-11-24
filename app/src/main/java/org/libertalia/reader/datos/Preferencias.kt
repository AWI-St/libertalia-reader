package org.libertalia.reader.datos

import android.content.Context
import androidx.core.content.edit

/** Ajustes sencillos de la app (se guardan en el móvil, nunca fuera). */
class Preferencias(contexto: Context) {

    private val prefs = contexto.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** La carpeta de la app elegida por el usuario (dirección "content://..."). */
    var uriCarpeta: String?
        get() = prefs.getString(CLAVE_CARPETA, null)
        set(valor) = prefs.edit { putString(CLAVE_CARPETA, valor) }

    /**
     * Al añadir un libro que está fuera de la carpeta (p. ej. en Descargas): si se borra el
     * original. null = preguntar cada vez; true/false = la elección recordada.
     */
    var borrarOriginales: Boolean?
        get() = if (prefs.contains(CLAVE_BORRAR_ORIGINALES)) prefs.getBoolean(CLAVE_BORRAR_ORIGINALES, false) else null
        set(valor) = prefs.edit {
            if (valor == null) remove(CLAVE_BORRAR_ORIGINALES) else putBoolean(CLAVE_BORRAR_ORIGINALES, valor)
        }

    /**
     * Archivos pesados al añadirlos: si se incluyen en la copia automática.
     * null = preguntar cada vez; true/false = la elección recordada.
     */
    var incluirPesados: Boolean?
        get() = if (prefs.contains(CLAVE_INCLUIR_PESADOS)) prefs.getBoolean(CLAVE_INCLUIR_PESADOS, true) else null
        set(valor) = prefs.edit {
            if (valor == null) remove(CLAVE_INCLUIR_PESADOS) else putBoolean(CLAVE_INCLUIR_PESADOS, valor)
        }

    /** A partir de cuántos MB un archivo se considera pesado (por defecto, 50; se podrá cambiar en Ajustes). */
    var limitePesadoMb: Int
        get() = prefs.getInt(CLAVE_LIMITE_PESADO, 50)
        set(valor) = prefs.edit { putInt(CLAVE_LIMITE_PESADO, valor) }

    /** «Buscar actualizaciones» en GitHub (lo único para lo que la app usa internet). Desactivado de entrada. */
    var buscarActualizaciones: Boolean
        get() = prefs.getBoolean(CLAVE_BUSCAR_ACTUALIZACIONES, false)
        set(valor) = prefs.edit { putBoolean(CLAVE_BUSCAR_ACTUALIZACIONES, valor) }

    /** Cuándo se preguntó a GitHub por última vez (para hacerlo como mucho una vez al día). */
    var ultimaComprobacion: Long
        get() = prefs.getLong(CLAVE_ULTIMA_COMPROBACION, 0L)
        set(valor) = prefs.edit { putLong(CLAVE_ULTIMA_COMPROBACION, valor) }

    /** La última versión nueva de la que ya se avisó sola (para no repetir el aviso cada día). */
    var versionAvisada: String?
        get() = prefs.getString(CLAVE_VERSION_AVISADA, null)
        set(valor) = prefs.edit { putString(CLAVE_VERSION_AVISADA, valor) }

    private companion object {
        const val CLAVE_BUSCAR_ACTUALIZACIONES = "buscar_actualizaciones"
        const val CLAVE_ULTIMA_COMPROBACION = "ultima_comprobacion"
        const val CLAVE_VERSION_AVISADA = "version_avisada"
        const val CLAVE_INCLUIR_PESADOS = "incluir_pesados"
        const val CLAVE_LIMITE_PESADO = "limite_pesado_mb"
        const val CLAVE_CARPETA = "uri_carpeta"
        const val CLAVE_BORRAR_ORIGINALES = "borrar_originales"
    }
}
