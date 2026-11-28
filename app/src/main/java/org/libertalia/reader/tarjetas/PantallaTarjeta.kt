package org.libertalia.reader.tarjetas

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.libertalia.reader.R
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.lector.SelectorColores
import org.libertalia.reader.ui.biblioteca.PortadaLibro

/**
 * Una tarjeta abierta (boceto 8): el libro, la frase con el subrayado, el color, la nota,
 * las etiquetas y las fechas. Abajo, flechas a la anterior y la siguiente e «Ir al libro».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaTarjeta(
    estado: TarjetasViewModel,
    idInicial: String,
    alVolver: () -> Unit,
    alIrAlLibro: (Tarjeta) -> Unit,
    alCompartir: (Tarjeta) -> Unit,
    alEtiqueta: (String) -> Unit = {},
) {
    var idActual by rememberSaveable { mutableStateOf(idInicial) }
    val lista = estado.visibles.ifEmpty { estado.todas.orEmpty() }
    val tarjeta = estado.tarjeta(idActual)
    val libro = estado.libro
    // Si la tarjeta deja de existir (se ha borrado), se vuelve a la lista.
    if (estado.todas != null && tarjeta == null) {
        LaunchedEffect(Unit) { alVolver() }
        return
    }
    tarjeta ?: return
    val posicion = lista.indexOfFirst { it.id == tarjeta.id }

    var confirmarBorrar by rememberSaveable { mutableStateOf(false) }
    var editandoTexto by rememberSaveable { mutableStateOf(false) }
    var anadiendoEtiqueta by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding()
            .navigationBarsPadding()
            // Deja sitio al teclado (la app se dibuja de borde a borde y Android no lo hace solo).
            .imePadding(),
    ) {
        // Barra de arriba: volver, «Tarjeta 4 de 18», compartir y borrar.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            Text(
                if (posicion >= 0) stringResource(R.string.tarjeta_numero, posicion + 1, lista.size) else "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            IconButton(onClick = { alCompartir(tarjeta) }) {
                Icon(painterResource(R.drawable.ic_compartir), contentDescription = stringResource(R.string.cd_compartir_tarjeta))
            }
            IconButton(onClick = { confirmarBorrar = true }) {
                Icon(
                    painterResource(R.drawable.ic_eliminar),
                    contentDescription = stringResource(R.string.cd_borrar_tarjeta),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // La tarjeta: libro, frase, color y «Editar texto».
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colorResource(R.color.fondo_tarjeta))
                    .border(BorderStroke(1.dp, colorResource(R.color.borde)), RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (libro != null) {
                        PortadaLibro(libro, Modifier.width(34.dp).height(48.dp), mostrarTitulo = false)
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            libro?.titulo.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val detalle = listOfNotNull(
                            libro?.autor?.takeIf { it.isNotBlank() },
                            tarjeta.capitulo?.takeIf { it.isNotBlank() },
                            paginaDe(tarjeta, libro),
                        ).joinToString(" · ")
                        if (detalle.isNotEmpty()) {
                            Text(
                                detalle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    fraseResaltada(tarjeta),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 32.sp),
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectorColores(tarjeta.color, { estado.cambiarColor(tarjeta, it) }, Modifier.weight(1f))
                }
                // (Una tarjeta con varios subrayados unidos no se edita: cada fragmento es del libro.)
                if (!Fragmentos.esUnida(tarjeta)) {
                    TextButton(onClick = { editandoTexto = true }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.tarjeta_editar_texto))
                    }
                }
            }

            // Mi nota: se guarda sola al escribir (y al salir).
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.tarjeta_mi_nota),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            CampoNota(tarjeta, estado)

            // Etiquetas.
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.tarjeta_etiquetas),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiquetas.de(tarjeta).forEach { etiqueta ->
                    val quitar = stringResource(R.string.cd_quitar_etiqueta, etiqueta)
                    val ver = stringResource(R.string.cd_ver_etiqueta, etiqueta)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(colorResource(R.color.borde).copy(alpha = 0.6f)),
                    ) {
                        // El nombre: lleva a todas las tarjetas con esta etiqueta.
                        Text(
                            etiqueta,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clickable { alEtiqueta(etiqueta) }
                                .clearAndSetSemantics { contentDescription = ver }
                                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        )
                        // La ×: quita la etiqueta de esta tarjeta.
                        Text(
                            "×",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clickable { estado.quitarEtiqueta(tarjeta, etiqueta) }
                                .clearAndSetSemantics { contentDescription = quitar }
                                .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                }
                OutlinedButton(
                    onClick = { anadiendoEtiqueta = true },
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, colorResource(R.color.borde)),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text(stringResource(R.string.tarjeta_anadir_etiqueta), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Fechas.
            Spacer(Modifier.height(22.dp))
            Text(
                textoFechas(tarjeta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Abajo: anterior, «Ir al libro» y siguiente.
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            BotonFlecha(
                icono = R.drawable.ic_volver,
                descripcion = stringResource(R.string.cd_tarjeta_anterior),
                activo = posicion > 0,
                alPulsar = { lista.getOrNull(posicion - 1)?.let { idActual = it.id } },
            )
            Button(
                onClick = { alIrAlLibro(tarjeta) },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.marron)),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_libro_abierto), contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.tarjeta_ir_al_libro), style = MaterialTheme.typography.titleMedium)
            }
            BotonFlecha(
                icono = R.drawable.ic_siguiente,
                descripcion = stringResource(R.string.cd_tarjeta_siguiente),
                activo = posicion in 0 until lista.size - 1,
                alPulsar = { lista.getOrNull(posicion + 1)?.let { idActual = it.id } },
            )
        }
    }

    if (confirmarBorrar) {
        AlertDialog(
            onDismissRequest = { confirmarBorrar = false },
            containerColor = colorResource(R.color.fondo_tarjeta),
            title = { Text(stringResource(R.string.tarjeta_borrar_titulo)) },
            text = { Text(stringResource(R.string.tarjeta_borrar_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrar = false
                    // Se pasa a la siguiente (o a la anterior); si era la última, se vuelve a la lista.
                    val otra = lista.getOrNull(posicion + 1) ?: lista.getOrNull(posicion - 1)
                    estado.eliminar(tarjeta)
                    if (otra != null) idActual = otra.id else alVolver()
                }) { Text(stringResource(R.string.tarjeta_borrar_si), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarBorrar = false }) { Text(stringResource(R.string.boton_cancelar)) }
            },
        )
    }
    if (editandoTexto) {
        DialogoEditarTexto(
            tarjeta = tarjeta,
            alGuardar = { texto, frase ->
                editandoTexto = false
                estado.cambiarTexto(tarjeta, texto, frase)
            },
            alCerrar = { editandoTexto = false },
        )
    }
    if (anadiendoEtiqueta) {
        DialogoEtiqueta(
            existentes = TodasLasEtiquetas.de(estado.todasLasTarjetas),
            yaPuestas = Etiquetas.de(tarjeta).toSet(),
            alGuardar = {
                anadiendoEtiqueta = false
                estado.anadirEtiqueta(tarjeta, it)
            },
            alCerrar = { anadiendoEtiqueta = false },
        )
    }
}

/** La nota de la tarjeta: se escribe aquí mismo y se guarda sola un momento después. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CampoNota(tarjeta: Tarjeta, estado: TarjetasViewModel) {
    var texto by rememberSaveable(tarjeta.id) { mutableStateOf(tarjeta.nota.orEmpty()) }
    val actual by rememberUpdatedState(texto)
    LaunchedEffect(tarjeta.id, texto) {
        delay(800)
        estado.guardarNota(tarjeta, texto)
    }
    // Al salir de la tarjeta (o pasar a otra), se guarda lo escrito.
    DisposableEffect(tarjeta.id) {
        onDispose { estado.guardarNota(tarjeta, actual) }
    }
    // Mientras se escribe, el cuadro se mantiene a la vista encima del teclado.
    val aLaVista = remember { BringIntoViewRequester() }
    var escribiendo by remember { mutableStateOf(false) }
    val tecladoAbierto = WindowInsets.isImeVisible
    LaunchedEffect(escribiendo, tecladoAbierto, texto) {
        if (escribiendo) {
            delay(150) // A que el teclado termine de subir.
            aLaVista.bringIntoView()
        }
    }
    OutlinedTextField(
        value = texto,
        onValueChange = { texto = it.take(2_000) },
        placeholder = { Text(stringResource(R.string.tarjeta_nota_vacia)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 3,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(aLaVista)
            .onFocusChanged { escribiendo = it.isFocused },
    )
    if (texto.isNotBlank()) {
        TextButton(onClick = { texto = "" }) {
            Text(stringResource(R.string.subrayado_borrar_nota), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BotonFlecha(icono: Int, descripcion: String, activo: Boolean, alPulsar: () -> Unit) {
    OutlinedButton(
        onClick = alPulsar,
        enabled = activo,
        shape = CircleShape,
        border = BorderStroke(1.dp, colorResource(R.color.borde)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(56.dp),
    ) {
        Icon(painterResource(icono), contentDescription = descripcion, tint = colorResource(R.color.texto))
    }
}

/** Corregir el texto subrayado y la frase completa. */
@Composable
private fun DialogoEditarTexto(tarjeta: Tarjeta, alGuardar: (String, String) -> Unit, alCerrar: () -> Unit) {
    var texto by rememberSaveable(tarjeta.id) { mutableStateOf(tarjeta.texto) }
    var frase by rememberSaveable(tarjeta.id) { mutableStateOf(tarjeta.contexto ?: tarjeta.texto) }
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        title = { Text(stringResource(R.string.tarjeta_editar_texto)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.tarjeta_editar_explicacion), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text(stringResource(R.string.tarjeta_campo_subrayado)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = frase,
                    onValueChange = { frase = it },
                    label = { Text(stringResource(R.string.tarjeta_campo_frase)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = texto.isNotBlank(), onClick = { alGuardar(texto, frase) }) {
                Text(stringResource(R.string.boton_guardar))
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_cancelar)) } },
    )
}

/** «Creada el 12 de noviembre · editada hoy», con el formato del idioma del móvil. */
@Composable
private fun textoFechas(tarjeta: Tarjeta): String {
    val creada = if (DateUtils.isToday(tarjeta.fechaCreacion)) {
        stringResource(R.string.tarjeta_creada_hoy)
    } else {
        stringResource(R.string.tarjeta_creada, fecha(tarjeta.fechaCreacion))
    }
    if (tarjeta.fechaModificacion - tarjeta.fechaCreacion < 60_000) return creada
    val editada = if (DateUtils.isToday(tarjeta.fechaModificacion)) {
        stringResource(R.string.tarjeta_editada_hoy)
    } else {
        stringResource(R.string.tarjeta_editada, fecha(tarjeta.fechaModificacion))
    }
    return stringResource(R.string.tarjeta_fechas, creada, editada)
}

/** La fecha con el formato del idioma de la app («12 de noviembre de 2026»). */
@Composable
private fun fecha(milisegundos: Long): String = org.libertalia.reader.ui.ajustes.AjustesApp.fecha(LocalContext.current, milisegundos)
