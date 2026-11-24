package org.libertalia.reader.voz

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.R
import java.text.NumberFormat

/**
 * Panel pequeño de la lectura en voz alta, encima de la barra inferior: frase anterior,
 * pausa / seguir, frase siguiente, velocidad y parar.
 */
@Composable
fun PanelVoz(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val sonando by LecturaEnVoz.sonando.collectAsState()
    val velocidad by LecturaEnVoz.velocidad.collectAsState()
    val textoVelocidad = stringResource(
        R.string.voz_velocidad,
        // Con el formato del idioma de la app («1,25», no «1.25»).
        NumberFormat.getNumberInstance(java.util.Locale(stringResource(R.string.codigo_idioma))).apply { maximumFractionDigits = 2 }.format(velocidad),
    )
    val descripcionVelocidad = stringResource(R.string.cd_voz_velocidad)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            // La velocidad: cada toque pasa a la siguiente (0,75× … 2×).
            TextButton(
                onClick = { LecturaEnVoz.cambiarVelocidad(contexto) },
                modifier = Modifier.widthIn(min = 56.dp).heightIn(min = 48.dp).semantics { contentDescription = "$descripcionVelocidad: $textoVelocidad" },
            ) {
                Text(textoVelocidad, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = LecturaEnVoz::fraseAnterior) {
                Icon(painterResource(R.drawable.ic_frase_anterior), contentDescription = stringResource(R.string.cd_voz_anterior))
            }
            FilledIconButton(
                onClick = LecturaEnVoz::reproducirOPausar,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    painterResource(if (sonando) R.drawable.ic_pausa else R.drawable.ic_reproducir),
                    contentDescription = stringResource(if (sonando) R.string.cd_voz_pausar else R.string.cd_voz_reproducir),
                )
            }
            IconButton(onClick = LecturaEnVoz::fraseSiguiente) {
                Icon(painterResource(R.drawable.ic_frase_siguiente), contentDescription = stringResource(R.string.cd_voz_siguiente))
            }
            IconButton(onClick = LecturaEnVoz::parar) {
                Icon(painterResource(R.drawable.ic_cerrar), contentDescription = stringResource(R.string.cd_voz_parar))
            }
        }
    }
}

/** Aviso si no se ha podido leer en voz alta (por ejemplo, falta la voz del idioma del libro). */
@Composable
fun AvisoProblemaVoz() {
    val problema by LecturaEnVoz.problema.collectAsState()
    val contexto = LocalContext.current
    when (val p = problema) {
        null -> Unit
        is ProblemaVoz.FaltaVoz -> AlertDialog(
            onDismissRequest = LecturaEnVoz::problemaVisto,
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onBackground,
            title = { Text(stringResource(R.string.voz_falta_titulo)) },
            text = {
                Text(
                    stringResource(R.string.voz_falta_texto, p.idioma) +
                        (p.alternativa?.let { "\n\n" + stringResource(R.string.voz_falta_alternativa, it) } ?: ""),
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    // Algunos libros dicen mal su idioma: se puede leer con la voz del idioma de la app.
                    p.alternativa?.let { otro ->
                        TextButton(onClick = {
                            LecturaEnVoz.leerConIdiomaDeLaApp(contexto.applicationContext as LibertaliaApp)
                        }) { Text(stringResource(R.string.voz_leer_en, otro)) }
                    }
                    TextButton(onClick = {
                        LecturaEnVoz.problemaVisto()
                        // Los ajustes de «Texto a voz» de Android, donde se descargan las voces.
                        runCatching {
                            contexto.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Text(stringResource(R.string.voz_abrir_ajustes)) }
                }
            },
            dismissButton = {
                TextButton(onClick = LecturaEnVoz::problemaVisto) { Text(stringResource(R.string.boton_cancelar)) }
            },
        )
        ProblemaVoz.Error -> AlertDialog(
            onDismissRequest = LecturaEnVoz::problemaVisto,
            containerColor = MaterialTheme.colorScheme.background,
            textContentColor = MaterialTheme.colorScheme.onBackground,
            text = { Text(stringResource(R.string.voz_error)) },
            confirmButton = {
                TextButton(onClick = LecturaEnVoz::problemaVisto) { Text(stringResource(R.string.boton_entendido)) }
            },
        )
    }
}
