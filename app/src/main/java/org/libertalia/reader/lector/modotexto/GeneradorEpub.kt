package org.libertalia.reader.lector.modotexto

import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Un capítulo del PDF (de su índice): título y página donde empieza. */
data class CapituloPdf(val titulo: String, val pagina: Int)

/**
 * Crea un EPUB sencillo con el texto limpio del PDF.
 * - Se divide en partes por los capítulos del índice del PDF o, si no tiene, cada 10 páginas.
 * - Al principio de cada página del PDF se pone una marca invisible (id «pag-N»), y se apunta
 *   en el [MapaTexto] en qué parte y a qué altura de ella empieza cada página.
 * - Las notas al pie van en un recuadro con otro fondo, después del párrafo en el que están.
 */
class GeneradorEpub(
    private val titulo: String,
    private val autor: String,
    private val idioma: String,
    private val identificador: String,
    /** Título para las partes sin capítulo: «Páginas 1–10». */
    private val tituloBloque: (desde: Int, hasta: Int) -> String,
) {

    private data class Parte(val archivo: String, val titulo: String?, val paginas: List<PaginaLimpia>)

    fun generar(paginas: List<PaginaLimpia>, capitulos: List<CapituloPdf>, destino: File, hrefPdf: String): MapaTexto {
        val partes = dividir(paginas, capitulos)
        val mapa = mutableListOf<MapaTexto.Parte>()
        destino.parentFile?.mkdirs()
        ZipOutputStream(destino.outputStream().buffered()).use { zip ->
            // El primer archivo de un EPUB es «mimetype», sin comprimir.
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                compressedSize = mimetype.size.toLong()
                crc = CRC32().apply { update(mimetype) }.value
            })
            zip.write(mimetype)
            zip.closeEntry()

            escribir(zip, "META-INF/container.xml", CONTAINER)
            escribir(zip, "OEBPS/estilo.css", ESTILO)
            partes.forEach { parte ->
                val (html, inicios) = htmlDeParte(parte)
                escribir(zip, "OEBPS/${parte.archivo}", html)
                mapa += MapaTexto.Parte(parte.archivo, inicios)
            }
            escribir(zip, "OEBPS/nav.xhtml", nav(partes))
            escribir(zip, "OEBPS/content.opf", opf(partes))
        }
        return MapaTexto(hrefPdf, paginas.size, mapa)
    }

    /** Parte el libro por capítulos (o cada 10 páginas); ninguna parte pasa de 25 páginas. */
    private fun dividir(paginas: List<PaginaLimpia>, capitulos: List<CapituloPdf>): List<Parte> {
        val inicios = capitulos.filter { it.pagina in 1..paginas.size }
            .distinctBy { it.pagina }.sortedBy { it.pagina }
        val cortes = mutableListOf<Pair<Int, String?>>()
        if (inicios.size >= 2) {
            if (inicios.first().pagina > 1) cortes += 1 to null
            inicios.forEach { cortes += it.pagina to it.titulo.ifBlank { null } }
        } else {
            // Cada 10 páginas, pero sin cortar un párrafo que sigue en la página siguiente
            // (se alarga el bloque hasta 5 páginas más buscando dónde acaba un párrafo).
            fun sigueParrafo(pagina: Int) =
                (paginas[pagina - 1].bloques.firstOrNull { it !is Bloque.Nota } as? Bloque.Parrafo)?.continua == true
            var desde = 1
            while (desde <= paginas.size) {
                cortes += desde to null
                var siguiente = desde + 10
                while (siguiente <= paginas.size && siguiente < desde + 15 && sigueParrafo(siguiente)) siguiente++
                desde = siguiente
            }
        }
        val partes = mutableListOf<Parte>()
        cortes.forEachIndexed { i, (desde, tituloCapitulo) ->
            val hasta = (cortes.getOrNull(i + 1)?.first ?: (paginas.size + 1)) - 1
            (desde..hasta).chunked(25).forEachIndexed { trozo, rango ->
                val titulo = when {
                    trozo > 0 -> null
                    tituloCapitulo != null -> tituloCapitulo
                    else -> tituloBloque(rango.first(), rango.last())
                }
                partes += Parte("parte-${partes.size + 1}.xhtml", titulo, rango.map { paginas[it - 1] })
            }
        }
        return partes
    }

    /** Una nota al pie esperando a que se cierre el párrafo que la llama. */
    private data class NotaPendiente(val numero: String?, val pagina: Int, val html: String)

    /** El HTML de una parte, y dónde empieza (de 0 a 1) cada página del PDF dentro de ella. */
    private fun htmlDeParte(parte: Parte): Pair<String, List<Pair<Int, Double>>> {
        val cuerpo = StringBuilder()
        val marcas = mutableListOf<Pair<Int, Int>>() // página → caracteres antes de ella
        var parrafoAbierto: StringBuilder? = null
        val notasPendientes = mutableListOf<NotaPendiente>()
        var paginaActual = 0

        fun escribirNotas(notas: List<NotaPendiente>) {
            if (notas.isEmpty()) return
            cuerpo.append("<div class=\"notas\">\n")
            notas.forEach { cuerpo.append("<p>").append(it.html).append("</p>\n") }
            cuerpo.append("</div>\n")
            notasPendientes.removeAll(notas)
        }

        /**
         * Cierra el párrafo y pone detrás sus notas: las que él llama (por su número volado)
         * y las de páginas anteriores que ningún párrafo ha llamado.
         */
        fun cerrarParrafo() {
            val abierto = parrafoAbierto ?: return
            cuerpo.append("<p>").append(abierto).append("</p>\n")
            parrafoAbierto = null
            val llamadas = LLAMADA.findAll(abierto).map { it.groupValues[1] }.toSet()
            escribirNotas(notasPendientes.filter { it.numero in llamadas || it.pagina < paginaActual })
        }

        parte.paginas.forEachIndexed { indice, pagina ->
            paginaActual = pagina.numero
            pagina.bloques.filterIsInstance<Bloque.Nota>().forEach { nota ->
                notasPendientes += NotaPendiente(NUMERO_NOTA.find(nota.texto)?.groupValues?.get(1), pagina.numero,
                    // Un espacio entre el número volado y el texto («⁵⁹La» → «⁵⁹ La»).
                    html(nota.texto).replace(Regex("^(\\s*<sup>[^<]*</sup>)(?=[^\\s<])"), "$1 "))
            }
            val ancla = "<span id=\"pag-${pagina.numero}\" class=\"pagina\"></span>"
            val primero = pagina.bloques.firstOrNull { it !is Bloque.Nota }
            val sigue = indice > 0 && primero is Bloque.Parrafo && primero.continua && parrafoAbierto != null
            if (!sigue) {
                cerrarParrafo()
                marcas += pagina.numero to cuerpo.length
                cuerpo.append(ancla).append('\n')
            }
            pagina.bloques.forEach { bloque ->
                when (bloque) {
                    is Bloque.Titulo -> {
                        cerrarParrafo()
                        cuerpo.append("<h2>").append(html(bloque.texto)).append("</h2>\n")
                    }
                    is Bloque.Parrafo -> {
                        val abierto = parrafoAbierto
                        if (bloque === primero && sigue && abierto != null) {
                            // El párrafo sigue desde la página anterior: la marca va dentro.
                            // Palabra partida con guion entre dos páginas: se junta.
                            val partida = LimpiezaTexto.acabaPartida(abierto.toString(), bloque.texto)
                            if (partida) abierto.setLength(abierto.length - 1) else abierto.append(' ')
                            marcas += pagina.numero to cuerpo.length + abierto.length
                            abierto.append(ancla).append(html(bloque.texto))
                        } else {
                            cerrarParrafo()
                            parrafoAbierto = StringBuilder(html(bloque.texto))
                        }
                    }
                    is Bloque.Nota -> Unit // ya están en notasPendientes
                }
            }
        }
        cerrarParrafo()
        escribirNotas(notasPendientes.toList())

        val total = cuerpo.length.coerceAtLeast(1).toDouble()
        val inicios = marcas.map { (pagina, antes) -> pagina to (antes / total).coerceIn(0.0, 1.0) }
        val documento = """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="$idioma" xml:lang="$idioma">
            |<head>
            |<meta charset="UTF-8"/>
            |<title>${html(parte.titulo ?: titulo)}</title>
            |<link rel="stylesheet" type="text/css" href="estilo.css"/>
            |</head>
            |<body>
            |$cuerpo</body>
            |</html>
        """.trimMargin()
        return documento to inicios
    }

    private fun nav(partes: List<Parte>): String {
        val entradas = partes.filter { it.titulo != null }.joinToString("\n") {
            "<li><a href=\"${it.archivo}\">${html(it.titulo!!)}</a></li>"
        }
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="$idioma" xml:lang="$idioma">
            |<head><meta charset="UTF-8"/><title>${html(titulo)}</title></head>
            |<body>
            |<nav epub:type="toc" id="toc"><ol>
            |$entradas
            |</ol></nav>
            |</body>
            |</html>
        """.trimMargin()
    }

    private fun opf(partes: List<Parte>): String {
        val manifiesto = partes.joinToString("\n") {
            "<item id=\"${it.archivo.substringBefore('.')}\" href=\"${it.archivo}\" media-type=\"application/xhtml+xml\"/>"
        }
        val lomo = partes.joinToString("\n") { "<itemref idref=\"${it.archivo.substringBefore('.')}\"/>" }
        val creador = if (autor.isNotBlank()) "<dc:creator>${html(autor)}</dc:creator>" else ""
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
            |<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            |<dc:identifier id="id">urn:uuid:$identificador</dc:identifier>
            |<dc:title>${html(titulo)}</dc:title>
            |$creador
            |<dc:language>$idioma</dc:language>
            |<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
            |</metadata>
            |<manifest>
            |<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            |<item id="estilo" href="estilo.css" media-type="text/css"/>
            |$manifiesto
            |</manifest>
            |<spine>
            |$lomo
            |</spine>
            |</package>
        """.trimMargin()
    }

    private fun escribir(zip: ZipOutputStream, ruta: String, contenido: String) {
        zip.putNextEntry(ZipEntry(ruta))
        zip.write(contenido.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /** Escapa el texto para HTML y convierte las llamadas a nota en superíndices. */
    private fun html(texto: String): String =
        texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            .replace(SUP_INICIO.toString(), "<sup>").replace(SUP_FIN.toString(), "</sup>")

    private companion object {
        /** Llamada a nota dentro de un párrafo: <sup>12</sup>. */
        val LLAMADA = Regex("<sup>(\\d{1,3})</sup>")

        /** Número con el que empieza una nota al pie. */
        val NUMERO_NOTA = Regex("^\\s*${SUP_INICIO}?\\(?(\\d{1,3})")

        val CONTAINER = """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            |<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            |</container>
        """.trimMargin()

        /**
         * Estilo del texto. Las notas llevan un fondo marrón semitransparente, para que se
         * distingan en los tres temas (crema, sepia y oscuro).
         */
        val ESTILO = """
            |h1 { font-size: 1.4em; margin: 1em 0 0.8em; }
            |h2 { font-size: 1.2em; margin: 1em 0 0.6em; }
            |p { margin: 0; text-indent: 1.5em; }
            |sup { font-size: 0.7em; line-height: 0; vertical-align: super; }
            |:root[style] body div.notas, body div.notas {
            |  background-color: rgba(122, 74, 42, 0.13) !important;
            |  border-left: 3px solid rgba(122, 74, 42, 0.5) !important;
            |  border-radius: 4px;
            |  padding: 0.4em 0.7em !important;
            |  margin: 0.7em 0 !important;
            |}
            |:root[style] body div.notas p, body div.notas p {
            |  font-size: 0.85em !important;
            |  line-height: 1.35 !important;
            |  text-indent: 0 !important;
            |  text-align: left !important;
            |  margin: 0.15em 0 !important;
            |}
        """.trimMargin()
    }
}
