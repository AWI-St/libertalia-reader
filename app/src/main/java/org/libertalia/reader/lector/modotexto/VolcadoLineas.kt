package org.libertalia.reader.lector.modotexto

import java.io.Writer
import java.util.Locale

/**
 * Herramienta de pruebas: guarda las líneas de un PDF tal como salen de [ExtractorPdf]
 * en un archivo de texto y las vuelve a leer. Así la limpieza (notas, cabeceras...) se
 * puede probar en el ordenador con muchos PDF a la vez, sin abrir cada uno en el móvil.
 */
object VolcadoLineas {

    private val CABECERA = Regex("^=== pág (\\d+) alto=([0-9.]+)$")
    private val LINEA = Regex("^y=([0-9.-]+) x=([0-9.-]+)~([0-9.-]+) t=([0-9.]+) \\| (.*)$")

    fun escribir(paginas: List<PaginaPdf>, salida: Writer) {
        paginas.forEach { p ->
            salida.write("=== pág ${p.numero} alto=${"%.2f".format(Locale.ROOT, p.alto)}\n")
            p.lineas.forEach { l ->
                salida.write(
                    "y=%.2f x=%.2f~%.2f t=%.2f | %s\n".format(Locale.ROOT, l.y, l.x, l.xFin, l.tamano, l.texto),
                )
            }
        }
    }

    fun leer(texto: String): List<PaginaPdf> {
        val paginas = mutableListOf<PaginaPdf>()
        var numero = 0
        var alto = 0f
        var lineas = mutableListOf<LineaPdf>()
        fun cerrar() {
            if (numero > 0) paginas += PaginaPdf(numero, alto, lineas)
        }
        texto.lineSequence().forEach { fila ->
            CABECERA.find(fila)?.let { m ->
                cerrar()
                numero = m.groupValues[1].toInt()
                alto = m.groupValues[2].toFloat()
                lineas = mutableListOf()
                return@forEach
            }
            LINEA.find(fila)?.let { m ->
                val (y, x, xFin, t, contenido) = m.destructured
                lineas += LineaPdf(contenido, t.toFloat(), x.toFloat(), xFin.toFloat(), y.toFloat())
            }
        }
        cerrar()
        return paginas
    }
}
