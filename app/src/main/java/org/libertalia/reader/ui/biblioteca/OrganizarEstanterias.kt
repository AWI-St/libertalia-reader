package org.libertalia.reader.ui.biblioteca

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R
import org.libertalia.reader.datos.Estanteria

/** Lo que se puede hacer con las estanterías creadas por el usuario. */
data class AccionesEstanterias(
    val renombrar: (Estanteria, String) -> Unit,
    val mover: (Estanteria, haciaArriba: Boolean) -> Unit,
    val eliminar: (Estanteria) -> Unit,
)

/**
 * Lista de las estanterías del usuario para cambiarlas de orden,
 * cambiarles el nombre o borrarlas. Al borrar una, sus libros no se tocan.
 */
@Composable
fun OrganizarEstanterias(
    estanterias: List<Estanteria>,
    acciones: AccionesEstanterias,
    alCerrar: () -> Unit,
) {
    // Estantería que se está renombrando o borrando (por su id).
    var idRenombrar by rememberSaveable { mutableStateOf<String?>(null) }
    var idEliminar by rememberSaveable { mutableStateOf<String?>(null) }

    val renombrando = estanterias.find { it.id == idRenombrar }
    val eliminando = estanterias.find { it.id == idEliminar }

    when {
        renombrando != null -> DialogoRenombrar(
            estanteria = renombrando,
            alGuardar = { acciones.renombrar(renombrando, it) },
            alCerrar = { idRenombrar = null },
        )
        eliminando != null -> AlertDialog(
            onDismissRequest = { idEliminar = null },
            containerColor = colorResource(R.color.fondo_tarjeta),
            titleContentColor = colorResource(R.color.texto),
            textContentColor = colorResource(R.color.texto),
            title = { Text(stringResource(R.string.eliminar_estanteria_titulo)) },
            text = { Text(stringResource(R.string.eliminar_estanteria_mensaje, eliminando.nombre)) },
            confirmButton = {
                TextButton(onClick = {
                    acciones.eliminar(eliminando)
                    idEliminar = null
                }) { Text(stringResource(R.string.boton_eliminar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { idEliminar = null }) { Text(stringResource(R.string.boton_cancelar)) }
            },
        )
        else -> AlertDialog(
            onDismissRequest = alCerrar,
            containerColor = colorResource(R.color.fondo_tarjeta),
            titleContentColor = colorResource(R.color.texto),
            textContentColor = colorResource(R.color.texto),
            title = { Text(stringResource(R.string.organizar_estanterias)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    estanterias.forEachIndexed { indice, estanteria ->
                        if (indice > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = estanteria.nombre,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            BotonIcono(
                                R.drawable.ic_subir, stringResource(R.string.cd_subir),
                                activo = indice > 0,
                            ) { acciones.mover(estanteria, true) }
                            BotonIcono(
                                R.drawable.ic_bajar, stringResource(R.string.cd_bajar),
                                activo = indice < estanterias.lastIndex,
                            ) { acciones.mover(estanteria, false) }
                            BotonIcono(R.drawable.ic_editar, stringResource(R.string.cd_renombrar)) {
                                idRenombrar = estanteria.id
                            }
                            BotonIcono(
                                R.drawable.ic_eliminar, stringResource(R.string.cd_eliminar_estanteria),
                                color = MaterialTheme.colorScheme.error,
                            ) { idEliminar = estanteria.id }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_listo)) } },
        )
    }
}

@Composable
private fun DialogoRenombrar(estanteria: Estanteria, alGuardar: (String) -> Unit, alCerrar: () -> Unit) {
    var nombre by rememberSaveable { mutableStateOf(estanteria.nombre) }
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.renombrar_estanteria)) },
        text = {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text(stringResource(R.string.campo_nombre)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = nombre.isNotBlank(),
                onClick = {
                    alGuardar(nombre)
                    alCerrar()
                },
            ) { Text(stringResource(R.string.boton_guardar)) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_cancelar)) } },
    )
}

/** Botón de icono de 48 dp (zona táctil cómoda). */
@Composable
private fun BotonIcono(
    @DrawableRes icono: Int,
    descripcion: String,
    activo: Boolean = true,
    color: Color = colorResource(R.color.texto),
    alPulsar: () -> Unit,
) {
    IconButton(onClick = alPulsar, enabled = activo) {
        Icon(
            painterResource(icono),
            contentDescription = descripcion,
            tint = if (activo) color else color.copy(alpha = 0.25f),
            modifier = Modifier.size(22.dp),
        )
    }
}
