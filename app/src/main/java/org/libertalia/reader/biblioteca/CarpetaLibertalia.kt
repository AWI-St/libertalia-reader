package org.libertalia.reader.biblioteca

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.libertalia.reader.datos.Preferencias
import java.io.File
import java.io.InputStream

/**
 * La carpeta visible donde se guardan los libros (por defecto Documentos/Libertalia).
 *
 * Android solo deja que una app lea una carpeta pública si el usuario la elige
 * con el selector del sistema. Al elegirla, la app guarda un permiso permanente,
 * así los libros siguen ahí aunque se desinstale la app y se pueden volver a leer.
 */
class CarpetaLibertalia(
    private val contexto: Context,
    private val preferencias: Preferencias,
) {

    /** La carpeta elegida, si todavía tenemos permiso para usarla. */
    fun carpeta(): DocumentFile? {
        val uri = preferencias.uriCarpeta?.let(Uri::parse) ?: return null
        val tienePermiso = contexto.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        if (!tienePermiso) return null
        return DocumentFile.fromTreeUri(contexto, uri)?.takeIf { it.exists() }
    }

    fun estaElegida(): Boolean = carpeta() != null

    /**
     * Si el archivo está dentro de la carpeta de la app. Los cómics se abren desde donde
     * estén (no se copian): esos no se renombran ni se borran desde la app.
     */
    fun contiene(uriArchivo: String?): Boolean {
        val carpeta = preferencias.uriCarpeta ?: return false
        return uriArchivo?.startsWith("$carpeta/document/") == true
    }

    /**
     * Si un documento elegido con el selector (dirección «…/document/…») está dentro de la
     * carpeta de la app. Se compara su identificador («primary:Documents/Libertalia/x.pdf»)
     * con el de la carpeta.
     */
    fun contieneDocumento(uri: Uri): Boolean {
        if (contiene(uri.toString())) return true
        val carpeta = preferencias.uriCarpeta?.let(Uri::parse) ?: return false
        val idCarpeta = runCatching { DocumentsContract.getTreeDocumentId(carpeta) }.getOrNull() ?: return false
        if (uri.authority != carpeta.authority) return false
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return false
        return id == idCarpeta || id.startsWith("$idCarpeta/")
    }

    /**
     * Intenta crear Documentos/Libertalia y devuelve su dirección, para que el selector
     * de Android se abra directamente allí y al usuario le baste con pulsar "Usar esta carpeta".
     */
    fun uriInicialSugerida(): Uri? {
        runCatching {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                NOMBRE_CARPETA,
            ).mkdirs()
        }
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:${Environment.DIRECTORY_DOCUMENTS}/$NOMBRE_CARPETA",
        )
    }

    /** Guarda la carpeta que ha elegido el usuario, con permiso permanente. */
    fun guardarEleccion(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contexto.contentResolver.takePersistableUriPermission(uri, flags)
        preferencias.uriCarpeta = uri.toString()
    }

    /**
     * Copia un archivo a la carpeta. Si ya existe uno con ese nombre,
     * añade " (2)", " (3)"... para no sobrescribir nada.
     * Devuelve el archivo creado, o null si no se pudo.
     */
    fun guardarArchivo(nombreDeseado: String, tipoMime: String, origen: InputStream): DocumentFile? {
        val carpeta = carpeta() ?: return null
        val nombre = nombreLibre(carpeta, limpiarNombre(nombreDeseado))
        val archivo = carpeta.createFile(tipoMime, nombre) ?: return null
        contexto.contentResolver.openOutputStream(archivo.uri)?.use { salida ->
            origen.copyTo(salida)
        } ?: return null
        return archivo
    }

    /**
     * Busca en la carpeta un archivo con el mismo contenido que otro, para no duplicarlo.
     * Solo se comprueba la huella de los archivos que pesan exactamente lo mismo.
     */
    fun buscarIgual(tamano: Long, esIgual: (DocumentFile) -> Boolean): DocumentFile? = runCatching {
        carpeta()?.listFiles()?.firstOrNull { it.isFile && it.length() == tamano && esIgual(it) }
    }.getOrNull()

    /**
     * Cambia el nombre de un archivo de la carpeta (el contenido no se toca).
     * Devuelve el archivo con su nueva dirección, o null si no se pudo.
     */
    fun renombrar(uriArchivo: Uri, nombreDeseado: String): DocumentFile? = runCatching {
        val carpeta = carpeta() ?: return null
        val actual = DocumentFile.fromSingleUri(contexto, uriArchivo)
        val limpio = limpiarNombre(nombreDeseado)
        if (actual?.name == limpio) return actual
        val nombre = nombreLibre(carpeta, limpio)
        val nueva = DocumentsContract.renameDocument(contexto.contentResolver, uriArchivo, nombre)
            ?: return null
        DocumentFile.fromSingleUri(contexto, nueva)
    }.getOrNull()

    /** Borra un archivo de la carpeta. Devuelve true si se borró (o ya no existía). */
    fun borrar(uriArchivo: Uri): Boolean = runCatching {
        val archivo = DocumentFile.fromSingleUri(contexto, uriArchivo)
        archivo == null || !archivo.exists() ||
            DocumentsContract.deleteDocument(contexto.contentResolver, uriArchivo)
    }.getOrDefault(false)

    private fun nombreLibre(carpeta: DocumentFile, nombre: String): String {
        if (carpeta.findFile(nombre) == null) return nombre
        val base = nombre.substringBeforeLast('.')
        val extension = nombre.substringAfterLast('.', "")
        var n = 2
        while (true) {
            val candidato = if (extension.isEmpty()) "$base ($n)" else "$base ($n).$extension"
            if (carpeta.findFile(candidato) == null) return candidato
            n++
        }
    }

    /** Quita caracteres que no se pueden usar en nombres de archivo. */
    private fun limpiarNombre(nombre: String): String =
        nombre.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifEmpty { "libro" }

    companion object {
        const val NOMBRE_CARPETA = "Libertalia"
    }
}
