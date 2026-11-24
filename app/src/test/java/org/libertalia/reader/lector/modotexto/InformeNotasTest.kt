package org.libertalia.reader.lector.modotexto

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Revisa las notas al pie que encuentra el modo texto en muchos PDF a la vez.
 *
 * Usa los volcados de líneas que crea la herramienta de pruebas VolcarPdfActivity
 * (se copian del móvil a una carpeta y se indica con la variable VOLCADOS).
 * Escribe un informe en app/build/informe-notas.txt: llamadas sin nota, notas sin
 * llamada, notas sin número y párrafos que parecen notas que se han escapado.
 *
 * Uso: VOLCADOS=<carpeta> ./gradlew :app:testDebugUnitTest --tests '*InformeNotas*'
 */
class InformeNotasTest {

    private val volada = Regex("${SUP_INICIO}([0-9]{1,3})${SUP_FIN}")
    private val numeroInicial = Regex("^\\s*${SUP_INICIO}?\\(?([0-9]{1,3})(?![0-9])")

    @Test
    fun informe() {
        val carpeta = System.getenv("VOLCADOS")?.let(::File)
        assumeTrue("Sin VOLCADOS: no hay nada que revisar", carpeta?.isDirectory == true)
        val informe = StringBuilder()
        val resumen = StringBuilder("libro | llamadas | notas | sin nota | sin llamada | sin número | escapadas\n")
        carpeta!!.listFiles { f -> f.name.endsWith(".txt") }!!.sortedBy { it.name }.forEach { archivo ->
            val paginas = LimpiezaTexto.limpiar(VolcadoLineas.leer(archivo.readText()))
            val llamadas = mutableMapOf<Int, Int>() // número → página
            val notas = mutableListOf<Triple<Int, Int?, String>>() // página, número, texto
            val escapadas = mutableListOf<String>()
            paginas.forEach { p ->
                p.bloques.forEach { b ->
                    when (b) {
                        is Bloque.Nota -> notas += Triple(p.numero, numeroInicial.find(b.texto)?.groupValues?.get(1)?.toInt(), b.texto)
                        is Bloque.Parrafo -> {
                            volada.findAll(b.texto).forEach { llamadas.putIfAbsent(it.groupValues[1].toInt(), p.numero) }
                            if (b.texto.trimStart().startsWith(SUP_INICIO)) escapadas += "p.${p.numero}: ${b.texto.limpio().take(110)}"
                        }
                        is Bloque.Titulo -> volada.findAll(b.texto).forEach { llamadas.putIfAbsent(it.groupValues[1].toInt(), p.numero) }
                    }
                }
            }
            val numeradas = notas.mapNotNull { it.second }.toSet()
            val sinNota = llamadas.keys.filter { it !in numeradas }.sorted()
            val sinLlamada = numeradas.filter { it !in llamadas.keys }.sorted()
            val sinNumero = notas.filter { it.second == null }
            resumen.append("${archivo.name.take(45)} | ${llamadas.size} | ${notas.size} | ${sinNota.size} | ${sinLlamada.size} | ${sinNumero.size} | ${escapadas.size}\n")

            informe.append("\n===== ${archivo.name} (${paginas.size} págs.)\n")
            if (sinNota.isNotEmpty()) informe.append("Llamadas sin nota: ${sinNota.joinToString { "$it (p.${llamadas[it]})" }}\n")
            if (sinLlamada.isNotEmpty()) informe.append("Notas sin llamada: ${sinLlamada.joinToString()}\n")
            sinNumero.forEach { informe.append("Nota sin número p.${it.first}: ${it.third.limpio().take(110)}\n") }
            escapadas.forEach { informe.append("¿Nota escapada? $it\n") }
            informe.append("Notas:\n")
            notas.forEach { (pag, num, texto) -> informe.append("  p.$pag [${num ?: "-"}] ${texto.limpio().take(100)}\n") }
        }
        val salida = File("build/informe-notas.txt")
        salida.writeText(resumen.toString() + informe)
        println(resumen)
    }

    /**
     * Escribe cada libro tal como queda tras la limpieza (títulos, párrafos y notas) en
     * app/build/texto-limpio/, para leerlo entero y buscar fallos.
     * Uso: VOLCADOS=<carpeta> ./gradlew :app:testDebugUnitTest --tests '*InformeNotasTest.textoLimpio'
     */
    @Test
    fun textoLimpio() {
        val carpeta = System.getenv("VOLCADOS")?.let(::File)
        assumeTrue("Sin VOLCADOS: no hay nada que revisar", carpeta?.isDirectory == true)
        val salida = File("build/texto-limpio").apply { mkdirs() }
        carpeta!!.listFiles { f -> f.name.endsWith(".txt") }!!.forEach { archivo ->
            val texto = StringBuilder()
            LimpiezaTexto.limpiar(VolcadoLineas.leer(archivo.readText())).forEach { p ->
                texto.append("\n======== pág. ${p.numero}\n")
                p.bloques.forEach { b ->
                    when (b) {
                        is Bloque.Titulo -> texto.append("# ${b.texto.limpio()}\n")
                        is Bloque.Parrafo -> texto.append(if (b.continua) "(sigue) " else "").append(b.texto.limpio()).append("\n")
                        is Bloque.Nota -> texto.append("  [NOTA] ${b.texto.limpio()}\n")
                    }
                }
            }
            File(salida, archivo.name).writeText(texto.toString())
        }
    }

    private fun String.limpio() = replace(SUP_INICIO, '^').replace(SUP_FIN.toString(), "")
}
