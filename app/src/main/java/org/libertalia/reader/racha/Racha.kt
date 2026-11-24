package org.libertalia.reader.racha

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.widgets.Widgets
import java.time.LocalDate
import java.time.LocalDateTime

/** Cómo está la racha (y, con ella, la cara del pirata). */
enum class EstadoRacha {
    /** Hoy ya se ha leído lo suficiente. */
    CONTENTO,

    /** Aún no se ha leído hoy, pero hay tiempo. */
    TRANQUILO,

    /** Son más de las 20:00, aún no se ha leído hoy y hay una racha que perder. */
    ASUSTADO,

    /** Ayer no se leyó: la racha se ha roto y empieza de cero. */
    TRISTE,
}

/** [dias]: días seguidos leyendo (contando hoy si ya se ha leído). */
data class Racha(val dias: Int, val estado: EstadoRacha)

/**
 * La racha de lectura: días seguidos con al menos 5 minutos de lectura (libros o cómics).
 */
object CalculoRacha {

    /** Lo que hay que leer un día para que cuente: 5 minutos. */
    const val MINIMO_SEGUNDOS = 5 * 60L

    /** A partir de esta hora, si aún no se ha leído, la racha está en peligro. */
    const val HORA_PELIGRO = 20

    fun calcular(segundosPorDia: Map<LocalDate, Long>, ahora: LocalDateTime): Racha {
        fun leido(dia: LocalDate) = (segundosPorDia[dia] ?: 0L) >= MINIMO_SEGUNDOS
        fun seguidosDesde(dia: LocalDate): Int {
            var n = 0
            var d = dia
            while (leido(d)) {
                n++
                d = d.minusDays(1)
            }
            return n
        }
        val hoy = ahora.toLocalDate()
        if (leido(hoy)) return Racha(seguidosDesde(hoy), EstadoRacha.CONTENTO)
        val hastaAyer = seguidosDesde(hoy.minusDays(1))
        return when {
            hastaAyer > 0 && ahora.hour >= HORA_PELIGRO -> Racha(hastaAyer, EstadoRacha.ASUSTADO)
            hastaAyer > 0 -> Racha(hastaAyer, EstadoRacha.TRANQUILO)
            // Ayer no se leyó: si alguna vez hubo racha, se ha roto.
            segundosPorDia.any { (dia, s) -> dia < hoy && s >= MINIMO_SEGUNDOS } -> Racha(0, EstadoRacha.TRISTE)
            else -> Racha(0, EstadoRacha.TRANQUILO)
        }
    }

    /** La racha de ahora mismo, con los datos de la base de datos. */
    suspend fun actual(app: LibertaliaApp): Racha {
        val hoy = LocalDate.now()
        // Se miran los últimos 1000 días (casi tres años de racha seguida).
        val dias = app.baseDatos.diasLectura().desde(hoy.minusDays(1_000).toString())
            .associate { LocalDate.parse(it.fecha) to it.segundos }
        return calcular(dias, LocalDateTime.now())
    }
}

/**
 * Mide el tiempo que se pasa leyendo. Solo cuenta el tiempo activo: cada vez que se pasa
 * de página (o se abre el libro) se apunta; si pasan más de 3 minutos sin pasar página,
 * ese hueco cuenta como 3 minutos como mucho (el móvil puede haberse quedado quieto).
 *
 * [comic]: el tiempo va a los cómics (si no, a los libros). Para la velocidad de lectura, el
 * lector de libros dice cuántas palabras tenía la página que se acaba de pasar.
 */
class MedidorLectura(private val app: LibertaliaApp, private val comic: Boolean = false) {

    /** Lo que aún no se ha guardado de un día: tiempo, palabras y el tiempo que llevaron. */
    private class Pendiente(var ms: Long = 0, var palabras: Long = 0, var msPalabras: Long = 0)

    private var marca: Long? = null
    private val pendiente = mutableMapOf<LocalDate, Pendiente>()
    private var enMarcha = false

    /** Al ponerse delante el lector (o al empezar a sonar la lectura en voz alta). */
    fun empezar() {
        marca = System.currentTimeMillis()
        if (!enMarcha) {
            enMarcha = true
            activos.incrementAndGet()
        }
    }

    /**
     * Señal de que se está leyendo (se ha pasado de página). [palabras]: las de la página que
     * se acaba de leer, si se ha pasado justo a la siguiente (0 si se ha saltado a otro sitio).
     */
    fun actividad(palabras: Int = 0) {
        val ahora = System.currentTimeMillis()
        marca?.let { anadir(it, ahora, palabras) }
        marca = ahora
        if (pendiente.values.sumOf { it.ms } >= GUARDAR_CADA) guardar()
    }

    /** Al salir del lector (o pasar a otra app). */
    fun parar() {
        marca?.let { anadir(it, System.currentTimeMillis()) }
        marca = null
        if (enMarcha) {
            enMarcha = false
            activos.decrementAndGet()
        }
        guardar()
    }

    private fun anadir(desde: Long, hasta: Long, palabras: Int = 0) {
        val bruto = hasta - desde
        val tiempo = bruto.coerceIn(0, HUECO_MAXIMO)
        if (tiempo == 0L) return
        val dia = pendiente.getOrPut(LocalDate.now()) { Pendiente() }
        dia.ms += tiempo
        // Para la velocidad solo cuentan las páginas leídas de verdad: ni las que se pasan
        // de corrido (más de 1500 palabras por minuto) ni las que se dejan mucho rato.
        if (palabras > 0 && bruto < HUECO_MAXIMO && palabras * 60_000.0 / bruto <= MAXIMO_PALABRAS_MINUTO) {
            dia.palabras += palabras
            dia.msPalabras += tiempo
        }
    }

    private fun guardar() {
        if (pendiente.isEmpty()) return
        val copia = pendiente.toMap()
        pendiente.clear()
        app.alcanceApp.launch {
            copia.forEach { (dia, p) ->
                val segundos = p.ms / 1000
                app.baseDatos.diasLectura().sumar(
                    fecha = dia.toString(),
                    segundos = segundos,
                    libros = if (comic) 0 else segundos,
                    comics = if (comic) segundos else 0,
                    palabras = p.palabras,
                    segundosPalabras = p.msPalabras / 1000,
                )
            }
            // Un momento después (para que también se haya guardado la página), los widgets.
            delay(1_500)
            Widgets.actualizar(app)
        }
    }

    companion object {
        /** Cuántos medidores están en marcha (el del lector y el de la lectura en voz alta). */
        private val activos = java.util.concurrent.atomic.AtomicInteger(0)

        /** Si se está leyendo o escuchando ahora mismo (la copia automática nunca se hace entonces). */
        val leyendo: Boolean get() = activos.get() > 0

        private const val HUECO_MAXIMO = 3 * 60_000L
        private const val GUARDAR_CADA = 30_000L
        private const val MAXIMO_PALABRAS_MINUTO = 1_500.0
    }
}
