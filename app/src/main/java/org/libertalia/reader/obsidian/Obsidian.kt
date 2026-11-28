package org.libertalia.reader.obsidian

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.R
import org.libertalia.reader.copias.MarkdownTarjetas
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.datos.TipoTarjeta
import org.libertalia.reader.tarjetas.Etiquetas
import org.libertalia.reader.tarjetas.Fragmentos
import java.security.MessageDigest

/**
 * Vínculo con Obsidian. El usuario elige una vez la carpeta de su bóveda (selector de Android;
 * la app no puede buscarla sola) y la app escribe dentro, en «Libertalia/», un archivo .md por
 * libro con sus subrayados (recuadro `> [!quote]`), sus notas (`> [!note]`) y sus etiquetas
 * (#etiqueta, para que entren en el árbol de etiquetas de Obsidian).
 *
 * En los dos sentidos: antes de escribir se leen los archivos; si el usuario cambió en Obsidian
 * una nota o las etiquetas de una tarjeta (y la tarjeta no ha cambiado después en la app), se
 * pasan a la app. Cada tarjeta lleva una marca que Obsidian no enseña (`%% libertalia:ID %%`).
 * Los datos siguen guardados en la base de datos de la app: los .md son una copia legible.
 */
class Obsidian(private val app: LibertaliaApp) {

    private val prefs = app.getSharedPreferences("obsidian", Context.MODE_PRIVATE)
    private val cerrojo = Mutex()

    /** La carpeta de la bóveda (null = sin vincular). */
    var boveda by mutableStateOf(prefs.getString(CLAVE_BOVEDA, null))
        private set

    /** Resultado de la última vez: null = bien; si no, el texto del problema. */
    var problema by mutableStateOf<Int?>(null)
        private set

    /** El nombre de la carpeta elegida (para enseñarlo en Ajustes). */
    fun nombreBoveda(): String? = boveda?.let { DocumentFile.fromTreeUri(app, Uri.parse(it))?.name }

    fun vincular(uri: Uri) {
        val permisos = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(uri, permisos) }
        // Lo escrito para la bóveda anterior ya no vale.
        prefs.edit { clear(); putString(CLAVE_BOVEDA, uri.toString()) }
        boveda = uri.toString()
        problema = null
    }

    fun desvincular() {
        boveda?.let { anterior ->
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    Uri.parse(anterior),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit { clear() }
        boveda = null
        problema = null
    }

    /**
     * Lee lo cambiado en Obsidian y después escribe los archivos que hayan cambiado en la app.
     * Si no hay bóveda vinculada, no hace nada. Devuelve cuántos archivos escribió.
     */
    suspend fun sincronizar(): Int = withContext(Dispatchers.IO) {
        val uri = boveda ?: return@withContext 0
        cerrojo.withLock {
            runCatching { sincronizarEn(Uri.parse(uri)) }
                .onSuccess { problema = null }
                .onFailure { problema = R.string.obsidian_problema }
                .getOrDefault(0)
        }
    }

    private suspend fun sincronizarEn(uriBoveda: Uri): Int {
        val raiz = DocumentFile.fromTreeUri(app, uriBoveda)?.takeIf { it.exists() && it.canWrite() }
            ?: error("Sin permiso en la bóveda")
        val carpeta = raiz.findFile(CARPETA)?.takeIf { it.isDirectory } ?: raiz.createDirectory(CARPETA)
            ?: error("No se pudo crear la carpeta")

        // Los archivos que ya hay, por el libro al que pertenecen.
        val archivos = mutableMapOf<String, Pair<DocumentFile, String>>()
        carpeta.listFiles().filter { it.isFile && it.name.orEmpty().endsWith(".md") }.forEach { archivo ->
            val texto = leer(archivo) ?: return@forEach
            val idLibro = ID_LIBRO.find(texto)?.groupValues?.get(1) ?: return@forEach
            archivos[idLibro] = archivo to texto
        }

        val libros = app.baseDatos.libros().todos().filter { !it.eliminado }
        val tarjetasDao = app.baseDatos.tarjetas()
        var escritos = 0
        for (libro in libros) {
            var tarjetas = tarjetasDao.todasDeLibro(libro.id)
            val existente = archivos[libro.id]

            // 1. Lo cambiado en Obsidian pasa a la app.
            if (existente != null && huella(existente.second) != prefs.getString(CLAVE_HUELLA + libro.id, null)) {
                val cambiado = existente.first.lastModified()
                if (leerCambios(existente.second, tarjetas, cambiado)) tarjetas = tarjetasDao.todasDeLibro(libro.id)
            }

            // 2. Se escribe el archivo si su contenido cambia.
            val texto = markdown(app, libro, tarjetas)
            if (texto == null) continue
            if (existente?.second == texto) {
                prefs.edit { putString(CLAVE_HUELLA + libro.id, huella(texto)) }
                continue
            }
            val archivo = existente?.first ?: carpeta.createFile("text/markdown", nombreArchivo(libro, carpeta))
                ?: continue
            app.contentResolver.openOutputStream(archivo.uri, "wt")?.use { it.write(texto.toByteArray()) }
                ?: app.contentResolver.openOutputStream(archivo.uri, "w")?.use { it.write(texto.toByteArray()) }
            prefs.edit { putString(CLAVE_HUELLA + libro.id, huella(texto)) }
            escritos++
        }
        return escritos
    }

    /**
     * Busca en el archivo las notas y etiquetas de cada tarjeta y las guarda en la app si son
     * distintas y la tarjeta no se ha cambiado en la app después de cambiar el archivo.
     * Devuelve si cambió algo.
     */
    private suspend fun leerCambios(texto: String, tarjetas: List<Tarjeta>, cambiado: Long): Boolean {
        val dao = app.baseDatos.tarjetas()
        val porId = tarjetas.associateBy { it.id }
        var algo = false
        for ((id, bloque) in bloques(texto)) {
            val tarjeta = porId[id]?.takeIf { !it.eliminada } ?: continue
            if (tarjeta.fechaModificacion > cambiado) continue
            val leido = leerBloque(bloque)
            val nota = leido.nota?.takeIf { it.isNotBlank() }
            if (nota != tarjeta.nota?.trim()?.takeIf { it.isNotEmpty() }) {
                dao.guardarNota(id, nota, cambiado)
                algo = true
            }
            // Las etiquetas de Obsidian no llevan espacios: se respetan las que ya tenía la tarjeta.
            val actuales = Etiquetas.de(tarjeta)
            val nuevas = leido.etiquetas.map { leida -> actuales.firstOrNull { etiquetaObsidian(it) == leida } ?: leida }
            if (nuevas.toSet() != actuales.toSet()) {
                dao.guardarEtiquetas(id, nuevas.distinct().joinToString(", ").ifEmpty { null }, cambiado)
                algo = true
            }
        }
        return algo
    }

    private fun leer(archivo: DocumentFile): String? =
        runCatching { app.contentResolver.openInputStream(archivo.uri)?.use { String(it.readBytes()) } }.getOrNull()

    private fun nombreArchivo(libro: Libro, carpeta: DocumentFile): String {
        val base = libro.titulo.replace(Regex("[\\\\/:*?\"<>|#^\\[\\]]"), " ").replace(Regex("\\s+"), " ").trim()
            .take(80).ifEmpty { "Libro" }
        var nombre = "$base.md"
        var n = 2
        while (carpeta.findFile(nombre) != null) nombre = "$base ($n).md".also { n++ }
        return nombre
    }

    companion object {
        const val CARPETA = "Libertalia"
        private const val CLAVE_BOVEDA = "boveda"
        private const val CLAVE_HUELLA = "huella_"

        private val ID_LIBRO = Regex("""(?m)^libertalia:\s*"?([^"\s]+)"?\s*$""")
        private val MARCA = Regex("""%%\s*libertalia:([^\s%]+)\s*%%""")
        private val ETIQUETA = Regex("""(?<![\w/#])#([\p{L}\p{N}_/-]+)""")

        /** Una etiqueta como la escribe Obsidian (sin espacios). */
        fun etiquetaObsidian(etiqueta: String) = etiqueta.trim().replace(Regex("\\s+"), "_")

        /**
         * El archivo de un libro: cabecera con su id (para reconocerlo aunque cambie de nombre),
         * título, autor y una sección por tarjeta (los subrayados unidos van juntos). null si el
         * libro no tiene subrayados.
         */
        fun markdown(contexto: Context, libro: Libro, tarjetas: List<Tarjeta>): String? {
            val subrayados = Fragmentos.agrupar(
                tarjetas.filter { it.tipo == TipoTarjeta.SUBRAYADO && !it.eliminada }
                    .sortedWith(compareBy({ it.progreso }, { it.fechaCreacion })),
            )
            if (subrayados.isEmpty()) return null
            val md = StringBuilder()
            md.append("---\n")
            md.append("libro: \"").append(MarkdownTarjetas.unaLinea(libro.titulo).replace("\"", "'")).append("\"\n")
            if (libro.autor.isNotBlank()) md.append("autor: \"").append(MarkdownTarjetas.unaLinea(libro.autor).replace("\"", "'")).append("\"\n")
            md.append("libertalia: ").append(libro.id).append("\n")
            md.append("---\n\n")
            md.append("# ").append(MarkdownTarjetas.unaLinea(libro.titulo)).append("\n\n")
            if (libro.autor.isNotBlank()) md.append("*").append(MarkdownTarjetas.unaLinea(libro.autor)).append("*\n\n")
            subrayados.forEachIndexed { i, t ->
                val titulo = listOfNotNull("${i + 1}.", MarkdownTarjetas.lugar(contexto, libro, t)).joinToString(" ")
                md.append("> [!quote] ").append(titulo).append("\n")
                MarkdownTarjetas.fraseConSubrayado(t).lines().forEach { md.append("> ").append(it).append("\n") }
                md.append("\n%% libertalia:").append(t.id).append(" %%\n\n")
                t.nota?.trim()?.takeIf { it.isNotEmpty() }?.let { nota ->
                    md.append("> [!note] ").append(contexto.getString(R.string.obsidian_mi_nota)).append("\n")
                    nota.lines().forEach { md.append("> ").append(it).append("\n") }
                    md.append("\n")
                }
                val etiquetas = Etiquetas.de(t)
                if (etiquetas.isNotEmpty()) md.append(etiquetas.joinToString(" ") { "#" + etiquetaObsidian(it) }).append("\n\n")
                md.append("---\n\n")
            }
            return md.toString()
        }

        /** Cada tarjeta (por su id) con el texto que la sigue hasta la próxima cita. */
        fun bloques(texto: String): List<Pair<String, String>> {
            val marcas = MARCA.findAll(texto).toList()
            return marcas.mapIndexed { i, marca ->
                val desde = marca.range.last + 1
                val hasta = marcas.getOrNull(i + 1)?.range?.first ?: texto.length
                var bloque = texto.substring(desde, hasta)
                // La cita de la tarjeta siguiente no es de esta.
                Regex("""(?m)^>\s*\[!quote]""").find(bloque)?.let { bloque = bloque.substring(0, it.range.first) }
                marca.groupValues[1] to bloque
            }
        }

        /** Lo que el usuario puede cambiar en Obsidian: la nota y las etiquetas. */
        data class Bloque(val nota: String?, val etiquetas: List<String>)

        /**
         * La nota es lo que hay en el recuadro `> [!note]`; si no lo hay, el texto suelto que se
         * haya escrito debajo de la cita. Las etiquetas, todas las #etiqueta del bloque.
         */
        fun leerBloque(bloque: String): Bloque {
            val lineas = bloque.lines()
            val etiquetas = lineas.filterNot { it.trimStart().startsWith(">") }
                .flatMap { linea -> ETIQUETA.findAll(linea).map { it.groupValues[1] }.toList() }
                .distinct()
            val inicioNota = lineas.indexOfFirst { Regex("""^>\s*\[!note]""", RegexOption.IGNORE_CASE).containsMatchIn(it.trimStart()) }
            val nota = if (inicioNota >= 0) {
                lineas.drop(inicioNota + 1).takeWhile { it.trimStart().startsWith(">") }
                    .joinToString("\n") { it.trimStart().removePrefix(">").removePrefix(" ") }.trim()
            } else {
                lineas.map { it.trim() }
                    .filter { it.isNotEmpty() && it != "---" && !it.startsWith(">") }
                    .map { linea -> ETIQUETA.replace(linea, "").trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n").trim()
            }
            return Bloque(nota.ifEmpty { null }, etiquetas)
        }

        private fun huella(texto: String): String =
            MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
