package org.libertalia.reader.ui.copias

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.TipoLibro
import org.libertalia.reader.ui.biblioteca.PortadaLibro

/** A partir de este peso, la copia puede ser demasiado grande para el correo. */
private const val LIMITE_CORREO = 25L * 1024 * 1024

/**
 * Pantalla Exportar (boceto 11). Todo lo que se marca se guarda en un único archivo
 * .libertalia, que se puede guardar donde se quiera o compartir.
 */
@Composable
fun PantallaExportar(
    libros: List<Libro>,
    conteoTarjetas: Map<String, Int>,
    seleccion: Set<String>,
    incluirArchivos: Boolean,
    progreso: Float?,
    pesoEstimado: (Libro) -> Long,
    alCambiarIncluirArchivos: (Boolean) -> Unit,
    alAlternar: (String) -> Unit,
    alSeleccionarTodos: (Boolean) -> Unit,
    alGuardarEn: () -> Unit,
    alCompartir: () -> Unit,
    alVolver: () -> Unit,
) {
    val contexto = LocalContext.current
    val elegidos = libros.filter { it.id in seleccion }
    val tarjetas = elegidos.sumOf { conteoTarjetas[it.id] ?: 0 }
    val peso = elegidos.sumOf(pesoEstimado)
    val ocupado = progreso != null

    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding(),
    ) {
        // Cabecera: volver, título y explicación.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        ) {
            IconButton(onClick = alVolver, enabled = !ocupado) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.exportar_titulo),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.exportar_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = colorResource(R.color.borde))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // «Incluir los archivos»
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tarjeta()
                        .clickable(enabled = !ocupado, role = Role.Switch) { alCambiarIncluirArchivos(!incluirArchivos) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.exportar_incluir_archivos), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                if (incluirArchivos) R.string.exportar_incluir_archivos_si else R.string.exportar_incluir_archivos_no,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = incluirArchivos,
                        onCheckedChange = alCambiarIncluirArchivos,
                        enabled = !ocupado,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
            }
            // Lista con «Seleccionar todos» arriba.
            item {
                Column(Modifier.fillMaxWidth().tarjeta()) {
                    val estadoTodos = when (elegidos.size) {
                        0 -> ToggleableState.Off
                        libros.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorResource(R.color.fondo_app))
                            .clickable(enabled = !ocupado, role = Role.Checkbox) {
                                alSeleccionarTodos(estadoTodos != ToggleableState.On)
                            }
                            .heightIn(min = 60.dp)
                            .padding(horizontal = 12.dp),
                    ) {
                        TriStateCheckbox(
                            state = estadoTodos,
                            onClick = null,
                            enabled = !ocupado,
                            colors = coloresCasilla(),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.exportar_seleccionar_todos),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.exportar_x_de_y, elegidos.size, libros.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    libros.forEach { libro ->
                        HorizontalDivider(color = colorResource(R.color.borde))
                        FilaLibro(
                            libro = libro,
                            marcado = libro.id in seleccion,
                            tarjetas = conteoTarjetas[libro.id] ?: 0,
                            peso = pesoEstimado(libro),
                            activo = !ocupado,
                            alPulsar = { alAlternar(libro.id) },
                        )
                    }
                }
            }
        }

        // Abajo: resumen, aviso de peso y botones.
        HorizontalDivider(color = colorResource(R.color.borde))
        Column(
            Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.fondo_lectura))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.exportar_elementos, elegidos.size, elegidos.size) + " · " +
                        pluralStringResource(R.plurals.numero_tarjetas, tarjetas, tarjetas),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formatter.formatShortFileSize(contexto, peso),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (peso > LIMITE_CORREO) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.exportar_aviso_correo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorResource(R.color.rojo),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (progreso != null) {
                Text(
                    stringResource(R.string.exportar_preparando, (progreso * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progreso },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = colorResource(R.color.borde),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(20.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = alGuardarEn,
                        enabled = elegidos.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(painterResource(R.drawable.ic_guardar_en), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.exportar_guardar_en), textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        onClick = alCompartir,
                        enabled = elegidos.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (elegidos.isNotEmpty()) MaterialTheme.colorScheme.primary else colorResource(R.color.borde),
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(painterResource(R.drawable.ic_compartir), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.exportar_compartir), textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.exportar_pista_botones),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FilaLibro(
    libro: Libro,
    marcado: Boolean,
    tarjetas: Int,
    peso: Long,
    activo: Boolean,
    alPulsar: () -> Unit,
) {
    val contexto = LocalContext.current
    val tamano = Formatter.formatShortFileSize(contexto, peso)
    val detalle = if (libro.tipo == TipoLibro.COMIC) {
        stringResource(R.string.exportar_detalle_comic, tamano)
    } else {
        pluralStringResource(R.plurals.numero_tarjetas, tarjetas, tarjetas) + " · " + tamano
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = activo, role = Role.Checkbox, onClick = alPulsar)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Checkbox(checked = marcado, onCheckedChange = null, enabled = activo, colors = coloresCasilla())
        Spacer(Modifier.width(12.dp))
        PortadaLibro(libro, Modifier.width(30.dp).height(44.dp), mostrarTitulo = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                libro.titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun coloresCasilla() = CheckboxDefaults.colors(
    checkedColor = MaterialTheme.colorScheme.primary,
    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
    checkmarkColor = MaterialTheme.colorScheme.surface,
)

/** Recuadro crema con borde, como las tarjetas del boceto. */
@Composable
private fun Modifier.tarjeta(): Modifier {
    val forma = RoundedCornerShape(16.dp)
    return this
        .clip(forma)
        .background(colorResource(R.color.fondo_tarjeta))
        .border(1.dp, colorResource(R.color.borde), forma)
}
