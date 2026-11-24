package org.libertalia.reader.lector.modotexto

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.text.Normalizer

/** Una línea de texto del PDF, con su tamaño de letra y su sitio en la página. */
data class LineaPdf(
    /** El texto. Las llamadas a nota voladas (superíndices) van entre [SUP_INICIO] y [SUP_FIN]. */
    val texto: String,
    /** Tamaño de letra en puntos. */
    val tamano: Float,
    /** Dónde empieza y dónde acaba la línea (horizontal). */
    val x: Float,
    val xFin: Float,
    /** Altura de la línea (su base), medida desde arriba de la página. */
    val y: Float,
)

/** Todas las líneas de una página del PDF. */
data class PaginaPdf(val numero: Int, val alto: Float, val lineas: List<LineaPdf>)

/** Marcas para los superíndices (llamadas a nota) dentro del texto de una línea. */
const val SUP_INICIO = '\u0001'
const val SUP_FIN = '\u0002'

/**
 * Saca el texto de un PDF página a página con PdfBox, guardando de cada línea su
 * tamaño de letra y su posición: con eso se adivinan después los títulos, los párrafos,
 * las cabeceras repetidas y las notas al pie.
 */
class ExtractorPdf : PDFTextStripper() {

    /** Un trozo de texto tal como lo da PdfBox; sin posiciones es un espacio entre palabras. */
    private class Trozo(val texto: String, val posiciones: List<TextPosition>)

    /** Una línea según PdfBox (antes de juntar las que en realidad son la misma). */
    private class Fragmento(val trozos: List<Trozo>) {
        val letras = trozos.flatMap { it.posiciones }
        val x = letras.minOf { it.xDirAdj }
        val xFin = letras.maxOf { it.xDirAdj + it.widthDirAdj }
        val tamanoMax = letras.maxOf { it.fontSizeInPt }
        /** Hueco vertical que ocupa (desde arriba de la letra más alta hasta la base más baja). */
        val arriba = letras.minOf { it.yDirAdj - it.fontSizeInPt }
        val abajo = letras.maxOf { it.yDirAdj }
        val tamanoMin = letras.minOf { it.fontSizeInPt }

        /** Si comparte con [otro] al menos la mitad de la altura del más bajo de los dos. */
        fun solapa(otro: Fragmento): Boolean {
            val comun = minOf(abajo, otro.abajo) - maxOf(arriba, otro.arriba)
            return comun >= minOf(tamanoMin, otro.tamanoMin) * 0.5f
        }
    }

    private val fragmentos = mutableListOf<Fragmento>()
    private val trozos = mutableListOf<Trozo>()
    private var alto = 0f
    private var resultado: PaginaPdf? = null
    private var numero = 0

    init {
        // Se respeta el orden en que el PDF guarda el texto (suele ser el orden de lectura,
        // también en páginas a dos columnas).
        sortByPosition = false
    }

    /** Lee una página (empezando en 1). */
    fun leerPagina(documento: PDDocument, pagina: Int): PaginaPdf {
        numero = pagina
        startPage = pagina
        endPage = pagina
        resultado = null
        getText(documento)
        return resultado ?: PaginaPdf(pagina, alto, emptyList())
    }

    override fun startPage(page: PDPage) {
        super.startPage(page)
        fragmentos.clear()
        trozos.clear()
        alto = page.mediaBox.height
    }

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        trozos += Trozo(text, textPositions)
    }

    override fun writeWordSeparator() {
        trozos += Trozo(" ", emptyList())
    }

    override fun writeLineSeparator() {
        cerrarFragmento()
    }

    override fun endPage(page: PDPage) {
        cerrarFragmento()
        resultado = PaginaPdf(numero, alto, juntar(fragmentos).mapNotNull { linea(it) })
        super.endPage(page)
    }

    private fun cerrarFragmento() {
        if (trozos.any { it.posiciones.isNotEmpty() }) fragmentos += Fragmento(trozos.toList())
        trozos.clear()
    }

    /**
     * PdfBox corta la línea cuando una letra sube o baja un poco, así que una llamada
     * a nota volada («texto¹ sigue») o el número pequeño del principio de una nota al pie
     * acaban en una línea aparte. Aquí se vuelven a juntar: si un trozo empieza a la
     * derecha del anterior y casi a su misma altura, es la misma línea.
     */
    private fun juntar(lista: List<Fragmento>): List<Fragmento> {
        val juntas = mutableListOf<Fragmento>()
        for (f in lista) {
            val anterior = juntas.lastOrNull()
            val mismaLinea = anterior != null && f.solapa(anterior) &&
                f.x >= anterior.xFin - maxOf(f.tamanoMax, anterior.tamanoMax) * 0.5f
            if (mismaLinea) {
                // Solo se pone un espacio si entre los dos trozos hay hueco.
                val hueco = f.x - anterior!!.xFin > minOf(f.tamanoMax, anterior.tamanoMax) * 0.2f
                val separador = if (hueco) listOf(Trozo(" ", emptyList())) else emptyList()
                juntas[juntas.lastIndex] = Fragmento(anterior.trozos + separador + f.trozos)
            } else {
                juntas += f
            }
        }
        return juntas
    }

    /** Convierte un fragmento en una línea, marcando las cifras voladas. */
    private fun linea(f: Fragmento): LineaPdf? {
        // Tamaño de la línea: el más habitual entre sus letras.
        val tamanos = f.letras.map { it.fontSizeInPt }.sorted()
        val tamano = tamanos[tamanos.size / 2]
        fun esVolada(p: TextPosition) =
            p.fontSizeInPt < tamano * 0.8f && p.unicode.isNotEmpty() && p.unicode.all { it.isDigit() || it == '*' }

        val texto = StringBuilder()
        var enVolada = false
        for (trozo in f.trozos) {
            if (trozo.posiciones.isEmpty() || trozo.posiciones.none(::esVolada)) {
                if (enVolada) texto.append(SUP_FIN)
                enVolada = false
                texto.append(normalizar(trozo.texto))
                continue
            }
            for (p in trozo.posiciones) {
                val volada = esVolada(p)
                if (volada && !enVolada) texto.append(SUP_INICIO)
                if (!volada && enVolada) texto.append(SUP_FIN)
                enVolada = volada
                texto.append(normalizar(p.unicode))
            }
        }
        if (enVolada) texto.append(SUP_FIN)

        val contenido = texto.toString().replace(Regex("\\s+"), " ").trim()
        if (contenido.isEmpty()) return null
        val normales = f.letras.filterNot(::esVolada).ifEmpty { f.letras }
        return LineaPdf(
            texto = contenido,
            tamano = tamano,
            x = f.x,
            xFin = f.xFin,
            y = normales.first().yDirAdj,
        )
    }

    /** Deshace ligaduras como «ﬁ» → «fi» y une tildes sueltas a su letra. */
    private fun normalizar(cadena: String): String =
        Normalizer.normalize(cadena, Normalizer.Form.NFKC)
            .replace(Regex("[\u0001\u0002]"), "")
}
