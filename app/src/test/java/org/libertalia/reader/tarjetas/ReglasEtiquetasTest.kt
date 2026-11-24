package org.libertalia.reader.tarjetas

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pruebas de las sugerencias y la limpieza de etiquetas. */
class ReglasEtiquetasTest {

    // Como las da TodasLasEtiquetas: de la más usada a la menos.
    private val existentes = listOf(
        "aventuras" to 7, "honor" to 6, "filosofía" to 3, "historia" to 2, "biofilia" to 1, "filología" to 1,
    )

    @Test
    fun alEscribirFil_sugiereFilosofia_primeroLasQueEmpiezanIgual() {
        val r = ReglasEtiquetas.sugerencias(existentes, "fil", emptySet()).map { it.first }
        assertEquals(listOf("filosofía", "filología", "biofilia"), r)
    }

    @Test
    fun sinTildesNiMayusculas() {
        val r = ReglasEtiquetas.sugerencias(existentes, "FILOSOFIA", emptySet()).map { it.first }
        assertEquals(listOf("filosofía"), r)
    }

    @Test
    fun sinEscribirNada_lasMasUsadas_sinLasQueYaTiene() {
        val r = ReglasEtiquetas.sugerencias(existentes, "", setOf("honor"), maximo = 3).map { it.first }
        assertEquals(listOf("aventuras", "filosofía", "historia"), r)
    }

    @Test
    fun laAlmohadillaNoCuenta() {
        assertEquals("filosofía", ReglasEtiquetas.limpiar("#Filosofía"))
        assertEquals("historia del arte", ReglasEtiquetas.limpiar("  historia,  del arte "))
        val r = ReglasEtiquetas.sugerencias(existentes, "#hon", emptySet()).map { it.first }
        assertEquals(listOf("honor"), r)
    }
}
