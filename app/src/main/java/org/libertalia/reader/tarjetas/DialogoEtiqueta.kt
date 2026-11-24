package org.libertalia.reader.tarjetas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R
import org.libertalia.reader.datos.Tarjeta

/** Reglas de las etiquetas: cómo se limpian, se añaden, se quitan y se sugieren. */
object ReglasEtiquetas {

    /** Sin «#» delante, sin comas (separan las etiquetas al guardarlas) y en minúsculas. */
    fun limpiar(texto: String): String =
        texto.replace("#", "").replace(',', ' ').replace(Regex("\\s+"), " ").trim().lowercase()

    /** Las etiquetas de la tarjeta con [nueva] añadida (null si no cambia nada). */
    fun anadir(tarjeta: Tarjeta, nueva: String): String? {
        val limpia = limpiar(nueva)
        val actuales = Etiquetas.de(tarjeta)
        if (limpia.isEmpty() || limpia in actuales) return null
        return (actuales + limpia).joinToString(",")
    }

    /** Las etiquetas de la tarjeta sin [etiqueta] (null si se queda sin ninguna). */
    fun quitar(tarjeta: Tarjeta, etiqueta: String): String? =
        (Etiquetas.de(tarjeta) - etiqueta).joinToString(",").takeIf { it.isNotEmpty() }

    /**
     * Las etiquetas ya usadas que se parecen a lo escrito: primero las que empiezan igual
     * («fil» → «filosofía»), después las que lo contienen; dentro de cada grupo, de la más
     * usada a la menos ([existentes] ya viene en ese orden). Sin escribir nada, las más usadas.
     * No se sugieren las que ya tiene la tarjeta. Sin distinguir mayúsculas ni tildes.
     */
    fun sugerencias(
        existentes: List<Pair<String, Int>>,
        escrito: String,
        yaPuestas: Set<String>,
        maximo: Int = 8,
    ): List<Pair<String, Int>> {
        val libres = existentes.filter { it.first !in yaPuestas }
        val buscada = BuscarTexto.normalizar(limpiar(escrito))
        if (buscada.isEmpty()) return libres.take(maximo)
        val empiezan = libres.filter { BuscarTexto.normalizar(it.first).startsWith(buscada) }
        val contienen = libres.filter { it !in empiezan && BuscarTexto.normalizar(it.first).contains(buscada) }
        return (empiezan + contienen).take(maximo)
    }
}

/**
 * «Nueva etiqueta»: el «#» ya está escrito delante y no se puede borrar. Mientras se escribe,
 * debajo salen las etiquetas que ya existen y se parecen; tocar una la pone directamente.
 *
 * [existentes]: todas las etiquetas de todas las tarjetas, de la más usada a la menos.
 * [yaPuestas]: las que ya tiene esta tarjeta (no se sugieren).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoEtiqueta(
    existentes: List<Pair<String, Int>>,
    yaPuestas: Set<String>,
    alGuardar: (String) -> Unit,
    alCerrar: () -> Unit,
) {
    var texto by rememberSaveable { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    val sugerencias = remember(texto, existentes, yaPuestas) { ReglasEtiquetas.sugerencias(existentes, texto, yaPuestas) }
    val valida = ReglasEtiquetas.limpiar(texto).isNotEmpty()

    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        title = { Text(stringResource(R.string.tarjeta_nueva_etiqueta)) },
        text = {
            Column {
                OutlinedTextField(
                    value = texto,
                    // El «#» va fuera del texto (no se puede borrar); si se escribe otro, se quita.
                    onValueChange = { texto = it.replace("#", "").replace(",", "").take(40) },
                    leadingIcon = {
                        Text(
                            "#",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    placeholder = { Text(stringResource(R.string.etiqueta_pista)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valida) alGuardar(texto) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(foco),
                )
                if (sugerencias.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(if (texto.isBlank()) R.string.etiqueta_mas_usadas else R.string.etiqueta_parecidas),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sugerencias.forEach { (etiqueta, usos) ->
                            Text(
                                stringResource(R.string.etiqueta_sugerida, etiqueta, usos),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colorResource(R.color.borde).copy(alpha = 0.6f))
                                    .clickable { alGuardar(etiqueta) }
                                    .heightIn(min = 36.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valida, onClick = { alGuardar(texto) }) {
                Text(stringResource(R.string.boton_guardar))
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_cancelar)) } },
    )
}
