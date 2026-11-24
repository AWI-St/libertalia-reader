package org.libertalia.reader.copias

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.biblioteca.Portadas
import org.libertalia.reader.datos.Libro
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/** Lo que ha quedado en la copia. [sinArchivo]: títulos cuyo archivo no se pudo leer. */
data class ResumenExportacion(
    val elementos: Int,
    val tarjetas: Int,
    val bytes: Long,
    val sinArchivo: List<String>,
)

/**
 * Prepara una copia .libertalia (ver [FormatoCopia]) con los libros y cómics elegidos.
 * La escribe en un archivo del móvil; después se guarda donde diga el usuario o se comparte.
 */
class Exportador(private val app: LibertaliaApp) {

    private val contexto: Context get() = app

    /** Lo que pesará más o menos un libro en la copia (con o sin su archivo). */
    fun pesoEstimado(libro: Libro, incluirArchivo: Boolean): Long {
        val archivo = if (incluirArchivo && libro.uriArchivo != null) libro.tamanoBytes else 0L
        val portada = Portadas.archivo(contexto, libro.id).length()
        return archivo + portada + DATOS_APROXIMADOS
    }

    /**
     * Escribe la copia en [destino]. [incluirArchivo] decide, libro a libro, si va su archivo.
     * [alAvanzar] recibe de 0 a 1 según avanza (para la barra de progreso).
     */
    suspend fun exportar(
        libros: List<Libro>,
        incluirArchivo: (Libro) -> Boolean,
        destino: File,
        alAvanzar: (Float) -> Unit = {},
    ): ResumenExportacion = withContext(Dispatchers.IO) {
        val bd = app.baseDatos
        val total = libros.sumOf { pesoEstimado(it, incluirArchivo(it)) }.coerceAtLeast(1)
        var hecho = 0L
        fun avanzar(bytes: Long) {
            hecho += bytes
            alAvanzar((hecho.toFloat() / total).coerceIn(0f, 1f))
        }

        val relaciones = bd.estanterias().relaciones().groupBy({ it.libroId }, { it.estanteriaId })
        val listaManifiesto = JSONArray()
        val nombresLegibles = mutableSetOf<String>()
        val sinArchivo = mutableListOf<String>()
        var numeroTarjetas = 0
        val ahora = System.currentTimeMillis()

        destino.parentFile?.mkdirs()
        ZipOutputStream(destino.outputStream().buffered()).use { zip ->
            for (libro in libros) {
                coroutineContext.ensureActive()
                val carpeta = FormatoCopia.carpetaDe(libro)
                val tarjetas = bd.tarjetas().todasDeLibro(libro.id)
                numeroTarjetas += tarjetas.count { !it.eliminada }

                // 1. El archivo original (los libros, epub o pdf, ya van comprimidos: se guardan tal cual).
                var conArchivo = false
                if (incluirArchivo(libro) && libro.uriArchivo != null) {
                    val entrada = runCatching { contexto.contentResolver.openInputStream(Uri.parse(libro.uriArchivo)) }.getOrNull()
                    if (entrada == null) {
                        sinArchivo += libro.titulo
                    } else {
                        zip.setLevel(Deflater.NO_COMPRESSION)
                        zip.putNextEntry(ZipEntry(carpeta + FormatoCopia.nombreArchivo(libro.formato)))
                        entrada.use { copiar(it, zip, ::avanzar) }
                        zip.closeEntry()
                        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                        conArchivo = true
                    }
                }

                // 2. La portada.
                val portada = Portadas.archivo(contexto, libro.id)
                if (portada.exists()) {
                    zip.setLevel(Deflater.NO_COMPRESSION)
                    zip.putNextEntry(ZipEntry(carpeta + FormatoCopia.PORTADA))
                    portada.inputStream().use { copiar(it, zip, ::avanzar) }
                    zip.closeEntry()
                    zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                }

                // 3. Sus datos y todas sus tarjetas (también las borradas, para que no resuciten).
                val datos = JSONObject()
                    .put("libro", FormatoCopia.libroAJson(libro))
                    .put("estanterias", JSONArray(relaciones[libro.id].orEmpty()))
                    .put("tarjetas", JSONArray(tarjetas.map(FormatoCopia::tarjetaAJson)))
                    .put("preferencias", preferenciasDe(libro.id))
                zip.putNextEntry(ZipEntry(carpeta + FormatoCopia.DATOS))
                zip.write(datos.toString(1).toByteArray())
                zip.closeEntry()

                // 4. Las tarjetas para leer fuera de la app.
                MarkdownTarjetas.de(contexto, libro, tarjetas, ahora)?.let { md ->
                    val nombre = nombreLegible(libro.titulo, nombresLegibles)
                    zip.putNextEntry(ZipEntry(FormatoCopia.CARPETA_LEGIBLE + nombre))
                    zip.write(md.toByteArray())
                    zip.closeEntry()
                }
                avanzar(DATOS_APROXIMADOS)

                listaManifiesto.put(
                    JSONObject()
                        .put("id", libro.id)
                        .put("titulo", libro.titulo)
                        .put("autor", libro.autor)
                        .put("tipo", libro.tipo.name)
                        .put("carpeta", carpeta)
                        .put("conArchivo", conArchivo)
                        .put("tamanoBytes", libro.tamanoBytes),
                )
            }

            // 5. El manifiesto, con las estanterías y los días leídos (la racha).
            val manifiesto = JSONObject()
                .put("formato", FormatoCopia.VERSION)
                .put("app", "Libertalia Reader")
                .put("versionApp", versionApp())
                .put("fecha", ahora)
                .put("libros", listaManifiesto)
                .put("estanterias", JSONArray(bd.estanterias().todas().map(FormatoCopia::estanteriaAJson)))
                .put("diasLectura", JSONArray(bd.diasLectura().desde("").map(FormatoCopia::diaAJson)))
            zip.putNextEntry(ZipEntry(FormatoCopia.MANIFIESTO))
            zip.write(manifiesto.toString(1).toByteArray())
            zip.closeEntry()
        }
        alAvanzar(1f)
        ResumenExportacion(libros.size, numeroTarjetas, destino.length(), sinArchivo)
    }

    /**
     * Ajustes de este libro guardados fuera de la base de datos: si se lee en modo texto
     * (y su posición allí), si separa las notas al pie, el modo de lectura del cómic...
     */
    private fun preferenciasDe(idLibro: String): JSONArray {
        val lista = JSONArray()
        for (archivo in PREFERENCIAS_POR_LIBRO) {
            val prefs = contexto.getSharedPreferences(archivo, Context.MODE_PRIVATE)
            prefs.all.forEach { (clave, valor) ->
                if (!clave.endsWith("_$idLibro") || valor == null) return@forEach
                val tipo = when (valor) {
                    is Boolean -> "boolean"
                    is String -> "string"
                    is Int -> "int"
                    is Long -> "long"
                    is Float -> "float"
                    else -> return@forEach
                }
                lista.put(
                    JSONObject()
                        .put("archivo", archivo)
                        .put("clave", clave.removeSuffix("_$idLibro"))
                        .put("tipo", tipo)
                        .put("valor", valor),
                )
            }
        }
        return lista
    }

    private fun versionApp(): String = runCatching {
        contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** «Don Quijote de la Mancha.md» (sin signos que no admiten los sistemas de archivos). */
    private fun nombreLegible(titulo: String, usados: MutableSet<String>): String {
        val base = titulo.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "-").trim().take(80).ifEmpty { "libro" }
        var nombre = "$base.md"
        var n = 2
        while (!usados.add(nombre.lowercase())) nombre = "$base ($n).md".also { n++ }
        return nombre
    }

    private fun copiar(entrada: InputStream, salida: OutputStream, alCopiar: (Long) -> Unit) {
        val bufer = ByteArray(64 * 1024)
        while (true) {
            val leidos = entrada.read(bufer)
            if (leidos < 0) break
            salida.write(bufer, 0, leidos)
            alCopiar(leidos.toLong())
        }
    }

    companion object {
        /** Lo que ocupan, más o menos, los datos y las tarjetas de un libro. */
        private const val DATOS_APROXIMADOS = 8 * 1024L

        /** Archivos de preferencias con ajustes por libro (claves que acaban en «_<id>»). */
        val PREFERENCIAS_POR_LIBRO = listOf("modo_texto", "comic")

        /**
         * Copia [origen] encima del archivo [uri] elegido por el usuario (en el móvil o en
         * una nube). Usa el modo «wt» (borrar y escribir) y, si la nube no lo admite, «w».
         */
        fun escribirEn(contexto: Context, uri: Uri, origen: File) {
            val resolver = contexto.contentResolver
            val salida = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(uri, "w")
                ?: throw FileNotFoundException("No se puede abrir el archivo")
            salida.use { s -> origen.inputStream().use { it.copyTo(s, 64 * 1024) } }
        }
    }
}
