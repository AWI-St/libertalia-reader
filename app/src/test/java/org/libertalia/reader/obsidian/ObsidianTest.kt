package org.libertalia.reader.obsidian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lectura de los archivos .md de la bóveda: qué nota y qué etiquetas tiene cada tarjeta. */
class ObsidianTest {

    private val archivo = """
        ---
        libro: "Bases"
        libertalia: libro-1
        ---

        # Bases

        > [!quote] 1. Capítulo II
        > la frase con **el subrayado**

        %% libertalia:t1 %%

        > [!note] Mi nota
        > Primera línea
        > segunda línea

        #filosofía #historia_antigua

        ---

        > [!quote] 2. Capítulo II
        > otra **frase**

        %% libertalia:t2 %%

        Esto lo escribí en Obsidian sin recuadro #nueva

        ---

        > [!quote] 3. Capítulo III
        > **sin nada**

        %% libertalia:t3 %%

        ---
    """.trimIndent()

    @Test
    fun encuentraCadaTarjeta() {
        assertEquals(listOf("t1", "t2", "t3"), Obsidian.bloques(archivo).map { it.first })
    }

    @Test
    fun notaDelRecuadroYEtiquetas() {
        val bloque = Obsidian.leerBloque(Obsidian.bloques(archivo)[0].second)
        assertEquals("Primera línea\nsegunda línea", bloque.nota)
        assertEquals(listOf("filosofía", "historia_antigua"), bloque.etiquetas)
    }

    @Test
    fun notaEscritaSinRecuadro() {
        val bloque = Obsidian.leerBloque(Obsidian.bloques(archivo)[1].second)
        assertEquals("Esto lo escribí en Obsidian sin recuadro", bloque.nota)
        assertEquals(listOf("nueva"), bloque.etiquetas)
    }

    @Test
    fun tarjetaSinNotaNiEtiquetas() {
        val bloque = Obsidian.leerBloque(Obsidian.bloques(archivo)[2].second)
        assertNull(bloque.nota)
        assertEquals(emptyList<String>(), bloque.etiquetas)
    }

    @Test
    fun etiquetasSinEspacios() {
        assertEquals("historia_antigua", Obsidian.etiquetaObsidian("historia  antigua"))
    }
}
