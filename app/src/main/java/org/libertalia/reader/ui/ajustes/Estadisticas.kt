package org.libertalia.reader.ui.ajustes

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.R
import org.libertalia.reader.datos.DiaLectura
import org.libertalia.reader.datos.EstadoLectura
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.TipoLibro
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Cuándo se terminó un libro; los terminados antes de la versión 8, la última vez que se leyeron. */
fun fechaTerminado(libro: Libro): Long? =
    if (libro.estado != EstadoLectura.TERMINADO) null
    else libro.fechaTerminado ?: libro.fechaUltimaLectura ?: libro.fechaModificacion

/** Qué periodo se ve en el gráfico de tiempo leído. */
enum class Periodo { SEMANA, MES, ANIO }

/** Una barra del gráfico: su etiqueta (puede ir vacía) y el tiempo leído. */
data class Barra(val etiqueta: String, val segundos: Long)

/** Lo terminado en un año: libros y cómics. */
data class AnioTerminados(val anio: Int, val libros: List<Libro>, val comics: List<Libro>)

class EstadisticasViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as LibertaliaApp

    private var dias by mutableStateOf<Map<LocalDate, DiaLectura>>(emptyMap())

    var cargado by mutableStateOf(false)
        private set

    var terminados by mutableStateOf<List<AnioTerminados>>(emptyList())
        private set

    /** Palabras por minuto en libros (null si aún no hay bastante lectura medida). */
    var palabrasPorMinuto by mutableStateOf<Int?>(null)
        private set

    var periodo by mutableStateOf(Periodo.SEMANA)

    init {
        viewModelScope.launch {
            dias = app.baseDatos.diasLectura().desde("").associateBy { LocalDate.parse(it.fecha) }
            val zona = ZoneId.systemDefault()
            terminados = app.baseDatos.libros().todos()
                .mapNotNull { libro -> fechaTerminado(libro)?.let { Instant.ofEpochMilli(it).atZone(zona).year to libro } }
                .groupBy({ it.first }, { it.second })
                .toSortedMap(compareByDescending { it })
                .map { (anio, libros) ->
                    AnioTerminados(anio, libros.filter { it.tipo == TipoLibro.TEXTO }, libros.filter { it.tipo == TipoLibro.COMIC })
                }
            val palabras = dias.values.sumOf { it.palabras }
            val segundos = dias.values.sumOf { it.segundosPalabras }
            // Hace falta al menos un par de minutos medidos para que la cifra tenga sentido.
            palabrasPorMinuto = if (segundos >= MINIMO_SEGUNDOS) (palabras * 60 / segundos).toInt() else null
            cargado = true
        }
    }

    /** Los días del periodo elegido (del más antiguo a hoy). */
    private fun diasDelPeriodo(hoy: LocalDate): List<LocalDate> = when (periodo) {
        Periodo.SEMANA -> (6 downTo 0).map { hoy.minusDays(it.toLong()) }
        Periodo.MES -> (29 downTo 0).map { hoy.minusDays(it.toLong()) }
        Periodo.ANIO -> generateSequence(hoy.minusDays(364)) { it.plusDays(1) }.takeWhile { !it.isAfter(hoy) }.toList()
    }

    /** Las barras del gráfico: un día por barra (semana y mes) o un mes por barra (año). */
    fun barras(idioma: Locale): List<Barra> {
        val hoy = LocalDate.now()
        val delPeriodo = diasDelPeriodo(hoy)
        return when (periodo) {
            Periodo.SEMANA -> delPeriodo.map {
                Barra(it.dayOfWeek.getDisplayName(TextStyle.NARROW, idioma), dias[it]?.segundos ?: 0)
            }
            Periodo.MES -> delPeriodo.mapIndexed { i, d ->
                // Con 30 barras no caben todas las etiquetas: una de cada cinco días, y hoy.
                Barra(if (i % 5 == 4 || i == delPeriodo.lastIndex) d.dayOfMonth.toString() else "", dias[d]?.segundos ?: 0)
            }
            Periodo.ANIO -> delPeriodo.groupBy { it.withDayOfMonth(1) }.toSortedMap().map { (mes, deMes) ->
                Barra(mes.month.getDisplayName(TextStyle.NARROW, idioma), deMes.sumOf { dias[it]?.segundos ?: 0 })
            }
        }
    }

    /** Tiempo total del periodo, separado en libros y cómics (desde que se separa). */
    fun totales(): Triple<Long, Long, Long> {
        val delPeriodo = diasDelPeriodo(LocalDate.now()).mapNotNull { dias[it] }
        return Triple(delPeriodo.sumOf { it.segundos }, delPeriodo.sumOf { it.segundosLibros }, delPeriodo.sumOf { it.segundosComics })
    }

    /**
     * Media por día del periodo, contando los días sin leer, pero solo desde el primer día con
     * datos (si la app se empezó a usar hace una semana, el año no se divide entre 365 días).
     */
    fun mediaDiaria(): Long {
        val primero = dias.keys.minOrNull() ?: return 0
        val contados = diasDelPeriodo(LocalDate.now()).count { !it.isBefore(primero) }.coerceAtLeast(1)
        return totales().first / contados
    }

    private companion object {
        const val MINIMO_SEGUNDOS = 60L
    }
}

/** Pantalla de estadísticas: tiempo leído, velocidad y lo terminado cada año (sin la racha: esa va en el widget). */
@Composable
fun PantallaEstadisticas(alVolver: () -> Unit) {
    val estado: EstadisticasViewModel = viewModel()
    val idioma = Locale(stringResource(R.string.codigo_idioma))
    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            Text(
                stringResource(R.string.estadisticas_titulo),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (!estado.cargado) return@Column

        // Tiempo leído.
        Bloque(stringResource(R.string.estadisticas_tiempo)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                listOf(
                    Periodo.SEMANA to R.string.estadisticas_semana,
                    Periodo.MES to R.string.estadisticas_mes,
                    Periodo.ANIO to R.string.estadisticas_anio,
                ).forEach { (p, texto) ->
                    FilterChip(
                        selected = estado.periodo == p,
                        onClick = { estado.periodo = p },
                        label = { Text(stringResource(texto)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorResource(R.color.texto),
                            selectedLabelColor = colorResource(R.color.fondo_tarjeta),
                        ),
                    )
                }
            }
            Grafico(estado.barras(idioma), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val (total, libros, comics) = estado.totales()
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
                Text(
                    stringResource(R.string.estadisticas_total, textoTiempo(total)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.estadisticas_media, textoTiempo(estado.mediaDiaria())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (libros + comics > 0) {
                    Text(
                        stringResource(R.string.estadisticas_libros_comics, textoTiempo(libros), textoTiempo(comics)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Velocidad de lectura.
        Bloque(stringResource(R.string.estadisticas_velocidad)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val ppm = estado.palabrasPorMinuto
                if (ppm != null) {
                    Text(
                        pluralStringResource(R.plurals.estadisticas_ppm, ppm, ppm),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.estadisticas_velocidad_nota),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.estadisticas_velocidad_vacia), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Terminados cada año.
        Bloque(stringResource(R.string.estadisticas_terminados)) {
            if (estado.terminados.isEmpty()) {
                Text(
                    stringResource(R.string.estadisticas_terminados_vacio),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            estado.terminados.forEachIndexed { i, anio ->
                if (i > 0) HorizontalDivider(color = colorResource(R.color.borde))
                FilaAnio(anio)
            }
        }
    }
}

/** Un año: «2026 · 3 libros · 1 cómic»; al tocarlo, se ven los títulos. */
@Composable
private fun FilaAnio(anio: AnioTerminados) {
    var abierto by rememberSaveable(anio.anio) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { abierto = !abierto }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val partes = buildList {
            if (anio.libros.isNotEmpty()) add(pluralStringResource(R.plurals.estadisticas_n_libros, anio.libros.size, anio.libros.size))
            if (anio.comics.isNotEmpty()) add(pluralStringResource(R.plurals.estadisticas_n_comics, anio.comics.size, anio.comics.size))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(anio.anio.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "  ·  " + partes.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(if (abierto) R.drawable.ic_subir else R.drawable.ic_bajar),
                contentDescription = stringResource(if (abierto) R.string.cd_ocultar_titulos else R.string.cd_ver_titulos),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (abierto) {
            Spacer(Modifier.height(6.dp))
            (anio.libros + anio.comics).forEach { libro ->
                Text(
                    "• " + libro.titulo + if (libro.autor.isNotBlank()) " — " + libro.autor else "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** Gráfico de barras sencillo (barras marrones sobre crema) con las etiquetas debajo. */
@Composable
private fun Grafico(barras: List<Barra>, modifier: Modifier = Modifier) {
    val maximo = (barras.maxOfOrNull { it.segundos } ?: 0L).coerceAtLeast(1L)
    val color = colorResource(R.color.marron)
    val fondo = colorResource(R.color.borde)
    val forma = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .border(1.dp, colorResource(R.color.borde), forma)
            .padding(12.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val hueco = size.width / barras.size
            val ancho = (hueco * 0.62f).coerceAtLeast(2f)
            barras.forEachIndexed { i, barra ->
                val x = i * hueco + (hueco - ancho) / 2
                // Una rayita en los días sin lectura, para que se vea que el día existe.
                val alto = if (barra.segundos == 0L) 3f else (barra.segundos.toFloat() / maximo * size.height).coerceAtLeast(6f)
                drawRoundRect(
                    color = if (barra.segundos == 0L) fondo else color,
                    topLeft = Offset(x, size.height - alto),
                    size = Size(ancho, alto),
                    cornerRadius = CornerRadius(ancho / 4, ancho / 4),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            barras.forEach { barra ->
                Text(
                    barra.etiqueta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
