package org.libertalia.reader.ui.copias

import android.Manifest
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import org.libertalia.reader.R
import org.libertalia.reader.copias.AvisoCopia
import org.libertalia.reader.copias.CopiaAutomatica
import org.libertalia.reader.copias.CopiaAutomaticaViewModel
import java.text.DateFormat
import java.util.Date

/** Texto de cada frecuencia: «Cada 3 días», «Cada mes», «Nunca». */
@Composable
fun textoFrecuencia(dias: Int): String = when (dias) {
    0 -> stringResource(R.string.copia_nunca)
    30 -> stringResource(R.string.copia_cada_mes)
    else -> pluralStringResource(R.plurals.copia_cada_dias, dias, dias)
}

/**
 * Pantalla «Copia automática»: dónde se guarda, cada cuánto, cuánto pesa, la última copia
 * correcta y «Hacer copia ahora». Si la última falló, lo dice arriba en rojo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RutaCopiaAutomatica(alVolver: () -> Unit) {
    val estado: CopiaAutomaticaViewModel = viewModel()
    val contexto = LocalContext.current
    val info = estado.info

    // Al volver a la pantalla (p. ej. desde Drive), se vuelve a mirar cómo está.
    val ciclo = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento -> if (evento == Lifecycle.Event.ON_RESUME) estado.actualizar() }
        ciclo.addObserver(observador)
        onDispose { ciclo.removeObserver(observador) }
    }

    // Permiso para avisar con una notificación si algún día falla (Android 13 o posterior).
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            estado.elegirDestino(uri)
            if (Build.VERSION.SDK_INT >= 33) pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            Text(stringResource(R.string.copia_titulo), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.copia_explicacion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // La última copia falló: se dice por qué y cómo arreglarlo.
        val resultado = info.ultimoResultado
        if (info.falla && resultado != null) {
            Column(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorResource(R.color.rojo).copy(alpha = 0.12f))
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.copia_fallo_titulo), style = MaterialTheme.typography.titleMedium, color = colorResource(R.color.rojo))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(AvisoCopia.textoDe(resultado)), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Dónde se guarda.
        Recuadro(stringResource(R.string.copia_donde)) {
            Text(
                info.nombre ?: stringResource(if (info.uri == null) R.string.copia_sin_destino else R.string.copia_destino_perdido),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (info.nombre != null) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { elegir.launch(CopiaAutomatica.NOMBRE_SUGERIDO) },
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(if (info.uri == null) R.string.copia_elegir else R.string.copia_cambiar))
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.copia_donde_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Cada cuánto.
        Recuadro(stringResource(R.string.copia_frecuencia)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CopiaAutomatica.FRECUENCIAS.forEach { dias ->
                    FilterChip(
                        selected = info.dias == dias,
                        onClick = { estado.cambiarFrecuencia(dias) },
                        label = { Text(textoFrecuencia(dias)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.copia_frecuencia_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Peso, última copia y «Hacer copia ahora».
        Recuadro(stringResource(R.string.copia_estado)) {
            val peso = estado.peso
            Text(
                stringResource(
                    R.string.copia_peso,
                    if (peso == null) "…" else Formatter.formatShortFileSize(contexto, peso),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                info.fechaUltimaCorrecta?.let {
                    stringResource(R.string.copia_ultima, org.libertalia.reader.ui.ajustes.AjustesApp.fechaHora(LocalContext.current, it))
                } ?: stringResource(R.string.copia_ninguna),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.copia_archivos_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            val progreso = estado.progreso
            if (progreso != null) {
                Text(stringResource(R.string.copia_haciendo, (progreso * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progreso },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = colorResource(R.color.borde),
                    drawStopIndicator = {},
                )
            } else {
                Button(
                    onClick = estado::hacerAhora,
                    enabled = info.uri != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.copia_hacer_ahora)) }
            }
        }
    }
}

/** Recuadro crema con borde y un título pequeño en marrón. */
@Composable
private fun Recuadro(titulo: String, contenido: @Composable () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(forma)
            .background(colorResource(R.color.fondo_tarjeta))
            .border(1.dp, colorResource(R.color.borde), forma)
            .padding(16.dp),
    ) {
        Text(
            titulo.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        contenido()
    }
}
