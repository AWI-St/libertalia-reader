package org.libertalia.reader.biblioteca

import android.content.Context
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.datos.Libro
import java.io.File
import java.util.UUID

/**
 * «Solo leer»: un libro o cómic que llegó con «Abrir con» y se lee sin añadirlo a la
 * biblioteca. No se guarda en la base de datos (las órdenes de guardar la posición, el
 * estado... no encuentran ese libro y no hacen nada), no cuenta para la racha ni para las
 * estadísticas y no se puede subrayar. Su archivo es una copia en la caché y, al cerrar el
 * lector, se borra todo lo que haya dejado (también lo que prepara el modo texto).
 */
object LibroDePaso {

    private const val PREFIJO = "de-paso-"

    /** Los archivos de preferencias donde la app guarda cosas con el id del libro en la clave. */
    private val PREFERENCIAS = listOf("modo_texto", "paginas", "comic", "voz", "lector")

    /** El libro de paso abierto ahora (para el lector de cómics, que busca el libro por su id). */
    @Volatile
    var actual: Libro? = null

    fun nuevoId(): String = PREFIJO + UUID.randomUUID().toString()

    fun esDePaso(idLibro: String?): Boolean = idLibro?.startsWith(PREFIJO) == true

    private fun carpeta(contexto: Context) = File(contexto.cacheDir, "de-paso")

    /** Dónde se guarda la copia del archivo mientras se lee. */
    fun archivo(contexto: Context, idLibro: String, formato: Formato): File =
        File(carpeta(contexto), "$idLibro.${formato.name.lowercase()}")

    /** Borra todo lo que ha dejado el libro de paso [idLibro]. */
    fun limpiar(contexto: Context, idLibro: String) {
        if (!esDePaso(idLibro)) return
        if (actual?.id == idLibro) actual = null
        carpeta(contexto).listFiles { f -> f.name.startsWith(idLibro) }?.forEach { it.delete() }
        File(contexto.filesDir, "modo-texto/$idLibro").deleteRecursively()
        quitarPreferencias(contexto) { it.contains(idLibro) }
    }

    /** Al arrancar la app: restos de un libro de paso que no se pudieron borrar (la app se cerró). */
    fun limpiarRestos(contexto: Context) {
        if (actual != null) return
        carpeta(contexto).deleteRecursively()
        File(contexto.filesDir, "modo-texto").listFiles { f -> esDePaso(f.name) }?.forEach { it.deleteRecursively() }
        quitarPreferencias(contexto) { it.contains(PREFIJO) }
    }

    private fun quitarPreferencias(contexto: Context, quitar: (String) -> Boolean) {
        PREFERENCIAS.forEach { nombre ->
            val prefs = contexto.getSharedPreferences(nombre, Context.MODE_PRIVATE)
            val claves = prefs.all.keys.filter(quitar)
            if (claves.isNotEmpty()) prefs.edit().apply { claves.forEach(::remove) }.apply()
        }
    }
}

/** Si es un libro de paso («Solo leer»): no está en la biblioteca y no se guarda nada de él. */
val Libro.esDePaso: Boolean get() = LibroDePaso.esDePaso(id)
