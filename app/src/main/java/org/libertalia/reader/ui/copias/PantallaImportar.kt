package org.libertalia.reader.ui.copias

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R
import org.libertalia.reader.copias.AnalisisCopia
import org.libertalia.reader.copias.AnalisisLibro
import org.libertalia.reader.copias.ErrorCopia
import org.libertalia.reader.copias.EstadoEnCopia
import org.libertalia.reader.copias.EstadoImportar
import org.libertalia.reader.ui.biblioteca.PortadaLibro
import java.text.DateFormat
import java.util.Date

/**
 * Pantalla «Importar una copia»: primero un resumen de lo que trae la copia (sin tocar nada);
 * al confirmar, se une con la biblioteca.
 */
@Composable
fun PantallaImportar(
    estado: EstadoImportar,
    alImportar: () -> Unit,
    alVolver: () -> Unit,
    alTerminar: () -> Unit,
    copiaBorrada: Boolean? = null,
    alBorrarCopia: () -> Unit = {},
) {
    // Mientras se une la copia no se sale (seguiría, pero sin ver cómo acaba).
    BackHandler(enabled = estado is EstadoImportar.Aplicando) {}

    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        ) {
            IconButton(onClick = alVolver, enabled = estado !is EstadoImportar.Aplicando) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.importar_titulo),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                val fecha = (estado as? EstadoImportar.Resumen)?.analisis?.fecha
                    ?: (estado as? EstadoImportar.Hecho)?.analisis?.fecha
                if (fecha != null && fecha > 0) {
                    Text(
                        stringResource(
                            R.string.importar_fecha,
                            org.libertalia.reader.ui.ajustes.AjustesApp.fechaHora(LocalContext.current, fecha),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(color = colorResource(R.color.borde))

        when (estado) {
            is EstadoImportar.Leyendo -> Progreso(stringResource(R.string.importar_leyendo, (estado.progreso * 100).toInt()), estado.progreso)
            is EstadoImportar.Aplicando -> Progreso(stringResource(R.string.importar_aplicando, (estado.progreso * 100).toInt()), estado.progreso)
            is EstadoImportar.Fallo -> Mensaje(
                titulo = stringResource(R.string.importar_error_titulo),
                texto = stringResource(
                    when (estado.motivo) {
                        ErrorCopia.Motivo.NO_ES_COPIA -> R.string.importar_error_no_es_copia
                        ErrorCopia.Motivo.VERSION_NUEVA -> R.string.importar_error_version
                        ErrorCopia.Motivo.ILEGIBLE -> R.string.importar_error_ilegible
                    },
                ),
                boton = stringResource(R.string.cd_volver),
                alPulsar = alVolver,
            )
            is EstadoImportar.Resumen -> Resumen(estado.analisis, alImportar, alVolver)
            is EstadoImportar.Hecho -> Hecho(estado, copiaBorrada, alBorrarCopia, alTerminar)
        }
    }
}

@Composable
private fun Resumen(analisis: AnalisisCopia, alImportar: () -> Unit, alCancelar: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Cifras(analisis) }
            item {
                Text(
                    stringResource(R.string.importar_que_trae),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                Column(Modifier.fillMaxWidth().recuadro()) {
                    analisis.libros.forEachIndexed { i, libro ->
                        if (i > 0) HorizontalDivider(color = colorResource(R.color.borde))
                        FilaLibro(libro)
                    }
                }
            }
        }
        HorizontalDivider(color = colorResource(R.color.borde))
        Column(
            Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.fondo_lectura))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.importar_nada_se_borra),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = alCancelar,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(stringResource(R.string.boton_cancelar), textAlign = TextAlign.Center)
                }
                Button(onClick = alImportar, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.importar_boton), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Las cifras del resumen, una por línea (solo las que no son cero). */
@Composable
private fun Cifras(analisis: AnalisisCopia, hecho: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().recuadro().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val lineas = buildList {
            if (analisis.nuevos > 0) add(pluralStringResource(if (hecho) R.plurals.importar_anadidos else R.plurals.importar_nuevos, analisis.nuevos, analisis.nuevos))
            if (analisis.yaEstaban > 0) add(pluralStringResource(R.plurals.importar_ya_estaban, analisis.yaEstaban, analisis.yaEstaban))
            if (analisis.tarjetasNuevas > 0) add(pluralStringResource(R.plurals.importar_tarjetas_nuevas, analisis.tarjetasNuevas, analisis.tarjetasNuevas))
            if (analisis.destacadasNuevas > 0) add(pluralStringResource(R.plurals.importar_destacadas_nuevas, analisis.destacadasNuevas, analisis.destacadasNuevas))
            if (analisis.actualizadas > 0) add(pluralStringResource(R.plurals.importar_actualizadas, analisis.actualizadas, analisis.actualizadas))
        }
        if (lineas.isEmpty()) {
            Text(stringResource(R.string.importar_sin_cambios), style = MaterialTheme.typography.bodyLarge)
        }
        lineas.forEach { Text("·  $it", style = MaterialTheme.typography.bodyLarge) }
        if (analisis.sinArchivo > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.importar_sin_archivo, analisis.sinArchivo, analisis.sinArchivo),
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.rojo),
            )
        }
    }
}

@Composable
private fun FilaLibro(analisis: AnalisisLibro) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        PortadaLibro(analisis.libro, Modifier.width(30.dp).height(44.dp), mostrarTitulo = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                analisis.libro.titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val detalle = buildList {
                val tarjetas = analisis.tarjetasNuevas + analisis.destacadasNuevas
                if (tarjetas > 0) add(pluralStringResource(R.plurals.importar_libro_tarjetas, tarjetas, tarjetas))
                if (analisis.sinArchivo) add(stringResource(R.string.importar_libro_sin_archivo))
            }
            if (detalle.isNotEmpty()) {
                Text(
                    detalle.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        val (texto, color) = when (analisis.estado) {
            EstadoEnCopia.NUEVO -> stringResource(R.string.importar_etiqueta_nuevo) to colorResource(R.color.estado_terminado)
            EstadoEnCopia.VUELVE -> stringResource(R.string.importar_etiqueta_vuelve) to colorResource(R.color.estado_leyendo)
            EstadoEnCopia.YA_ESTA -> stringResource(R.string.importar_etiqueta_ya_esta) to colorResource(R.color.texto_secundario)
        }
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun Hecho(estado: EstadoImportar.Hecho, copiaBorrada: Boolean?, alBorrarCopia: () -> Unit, alTerminar: () -> Unit) {
    val analisis = estado.analisis
    var confirmar by remember { mutableStateOf(false) }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            containerColor = colorResource(R.color.fondo_tarjeta),
            titleContentColor = colorResource(R.color.texto),
            textContentColor = colorResource(R.color.texto),
            title = { Text(stringResource(R.string.importar_borrar_copia_titulo, estado.nombreCopia)) },
            text = { Text(stringResource(R.string.importar_borrar_copia_confirmar)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    alBorrarCopia()
                }) { Text(stringResource(R.string.originales_borrar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text(stringResource(R.string.boton_cancelar)) }
            },
        )
    }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(colorResource(R.color.estado_terminado)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_hecho), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.importar_hecho), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Cifras(analisis, hecho = true)
        // El archivo de copia del móvil (p. ej. en Descargas) ya no hace falta: se ofrece borrarlo.
        if (estado.copiaBorrable) {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().recuadro().padding(16.dp)) {
                when (copiaBorrada) {
                    null -> {
                        Text(stringResource(R.string.importar_borrar_copia_texto), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { confirmar = true },
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.importar_borrar_copia)) }
                    }
                    true -> Text(stringResource(R.string.importar_copia_borrada), style = MaterialTheme.typography.bodyMedium)
                    false -> Text(
                        stringResource(R.string.importar_copia_no_borrada),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorResource(R.color.rojo),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = alTerminar, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.importar_ir_biblioteca))
        }
    }
}

@Composable
private fun Progreso(texto: String, progreso: Float) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progreso },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = colorResource(R.color.borde),
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun Mensaje(titulo: String, texto: String, boton: String, alPulsar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = alPulsar) { Text(boton) }
    }
}

/** Recuadro crema con borde. */
@Composable
private fun Modifier.recuadro(): Modifier {
    val forma = RoundedCornerShape(16.dp)
    return this
        .clip(forma)
        .background(colorResource(R.color.fondo_tarjeta))
        .border(1.dp, colorResource(R.color.borde), forma)
}
