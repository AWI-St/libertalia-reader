package org.libertalia.reader.ui.biblioteca

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.libertalia.reader.R
import org.libertalia.reader.datos.Libro

/**
 * Aviso al añadir un archivo que pesa mucho (boceto 14): ¿incluirlo en la copia automática
 * o guardar solo la página y los marcadores? Con «Recordar mi elección» no se vuelve a preguntar.
 */
@Composable
fun DialogoArchivoPesado(
    libro: Libro,
    alResponder: (incluir: Boolean, recordar: Boolean) -> Unit,
) {
    val contexto = LocalContext.current
    var recordar by remember(libro.id) { mutableStateOf(false) }
    Dialog(onDismissRequest = { alResponder(true, false) }) {
        Surface(shape = RoundedCornerShape(28.dp), color = colorResource(R.color.fondo_lectura)) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PortadaLibro(libro, Modifier.width(44.dp).height(60.dp), mostrarTitulo = false)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        val titulo = libro.tomo?.let { stringResource(R.string.pesado_titulo_tomo, libro.titulo, it) } ?: libro.titulo
                        Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            libro.formato.name + " · " + Formatter.formatShortFileSize(contexto, libro.tamanoBytes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.pesado_pregunta), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.pesado_explicacion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { alResponder(true, recordar) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.pesado_incluir), textAlign = TextAlign.Center) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { alResponder(false, recordar) },
                    border = BorderStroke(1.dp, colorResource(R.color.borde)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorResource(R.color.texto)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.pesado_solo_datos), textAlign = TextAlign.Center) }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Checkbox) { recordar = !recordar },
                ) {
                    Checkbox(
                        checked = recordar,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.originales_recordar), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
