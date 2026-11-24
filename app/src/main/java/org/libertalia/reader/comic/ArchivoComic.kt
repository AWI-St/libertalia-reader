package org.libertalia.reader.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

/**
 * Un cómic CBZ (ZIP) o CBR (RAR) abierto: la lista de sus páginas en orden
 * y la forma de leer cada una.
 */
interface ArchivoComic : Closeable {
    /** Nombres de las imágenes, en el orden de lectura. */
    val paginas: List<String>

    /** Los bytes de una página (una imagen). */
    fun leer(nombre: String): ByteArray

    /** El contenido de ComicInfo.xml, si el cómic lo trae. */
    fun comicInfo(): String?

    companion object {
        /** Abre un archivo según su formato ("cbz" o "cbr"). */
        fun abrir(archivo: File, esRar: Boolean): ArchivoComic = if (esRar) ComicRar(archivo) else ComicZip(archivo)

        private val EXTENSIONES_IMAGEN = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

        fun esImagen(nombre: String): Boolean {
            val limpio = nombre.substringAfterLast('/').substringAfterLast('\\')
            // Fuera archivos ocultos y los que crea macOS («__MACOSX/._01.jpg»).
            if (limpio.startsWith(".") || nombre.contains("__MACOSX")) return false
            return limpio.substringAfterLast('.', "").lowercase() in EXTENSIONES_IMAGEN
        }

        /**
         * Orden «natural»: «pagina 2» va antes que «pagina 10» (como haría una persona,
         * no como el orden alfabético, que pondría la 10 antes que la 2).
         */
        val ORDEN_NATURAL: Comparator<String> = Comparator { a, b ->
            val trozosA = TROZOS.findAll(a.lowercase()).map { it.value }.toList()
            val trozosB = TROZOS.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(trozosA.size, trozosB.size)) {
                val x = trozosA[i]
                val y = trozosB[i]
                val comparacion = if (x[0].isDigit() && y[0].isDigit()) {
                    x.trimStart('0').length.compareTo(y.trimStart('0').length)
                        .takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
                } else {
                    x.compareTo(y)
                }
                if (comparacion != 0) return@Comparator comparacion
            }
            trozosA.size.compareTo(trozosB.size)
        }
        private val TROZOS = Regex("\\d+|\\D+")
    }
}

/** Cómic en ZIP (.cbz). */
private class ComicZip(archivo: File) : ArchivoComic {
    private val zip = ZipFile(archivo)

    override val paginas: List<String> = zip.entries().asSequence()
        .filter { !it.isDirectory && ArchivoComic.esImagen(it.name) }
        .map { it.name }
        .sortedWith(ArchivoComic.ORDEN_NATURAL)
        .toList()

    override fun leer(nombre: String): ByteArray =
        zip.getInputStream(zip.getEntry(nombre) ?: error("No está: $nombre")).use { it.readBytes() }

    override fun comicInfo(): String? = zip.entries().asSequence()
        .firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true) }
        ?.let { entrada -> zip.getInputStream(entrada).use { it.readBytes().toString(Charsets.UTF_8) } }

    override fun close() = zip.close()
}

/** Cómic en RAR (.cbr). junrar solo abre RAR 4 y anteriores. */
private class ComicRar(archivo: File) : ArchivoComic {
    private val rar = Archive(archivo)
    private val cabeceras: Map<String, FileHeader> = rar.fileHeaders
        .filter { !it.isDirectory }
        .associateBy { it.fileName.replace('\\', '/') }

    override val paginas: List<String> = cabeceras.keys
        .filter { ArchivoComic.esImagen(it) }
        .sortedWith(ArchivoComic.ORDEN_NATURAL)

    override fun leer(nombre: String): ByteArray {
        val cabecera = cabeceras[nombre] ?: error("No está: $nombre")
        return ByteArrayOutputStream().also { rar.extractFile(cabecera, it) }.toByteArray()
    }

    override fun comicInfo(): String? = cabeceras.entries
        .firstOrNull { it.key.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true) }
        ?.let { leer(it.key).toString(Charsets.UTF_8) }

    override fun close() = rar.close()
}

/** Abre una imagen reduciéndola para que su alto no pase mucho de [altoMaximo]. */
fun decodificarReducida(bytes: ByteArray, altoMaximo: Int): Bitmap? {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
    var reduccion = 1
    while (medidas.outHeight / (reduccion * 2) >= altoMaximo) reduccion *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = reduccion })
}
