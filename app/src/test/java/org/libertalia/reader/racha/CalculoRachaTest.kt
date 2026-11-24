package org.libertalia.reader.racha

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Pruebas de la racha de lectura: los 4 estados del pirata y la cuenta de días. */
class CalculoRachaTest {

    private val hoy = LocalDate.of(2026, 11, 23)
    private fun aLas(hora: Int) = hoy.atTime(hora, 0)
    private fun dias(vararg atras: Int, segundos: Long = 600) =
        atras.associate { hoy.minusDays(it.toLong()) to segundos }

    @Test
    fun hoyYaLeido_contento_contandoHoy() {
        val r = CalculoRacha.calcular(dias(0, 1, 2), aLas(10))
        assertEquals(Racha(3, EstadoRacha.CONTENTO), r)
    }

    @Test
    fun hoyAunNo_porLaManana_tranquilo() {
        val r = CalculoRacha.calcular(dias(1, 2, 3, 4, 5, 6), aLas(16))
        assertEquals(Racha(6, EstadoRacha.TRANQUILO), r)
    }

    @Test
    fun hoyAunNo_desdeLas20_asustado() {
        val r = CalculoRacha.calcular(dias(1, 2), aLas(20))
        assertEquals(Racha(2, EstadoRacha.ASUSTADO), r)
    }

    @Test
    fun ayerNoSeLeyo_rachaRota_triste() {
        val r = CalculoRacha.calcular(dias(2, 3, 4), aLas(9))
        assertEquals(Racha(0, EstadoRacha.TRISTE), r)
    }

    @Test
    fun nuncaSeHaLeido_tranquilo_sinRacha() {
        assertEquals(Racha(0, EstadoRacha.TRANQUILO), CalculoRacha.calcular(emptyMap(), aLas(22)))
    }

    @Test
    fun menosDeCincoMinutos_noCuenta() {
        // Hoy solo 4 minutos: aún no cuenta; la racha viene de ayer.
        val datos = dias(1, 2) + (hoy to 240L)
        assertEquals(Racha(2, EstadoRacha.TRANQUILO), CalculoRacha.calcular(datos, aLas(12)))
    }
}
