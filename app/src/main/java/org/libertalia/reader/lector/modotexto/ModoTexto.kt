package org.libertalia.reader.lector.modotexto

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.libertalia.reader.R
import org.libertalia.reader.datos.Libro
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Dónde empieza cada página del PDF dentro del EPUB del modo texto,
 * para pasar de un modo a otro sin perder el sitio.
 */
class MapaTexto(
    /** Cómo llama Readium al archivo del PDF (para crear posiciones del PDF). */
    val hrefPdf: String,
    val totalPaginas: Int,
    val partes: List<Parte>,
) {
    /** Un archivo del EPUB y, de cada página del PDF que contiene, dónde empieza (0 a 1). */
    class Parte(val archivo: String, val paginas: List<Pair<Int, Double>>)

    /** La página del PDF que corresponde a una posición del modo texto. */
    fun paginaDe(posicion: Locator): Int? {
        val href = posicion.href.toString().substringBefore('#')
        val parte = partes.firstOrNull { href.endsWith(it.archivo) } ?: return null
        val progreso = posicion.locations.progression ?: 0.0
        return parte.paginas.lastOrNull { it.second <= progreso + 0.0001 }?.first ?: parte.paginas.firstOrNull()?.first
    }

    /** La posición del modo texto donde empieza una página del PDF. */
    fun posicionTexto(pagina: Int, publicacion: Publication): Locator? {
        val parte = partes.firstOrNull { p -> p.paginas.any { it.first == pagina } }
            ?: partes.lastOrNull { p -> (p.paginas.firstOrNull()?.first ?: Int.MAX_VALUE) <= pagina }
            ?: return null
        val inicio = parte.paginas.firstOrNull { it.first == pagina }?.second ?: 0.0
        val enlace = publicacion.readingOrder.firstOrNull { it.href.toString().endsWith(parte.archivo) } ?: return null
        return publicacion.locatorFromLink(enlace)?.copyWithLocations(progression = inicio)
    }

    /** La posición del PDF original para una página (como la guarda Readium). */
    fun posicionPdf(pagina: Int): Locator? {
        val total = totalPaginas.coerceAtLeast(1)
        val json = JSONObject()
            .put("href", hrefPdf)
            .put("type", "application/pdf")
            .put(
                "locations",
                JSONObject()
                    .put("fragments", JSONArray().put("page=$pagina"))
                    .put("position", pagina)
                    .put("totalProgression", (pagina - 1).toDouble() / total),
            )
        return Locator.fromJSON(json)
    }

    fun toJSON(): JSONObject = JSONObject()
        .put("hrefPdf", hrefPdf)
        .put("totalPaginas", totalPaginas)
        .put("partes", JSONArray().apply {
            partes.forEach { parte ->
                put(JSONObject().put("archivo", parte.archivo).put("paginas", JSONArray().apply {
                    parte.paginas.forEach { (pagina, inicio) -> put(JSONArray().put(pagina).put(inicio)) }
                }))
            }
        })

    companion object {
        fun fromJSON(json: JSONObject): MapaTexto {
            val partes = json.getJSONArray("partes")
            return MapaTexto(
                hrefPdf = json.getString("hrefPdf"),
                totalPaginas = json.getInt("totalPaginas"),
                partes = (0 until partes.length()).map { i ->
                    val parte = partes.getJSONObject(i)
                    val paginas = parte.getJSONArray("paginas")
                    MapaTexto.Parte(
                        parte.getString("archivo"),
                        (0 until paginas.length()).map { j ->
                            paginas.getJSONArray(j).let { it.getInt(0) to it.getDouble(1) }
                        },
                    )
                },
            )
        }
    }
}

/** El PDF no tiene texto que sacar (son imágenes escaneadas). */
class PdfSinTexto : Exception()

/**
 * Modo texto para PDF: crea (una sola vez) el EPUB con el texto del PDF, lo guarda en la
 * memoria privada de la app y recuerda, para cada libro, si se estaba leyendo en modo texto.
 */
object ModoTexto {

    /** Si cambia la forma de limpiar el texto, se sube y los EPUB se vuelven a crear. */
    private const val VERSION = 25

    private fun carpeta(contexto: Context, idLibro: String) = File(contexto.filesDir, "modo-texto/$idLibro")
    /** Con las notas al pie desactivadas se guarda otra versión del texto («-sin-notas»). */
    private fun sufijo(contexto: Context, idLibro: String) = if (notasActivas(contexto, idLibro)) "" else "-sin-notas"
    fun archivoEpub(contexto: Context, idLibro: String) =
        File(carpeta(contexto, idLibro), "libro-v$VERSION${sufijo(contexto, idLibro)}.epub")
    private fun archivoMapa(contexto: Context, idLibro: String) =
        File(carpeta(contexto, idLibro), "mapa-v$VERSION${sufijo(contexto, idLibro)}.json")

    fun estaPreparado(contexto: Context, idLibro: String): Boolean =
        archivoEpub(contexto, idLibro).exists() && archivoMapa(contexto, idLibro).exists()

    fun cargarMapa(contexto: Context, idLibro: String): MapaTexto? = runCatching {
        MapaTexto.fromJSON(JSONObject(archivoMapa(contexto, idLibro).readText()))
    }.getOrNull()

    /**
     * Saca el texto del PDF, lo limpia y crea el EPUB. Puede tardar en libros largos;
     * [progreso] recibe la página por la que va y el total.
     */
    suspend fun preparar(
        contexto: Context,
        libro: Libro,
        hrefPdf: String,
        progreso: (pagina: Int, total: Int) -> Unit,
    ): MapaTexto = withContext(Dispatchers.IO) {
        val uri = Uri.parse(libro.uriArchivo ?: error("Libro sin archivo"))
        PDFBoxResourceLoader.init(contexto.applicationContext)
        val (paginas, capitulos) = contexto.contentResolver.openInputStream(uri)!!.use { entrada ->
            PDDocument.load(entrada, MemoryUsageSetting.setupTempFileOnly()).use { documento ->
                val total = documento.numberOfPages
                val extractor = ExtractorPdf()
                val paginas = (1..total).map { numero ->
                    coroutineContext.ensureActive()
                    progreso(numero, total)
                    runCatching { extractor.leerPagina(documento, numero) }
                        .getOrDefault(PaginaPdf(numero, 0f, emptyList()))
                }
                paginas to capitulosDe(documento)
            }
        }
        // Si casi no hay letras, es un PDF escaneado.
        val letras = paginas.sumOf { p -> p.lineas.sumOf { it.texto.length } }
        if (letras < paginas.size * 20) throw PdfSinTexto()

        // Solo en la versión de pruebas: las líneas tal como salen del PDF, para revisar la limpieza.
        if (contexto.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            runCatching {
                carpeta(contexto, libro.id).mkdirs()
                File(carpeta(contexto, libro.id), "lineas-v$VERSION.txt").bufferedWriter().use { salida ->
                    VolcadoLineas.escribir(paginas, salida)
                }
            }
        }

        val limpias = LimpiezaTexto.limpiar(paginas, detectarNotas = notasActivas(contexto, libro.id))
        val idioma = libro.idioma?.takeIf { it.isNotBlank() } ?: Locale.getDefault().language
        val generador = GeneradorEpub(
            titulo = libro.titulo,
            autor = libro.autor,
            idioma = idioma,
            identificador = libro.id,
            tituloBloque = { desde, hasta -> contexto.getString(R.string.modo_texto_bloque, desde, hasta) },
        )
        val temporal = File(carpeta(contexto, libro.id), "creando.epub")
        val mapa = generador.generar(limpias, capitulos, temporal, hrefPdf)
        coroutineContext.ensureActive()
        // Solo cuando todo ha ido bien se ponen en su sitio (nunca queda un EPUB a medias).
        archivoMapa(contexto, libro.id).writeText(mapa.toJSON().toString())
        val epub = archivoEpub(contexto, libro.id)
        epub.delete()
        temporal.renameTo(epub)
        // Se borran los de versiones anteriores, que ya no sirven.
        carpeta(contexto, libro.id).listFiles()
            ?.filterNot { it.name.contains("-v$VERSION.") || it.name.contains("-v$VERSION-") }
            ?.forEach { it.delete() }
        mapa
    }

    /** Los capítulos del índice del PDF (si lo tiene), con la página donde empiezan. */
    private fun capitulosDe(documento: PDDocument): List<CapituloPdf> = runCatching {
        val indice = documento.documentCatalog.documentOutline ?: return emptyList()
        var nivel = indice.children().toList()
        // Si solo hay una entrada (por ejemplo, el título del libro), se usan sus hijos.
        if (nivel.size == 1) nivel = nivel.first().children().toList()
        nivel.mapNotNull { entrada ->
            val pagina = runCatching { entrada.findDestinationPage(documento) }.getOrNull() ?: return@mapNotNull null
            val numero = documento.pages.indexOf(pagina) + 1
            if (numero <= 0) null else CapituloPdf(entrada.title?.trim().orEmpty(), numero)
        }
    }.getOrDefault(emptyList())

    // ---- Qué modo usa cada libro y su posición exacta en el modo texto ----

    private fun prefs(contexto: Context) = contexto.getSharedPreferences("modo_texto", Context.MODE_PRIVATE)

    fun estaActivo(contexto: Context, idLibro: String): Boolean =
        prefs(contexto).getBoolean("activo_$idLibro", false)

    /**
     * Si en este libro se separan las notas al pie (por defecto, sí). Se puede desactivar
     * en el panel Aa si en algún PDF la detección confunde notas y texto.
     */
    fun notasActivas(contexto: Context, idLibro: String): Boolean =
        prefs(contexto).getBoolean("notas_$idLibro", true)

    fun activarNotas(contexto: Context, idLibro: String, activas: Boolean) {
        prefs(contexto).edit { putBoolean("notas_$idLibro", activas) }
    }

    fun activar(contexto: Context, idLibro: String, activo: Boolean) {
        prefs(contexto).edit { putBoolean("activo_$idLibro", activo) }
    }

    /** La posición exacta en el modo texto (en la base de datos se guarda la página del PDF). */
    fun posicionGuardada(contexto: Context, idLibro: String): Locator? =
        prefs(contexto).getString("posicion_$idLibro", null)?.let {
            runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
        }

    fun guardarPosicion(contexto: Context, idLibro: String, posicion: Locator) {
        prefs(contexto).edit { putString("posicion_$idLibro", posicion.toJSON().toString()) }
    }
}
