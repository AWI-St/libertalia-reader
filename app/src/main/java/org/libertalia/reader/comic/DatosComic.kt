package org.libertalia.reader.comic

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Lo que se saca de un CBZ o CBR al añadirlo a la biblioteca. */
class DatosComic(
    val ficha: FichaComic,
    val numeroPaginas: Int,
    val portada: Bitmap?,
)

object LectorDatosComic {

    /** Lee las páginas, la ficha ComicInfo.xml y la portada (primera página). null = no es un cómic. */
    fun leer(archivo: File, esRar: Boolean): DatosComic? =
        ArchivoComic.abrir(archivo, esRar).use { comic ->
            if (comic.paginas.isEmpty()) return null
            val portada = runCatching { decodificarReducida(comic.leer(comic.paginas.first()), 900) }.getOrNull()
            DatosComic(FichaComic.leer(runCatching { comic.comicInfo() }.getOrNull()), comic.paginas.size, portada)
        }

    /**
     * Un PDF es de cómic si sus páginas son solo imágenes: se miran las primeras
     * y, si casi no tienen texto, se trata como cómic (se puede cambiar en el menú del libro).
     */
    fun esPdfDeImagenes(contexto: Context, archivo: File): Boolean = runCatching {
        PDFBoxResourceLoader.init(contexto.applicationContext)
        PDDocument.load(archivo, MemoryUsageSetting.setupTempFileOnly()).use { documento ->
            val revisar = minOf(documento.numberOfPages, 8)
            if (revisar == 0) return@use false
            val extractor = PDFTextStripper().apply {
                startPage = 1
                endPage = revisar
            }
            val letras = extractor.getText(documento).count { it.isLetterOrDigit() }
            letras < revisar * 15
        }
    }.getOrDefault(false)
}
