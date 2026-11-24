package org.libertalia.reader.comic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * Los cómics no se copian a la carpeta de la app (pueden pesar mucho): se leen siempre
 * desde donde están. Para poder volver a abrirlos otro día, la app necesita un permiso
 * permanente sobre el archivo. Aquí se consigue:
 *
 * 1. Si el archivo llegó con «Añadir», Android da el permiso permanente directamente.
 * 2. Si llegó con «Abrir con» desde otra app (WhatsApp, Telegram...), el permiso suele ser
 *    solo para ese momento. Entonces se busca el mismo archivo dentro de alguna carpeta
 *    a la que la app ya tiene acceso (la carpeta Libertalia o una que el usuario eligió
 *    antes para sus cómics, por ejemplo «Download»).
 * 3. Si no está en ninguna, se le pide al usuario que elija ese mismo archivo con el selector
 *    de Android (que se abre ya en su carpeta): así el permiso pasa a ser permanente.
 *    (No se le pide la carpeta: Android no deja usar la carpeta «Download» entera.)
 */
object OrigenComic {

    /** Una dirección del archivo que la app podrá seguir abriendo, o null si no la hay. */
    fun direccionPermanente(contexto: Context, origen: Uri, nombre: String, tamano: Long): Uri? {
        val resolver = contexto.contentResolver
        val permanente = runCatching {
            resolver.takePersistableUriPermission(origen, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (permanente) return origen
        return buscarEnCarpetas(contexto, carpetasConPermiso(contexto), origen, nombre, tamano)
    }

    /** Las carpetas a las que la app tiene permiso permanente de lectura. */
    fun carpetasConPermiso(contexto: Context): List<Uri> =
        contexto.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { it.uri }

    /**
     * Busca el archivo en esas carpetas: primero por su identificador (si es del mismo
     * almacenamiento, como «Download/comic.cbz») y si no, por nombre y tamaño.
     */
    fun buscarEnCarpetas(contexto: Context, carpetas: List<Uri>, origen: Uri, nombre: String, tamano: Long): Uri? {
        // 1. Mismo almacenamiento: la dirección se construye sin recorrer la carpeta.
        val idDocumento = runCatching {
            if (DocumentsContract.isDocumentUri(contexto, origen)) DocumentsContract.getDocumentId(origen) else null
        }.getOrNull()
        if (idDocumento != null) {
            for (carpeta in carpetas) {
                if (carpeta.authority != origen.authority) continue
                val idCarpeta = DocumentsContract.getTreeDocumentId(carpeta)
                if (idDocumento.startsWith("$idCarpeta/")) {
                    val dentro = DocumentsContract.buildDocumentUriUsingTree(carpeta, idDocumento)
                    if (sePuedeLeer(contexto, dentro)) return dentro
                }
            }
        }
        // 2. Por nombre y tamaño, mirando también un par de niveles de subcarpetas.
        for (carpeta in carpetas) {
            val raiz = DocumentFile.fromTreeUri(contexto, carpeta) ?: continue
            buscar(raiz, nombre, tamano, profundidad = 3)?.let { return it }
        }
        return null
    }

    private fun buscar(carpeta: DocumentFile, nombre: String, tamano: Long, profundidad: Int): Uri? {
        val hijos = runCatching { carpeta.listFiles() }.getOrDefault(emptyArray())
        hijos.firstOrNull { it.isFile && it.name == nombre && it.length() == tamano }?.let { return it.uri }
        if (profundidad <= 1) return null
        for (hijo in hijos) {
            if (hijo.isDirectory) buscar(hijo, nombre, tamano, profundidad - 1)?.let { return it }
        }
        return null
    }

    /** Proveedores de archivos que están en el propio móvil (todo lo demás es una nube). */
    private val PROVEEDORES_LOCALES = setOf(
        "com.android.externalstorage.documents",
        "com.android.providers.downloads.documents",
        "com.android.providers.media.documents",
        "media",
        "downloads",
    )

    /**
     * Si el archivo está en una nube (Google Drive, Dropbox, OneDrive...) y no en el móvil.
     * Leer desde la nube gasta datos (y la primera vez tarda): se recomienda guardarlo en el móvil.
     */
    fun estaEnLaNube(uri: String?): Boolean {
        val direccion = uri?.let(Uri::parse) ?: return false
        if (direccion.scheme != "content") return false
        return direccion.authority !in PROVEEDORES_LOCALES
    }

    /** Si el archivo elegido es el mismo cómic: mismo tamaño y misma huella (SHA-256). */
    fun esElMismo(contexto: Context, archivo: Uri, tamano: Long, huella: String): Boolean = runCatching {
        val resolver = contexto.contentResolver
        val documento = DocumentFile.fromSingleUri(contexto, archivo)
        if (documento != null && documento.length() > 0 && documento.length() != tamano) return false
        val resumen = java.security.MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(archivo)?.use { entrada ->
            val bufer = ByteArray(64 * 1024)
            while (true) {
                val leidos = entrada.read(bufer)
                if (leidos < 0) break
                resumen.update(bufer, 0, leidos)
            }
        } ?: return false
        resumen.digest().joinToString("") { "%02x".format(it) } == huella
    }.getOrDefault(false)

    private fun sePuedeLeer(contexto: Context, uri: Uri): Boolean = runCatching {
        contexto.contentResolver.openInputStream(uri)?.use { it.read() }
        true
    }.getOrDefault(false)
}
