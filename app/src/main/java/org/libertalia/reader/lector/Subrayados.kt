package org.libertalia.reader.lector

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R
import org.libertalia.reader.datos.ColorSubrayado
import org.libertalia.reader.datos.Tarjeta
import org.readium.r2.shared.publication.Locator

/**
 * La frase completa donde está un subrayado: desde el punto anterior hasta el punto
 * siguiente. Readium da algo de texto de antes y de después de lo seleccionado.
 */
object FraseCompleta {

    private val FIN_DE_FRASE = Regex("""[.!?…»”]\s""")

    fun de(texto: Locator.Text): String? {
        val subrayado = texto.highlight?.trim() ?: return null
        val antes = texto.before.orEmpty()
        val despues = texto.after.orEmpty()
        // Lo de antes: desde el último final de frase.
        val inicio = FIN_DE_FRASE.findAll(antes).lastOrNull()?.range?.last?.plus(1) ?: 0
        // Lo de después: hasta el primer final de frase (incluido el signo).
        val fin = Regex("""[.!?…]""").find(despues)?.range?.last?.plus(1) ?: despues.length
        val frase = (antes.substring(inicio) + texto.highlight.orEmpty() + despues.substring(0, fin))
            .replace(Regex("""\s+"""), " ")
            .trim()
        return frase.takeIf { it.isNotEmpty() && it != subrayado }
    }
}

/** Círculos de los cuatro colores; el elegido lleva un aro. */
@Composable
fun SelectorColores(elegido: ColorSubrayado?, alElegir: (ColorSubrayado) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ColorSubrayado.entries.forEach { color ->
            val nombre = stringResource(color.nombre)
            val esElegido = color == elegido
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton) { alElegir(color) }
                    .semantics {
                        contentDescription = nombre
                        selected = esElegido
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (esElegido) 40.dp else 34.dp)
                        .clip(CircleShape)
                        .background(colorResource(color.color))
                        .then(
                            if (esElegido) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                            else Modifier
                        ),
                )
            }
        }
    }
}

/** Menú al tocar un subrayado: cambiar el color, la nota (añadir, editar, borrar) o quitarlo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaSubrayado(
    tarjeta: Tarjeta,
    alCambiarColor: (ColorSubrayado) -> Unit,
    alEscribirNota: () -> Unit,
    alBorrarNota: () -> Unit,
    alAnadirEtiqueta: () -> Unit,
    alQuitarEtiqueta: (String) -> Unit,
    alQuitar: () -> Unit,
    alCerrar: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "«${tarjeta.texto}»",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            tarjeta.nota?.let { nota ->
                Spacer(Modifier.heightIn(min = 8.dp))
                Text(
                    stringResource(R.string.subrayado_nota_actual, nota),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(Modifier.heightIn(min = 16.dp))
            SelectorColores(tarjeta.color, alCambiarColor, Modifier.padding(horizontal = 20.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            OpcionHoja(R.drawable.ic_editar, stringResource(if (tarjeta.nota == null) R.string.subrayado_anadir_nota else R.string.subrayado_editar_nota), onClick = alEscribirNota)
            if (tarjeta.nota != null) {
                OpcionHoja(R.drawable.ic_eliminar, stringResource(R.string.subrayado_borrar_nota), onClick = alBorrarNota)
            }
            // Etiquetas de esta tarjeta: «#etiqueta ×» y «Añadir etiqueta».
            val etiquetas = org.libertalia.reader.tarjetas.Etiquetas.de(tarjeta)
            if (etiquetas.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                ) {
                    etiquetas.forEach { etiqueta ->
                        val quitar = stringResource(R.string.cd_quitar_etiqueta, etiqueta)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(colorResource(R.color.borde).copy(alpha = 0.6f))
                                .clickable { alQuitarEtiqueta(etiqueta) }
                                .semantics { contentDescription = quitar }
                                .heightIn(min = 36.dp)
                                .padding(start = 12.dp, end = 8.dp),
                        ) {
                            Text("#$etiqueta", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(6.dp))
                            Text("×", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            OpcionHoja(R.drawable.ic_etiqueta, stringResource(R.string.subrayado_anadir_etiqueta), onClick = alAnadirEtiqueta)
            OpcionHoja(
                R.drawable.ic_eliminar,
                stringResource(R.string.subrayado_quitar),
                color = MaterialTheme.colorScheme.error,
                onClick = alQuitar,
            )
        }
    }
}

@Composable
private fun OpcionHoja(icono: Int, texto: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tinte = if (color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else color
        Icon(painterResource(icono), contentDescription = null, tint = tinte, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, color = tinte)
    }
}

/** Escribir o cambiar la nota de un subrayado (con opción de borrarla si ya la tiene). */
@Composable
fun DialogoNota(tarjeta: Tarjeta, alGuardar: (String) -> Unit, alBorrar: () -> Unit, alCerrar: () -> Unit) {
    var texto by rememberSaveable(tarjeta.id) { mutableStateOf(tarjeta.nota.orEmpty()) }
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text(stringResource(if (tarjeta.nota == null) R.string.nota_titulo_nueva else R.string.nota_titulo_editar)) },
        text = {
            Column {
                Text(
                    "«${tarjeta.texto}»",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.heightIn(min = 12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(2_000) },
                    label = { Text(stringResource(R.string.nota_campo)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(texto) }) { Text(stringResource(R.string.boton_guardar)) }
        },
        dismissButton = {
            Row {
                if (tarjeta.nota != null) {
                    TextButton(onClick = alBorrar) {
                        Text(stringResource(R.string.subrayado_borrar_nota), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_cancelar)) }
            }
        },
    )
}

/**
 * Página destacada: descripción breve (opcional) que hace de título en sus tarjetas.
 * [nueva] es true si se acaba de destacar; si no, se ofrece también quitarla.
 */
@Composable
fun DialogoDestacada(
    tarjeta: Tarjeta,
    nueva: Boolean,
    alGuardar: (String) -> Unit,
    alQuitar: () -> Unit,
    alCerrar: () -> Unit,
) {
    var texto by rememberSaveable(tarjeta.id) { mutableStateOf(tarjeta.descripcion.orEmpty()) }
    AlertDialog(
        onDismissRequest = { alGuardar(texto) },
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text(stringResource(if (nueva) R.string.destacada_titulo_nueva else R.string.destacada_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.destacada_explicacion), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.heightIn(min = 12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.replace('\n', ' ').take(MAXIMO_DESCRIPCION) },
                    label = { Text(stringResource(R.string.destacada_campo)) },
                    supportingText = { Text("${texto.length} / $MAXIMO_DESCRIPCION") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(texto) }) { Text(stringResource(R.string.boton_guardar)) }
        },
        dismissButton = {
            if (!nueva) {
                TextButton(onClick = alQuitar) {
                    Text(stringResource(R.string.destacada_quitar), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

private const val MAXIMO_DESCRIPCION = 80

/**
 * Lista de las páginas destacadas del libro: la descripción como título y debajo el
 * capítulo (y la página, en los PDF). Tocar una lleva a ella; el lápiz cambia su
 * descripción o la quita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaDestacadas(
    destacadas: List<Tarjeta>,
    esPdf: Boolean,
    alIr: (Tarjeta) -> Unit,
    alEditar: (Tarjeta) -> Unit,
    alCerrar: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.destacadas_titulo),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (destacadas.isEmpty()) {
                Text(
                    stringResource(R.string.destacadas_vacio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(destacadas, key = { it.id }) { tarjeta ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { alIr(tarjeta) }
                            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_destacar_lleno),
                            contentDescription = null,
                            tint = colorResource(R.color.rojo),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                tarjeta.descripcion ?: stringResource(R.string.destacadas_sin_descripcion),
                                style = MaterialTheme.typography.titleMedium,
                                fontStyle = if (tarjeta.descripcion == null) FontStyle.Italic else null,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // En los PDF la página es la del archivo (fija); en EPUB depende de la letra.
                            val pagina = tarjeta.pagina?.takeIf { esPdf }?.let { stringResource(R.string.destacadas_pagina, it) }
                            val detalle = listOfNotNull(tarjeta.capitulo?.takeIf { it.isNotBlank() }, pagina).joinToString(" · ")
                            if (detalle.isNotEmpty()) {
                                Text(
                                    detalle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { alEditar(tarjeta) }) {
                            Icon(
                                painterResource(R.drawable.ic_editar),
                                contentDescription = stringResource(R.string.destacadas_editar),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Aviso que sale abajo al subrayar (boceto 3), con «Ver» para abrir las tarjetas. */
@Composable
fun AvisoSubrayado(alVer: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onBackground,
        contentColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Text(
                stringResource(R.string.aviso_subrayado),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            TextButton(onClick = alVer) {
                Text(
                    stringResource(R.string.aviso_subrayado_ver),
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
    }
}
