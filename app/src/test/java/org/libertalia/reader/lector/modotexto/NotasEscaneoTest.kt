package org.libertalia.reader.lector.modotexto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Notas de un libro escaneado (un ensayo de los años 50): las llamadas son
 * números normales («el Informe”. 1 Muy tarde»), los números de los apartados se leyeron como
 * volados («¹ — Precocidad») y el reconocimiento de texto confunde cifras («8» por «3»).
 */
class NotasEscaneoTest {

    private fun cuerpo(texto: String, y: Float, x: Float = 38f) = LineaPdf(texto, 10f, x, 296f, y)
    private fun nota(texto: String, y: Float, x: Float = 38f, fin: Float = 296f) = LineaPdf(texto, 7f, x, fin, y)

    /** Una página con un título de apartado «volado», texto con llamadas normales y dos notas. */
    private fun pagina(n: Int): PaginaPdf {
        val a = n * 2 - 1
        val b = n * 2
        // La segunda nota la leyó mal el escaneo: número equivocado (como «8» por «3»).
        val malLeido = if (n == 2) 8 else b
        return PaginaPdf(n, 595f, listOf(
            LineaPdf("${SUP_INICIO}$n$SUP_FIN — Apartado número $n del libro", 14f, 60f, 250f, 100f),
            cuerpo("Es quizás demasiado pronto para escribir un estudio", 130f, 50f),
            cuerpo("sobre la gran encuesta de la que todos hablan, el", 143f),
            cuerpo("“el Informe”. $a Muy tarde, dado que la prensa ha", 156f),
            cuerpo("hablado mucho de esta obra y ha repetido sin", 169f),
            cuerpo("cuidado algunos datos (no los mejores).$b Y así", 182f),
            cuerpo("seguimos con el texto del libro hasta abajo.", 195f),
            nota("$a El primer volumen se publicó en el extranjero, y en los", 230f, 50f),
            nota("años siguientes llegaron sus traducciones.", 239f, fin = 200f),
            nota("$malLeido Los autores anuncian nuevos volúmenes sobre otros", 248f, 50f),
            nota("temas, que aún no han aparecido.", 257f, fin = 220f),
        ))
    }

    @Test
    fun notasConLlamadasNormales() {
        val paginas = LimpiezaTexto.limpiar((1..4).map(::pagina))
        paginas.forEach { p ->
            val notas = p.bloques.filterIsInstance<Bloque.Nota>()
            assertEquals("Notas de la página ${p.numero}", 2, notas.size)
            val texto = p.bloques.filterIsInstance<Bloque.Parrafo>().joinToString(" ") { it.texto }
            // La llamada de la primera nota pasa a verse volada.
            val a = p.numero * 2 - 1
            assertTrue("Llamada $a en la página ${p.numero}: $texto", texto.contains("$SUP_INICIO$a$SUP_FIN"))
            // Y el texto de las notas no se queda mezclado con el del libro.
            assertTrue(!texto.contains("primer volumen"))
        }
    }
}
