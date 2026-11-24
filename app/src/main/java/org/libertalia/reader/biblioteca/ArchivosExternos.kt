package org.libertalia.reader.biblioteca

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.libertalia.reader.comic.OrigenComic

/**
 * Archivos de fuera de la app (el original de un libro en Descargas, una copia .libertalia
 * que llegó por WhatsApp...) que se pueden borrar para no tener las cosas dos veces.
 *
 * Android solo deja borrar los archivos que el usuario ha dado a la app con el selector
 * (y no siempre: lo que llega con «Abrir con» suele ser solo de lectura). Nunca se ofrece
 * borrar nada que esté en una nube.
 */
object ArchivosExternos {

    /**
     * Si [uri] se puede ofrecer para borrar: está en el móvil (no en una nube), no está dentro
     * de la carpeta de la app y Android permite borrarlo.
     */
    fun sePuedeBorrar(contexto: Context, uri: Uri, carpeta: CarpetaLibertalia): Boolean {
        if (uri.scheme != "content" || OrigenComic.estaEnLaNube(uri.toString())) return false
        if (carpeta.contieneDocumento(uri)) return false
        if (!DocumentsContract.isDocumentUri(contexto, uri)) return false
        val banderas = runCatching {
            contexto.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        }.getOrNull() ?: 0
        return banderas and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
    }

    /**
     * Si [uri] es un archivo del propio móvil (no de una nube) que está fuera de la carpeta de
     * la app: se puede ofrecer quitarlo de allí, aunque para borrarlo haga falta que el usuario
     * lo vuelva a elegir con el selector (con «Abrir con» Android no suele dejar borrarlo).
     */
    fun estaEnElMovil(contexto: Context, uri: Uri, carpeta: CarpetaLibertalia): Boolean {
        if (uri.scheme != "content" || carpeta.contieneDocumento(uri)) return false
        if (!OrigenComic.estaEnLaNube(uri.toString())) return true
        // Los gestores de archivos del móvil (Archivos de Google, Mis archivos de Samsung...)
        // entregan los archivos con su propio proveedor: también son del móvil.
        val proveedor = uri.authority.orEmpty().lowercase()
        return "file" in proveedor && NUBES.none { it in proveedor }
    }

    private val NUBES = listOf("drive", "docs", "dropbox", "skydrive", "onedrive", "mega", "box", "cloud", "pcloud")

    /** Borra el archivo. Devuelve si se ha podido. */
    fun borrar(contexto: Context, uri: Uri): Boolean = runCatching {
        DocumentsContract.deleteDocument(contexto.contentResolver, uri)
    }.getOrDefault(false)

    /** El nombre del archivo («don-quijote.epub»), para enseñarlo al preguntar. */
    fun nombre(contexto: Context, uri: Uri): String = runCatching {
        contexto.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
}
