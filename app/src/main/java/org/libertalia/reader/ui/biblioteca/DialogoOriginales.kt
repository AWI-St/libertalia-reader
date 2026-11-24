package org.libertalia.reader.ui.biblioteca

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R
import org.libertalia.reader.biblioteca.ArchivosExternos

/**
 * «¿Borrar el original?»: el libro ya está guardado en la carpeta Libertalia, así que el
 * archivo que se añadió (p. ej. en Descargas) sobra. Con «Recordar mi elección» no se vuelve
 * a preguntar.
 */
@Composable
fun DialogoBorrarOriginales(
    originales: List<Uri>,
    alResponder: (borrar: Boolean, recordar: Boolean) -> Unit,
) {
    val contexto = LocalContext.current
    val primero = remember(originales) { originales.firstOrNull()?.let { ArchivosExternos.nombre(contexto, it) }.orEmpty() }
    var recordar by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { alResponder(false, false) },
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(pluralStringResource(R.plurals.originales_titulo, originales.size)) },
        text = {
            Column {
                Text(pluralStringResource(R.plurals.originales_texto, originales.size, originales.size, primero))
                Spacer(Modifier.height(12.dp))
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
        },
        confirmButton = {
            TextButton(onClick = { alResponder(true, recordar) }) { Text(stringResource(R.string.originales_borrar)) }
        },
        dismissButton = {
            TextButton(onClick = { alResponder(false, recordar) }) { Text(stringResource(R.string.originales_conservar)) }
        },
    )
}

/**
 * Ha llegado un libro o cómic nuevo con «Abrir con»: ¿se añade a la biblioteca o solo se lee?
 * «Solo leer» lo abre sin guardar nada (ni posición, ni racha, ni subrayados).
 */
@Composable
fun DialogoAnadirOSoloLeer(
    nombre: String,
    alResponder: (anadir: Boolean?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { alResponder(null) },
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.abrir_con_titulo)) },
        text = { Text(stringResource(R.string.abrir_con_texto, nombre)) },
        confirmButton = {
            TextButton(onClick = { alResponder(true) }) { Text(stringResource(R.string.abrir_con_anadir)) }
        },
        dismissButton = {
            TextButton(onClick = { alResponder(false) }) { Text(stringResource(R.string.abrir_con_solo_leer)) }
        },
    )
}

/**
 * Tras añadir un archivo que llegó de otra app: ¿se quita de su carpeta (p. ej. Descargas)
 * para dejarlo solo en la de Libertalia? En los libros ya hay una copia en la carpeta; los
 * cómics se mueven a ella. Con «Recordar mi elección» no se vuelve a preguntar.
 */
@Composable
fun DialogoMoverOriginal(
    nombre: String,
    esComic: Boolean,
    alResponder: (mover: Boolean, recordar: Boolean) -> Unit,
) {
    var recordar by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { alResponder(false, false) },
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.mover_titulo)) },
        text = {
            Column {
                Text(stringResource(if (esComic) R.string.mover_texto_comic else R.string.mover_texto_libro, nombre))
                Spacer(Modifier.height(12.dp))
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
        },
        confirmButton = {
            TextButton(onClick = { alResponder(true, recordar) }) { Text(stringResource(R.string.mover_si)) }
        },
        dismissButton = {
            TextButton(onClick = { alResponder(false, recordar) }) { Text(stringResource(R.string.originales_conservar)) }
        },
    )
}
