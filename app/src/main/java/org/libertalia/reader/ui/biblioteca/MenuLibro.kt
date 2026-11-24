package org.libertalia.reader.ui.biblioteca

import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libertalia.reader.R
import org.libertalia.reader.datos.EstadoLectura
import org.libertalia.reader.datos.Estanteria
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.datos.TipoLibro
import org.libertalia.reader.comic.SentidoLectura
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.input.KeyboardType

/** Lo que se puede hacer con un libro desde su menú. */
data class AccionesLibro(
    val cambiarEstado: (Libro, EstadoLectura) -> Unit,
    val guardarDatos: (libro: Libro, titulo: String, autor: String, portada: Uri?, renombrar: Boolean) -> Unit,
    val crearEstanteria: (nombre: String, conLibro: Libro?) -> Unit,
    val ponerEnEstanteria: (Libro, Estanteria, dentro: Boolean) -> Unit,
    val eliminar: (Libro, borrarArchivo: Boolean) -> Unit,
    val cambiarTomo: (Libro, Int?) -> Unit = { _, _ -> },
    val cambiarSentido: (Libro, String) -> Unit = { _, _ -> },
    val cambiarTipo: (Libro, TipoLibro) -> Unit = { _, _ -> },
    /** Si su archivo está en la carpeta de la app (los cómics se quedan donde estaban). */
    val estaEnCarpeta: (Libro) -> Boolean = { true },
    /** Si su archivo está en una nube (Drive...): se ofrece guardarlo en el móvil. */
    val estaEnLaNube: (Libro) -> Boolean = { false },
    val guardarEnMovil: (Libro) -> Unit = {},
    /** Abrir las tarjetas (subrayados) del libro. */
    val verTarjetas: (Libro) -> Unit = {},
    /** Abrir la pantalla Exportar con solo este libro marcado. */
    val exportar: (Libro) -> Unit = {},
    /** «Incluir el archivo en la copia automática». */
    val cambiarIncluirEnCopia: (Libro, Boolean) -> Unit = { _, _ -> },
)

private enum class Dialogo { EDITAR, ESTADO, ESTANTERIAS, ELIMINAR, SENTIDO }

/** Texto del estado de un libro («Leyendo», «Pendiente», «Terminado»). */
@Composable
fun textoEstado(estado: EstadoLectura): String = stringResource(
    when (estado) {
        EstadoLectura.LEYENDO -> R.string.estado_libro_leyendo
        EstadoLectura.PENDIENTE -> R.string.estado_libro_pendiente
        EstadoLectura.TERMINADO -> R.string.estado_libro_terminado
    }
)

/**
 * Menú que sube desde abajo al mantener pulsado un libro (boceto 5).
 * Cada opción abre su propio diálogo; al cerrarlo, se cierra también el menú.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuLibro(
    libro: Libro,
    estanterias: List<Estanteria>,
    estanteriasDelLibro: Set<String>,
    acciones: AccionesLibro,
    alCerrar: () -> Unit,
) {
    var dialogo by rememberSaveable { mutableStateOf<Dialogo?>(null) }

    when (dialogo) {
        null -> ModalBottomSheet(
            onDismissRequest = alCerrar,
            containerColor = colorResource(R.color.fondo_lectura),
            contentColor = colorResource(R.color.texto),
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                CabeceraMenu(libro)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                if (libro.tipo == TipoLibro.TEXTO) {
                    OpcionMenu(R.drawable.ic_tarjetas, stringResource(R.string.menu_ver_tarjetas)) {
                        acciones.verTarjetas(libro)
                        alCerrar()
                    }
                }
                OpcionMenu(R.drawable.ic_editar, stringResource(R.string.menu_editar)) { dialogo = Dialogo.EDITAR }
                OpcionMenu(R.drawable.ic_estado, stringResource(R.string.menu_cambiar_estado)) { dialogo = Dialogo.ESTADO }
                OpcionMenu(R.drawable.ic_estanteria, stringResource(R.string.menu_anadir_estanteria)) {
                    dialogo = Dialogo.ESTANTERIAS
                }
                if (libro.tipo == TipoLibro.COMIC) {
                    OpcionMenu(R.drawable.ic_sentido, stringResource(R.string.menu_sentido_lectura)) {
                        dialogo = Dialogo.SENTIDO
                    }
                }
                if (acciones.estaEnLaNube(libro)) {
                    OpcionMenu(R.drawable.ic_descargar, stringResource(R.string.menu_guardar_en_movil)) {
                        acciones.guardarEnMovil(libro)
                        alCerrar()
                    }
                }
                // Un PDF puede ser un libro de texto o un cómic escaneado: se puede cambiar.
                if (libro.formato == Formato.PDF) {
                    val esComic = libro.tipo == TipoLibro.COMIC
                    OpcionMenu(
                        R.drawable.ic_comic,
                        stringResource(if (esComic) R.string.menu_tratar_como_libro else R.string.menu_tratar_como_comic),
                    ) {
                        acciones.cambiarTipo(libro, if (esComic) TipoLibro.TEXTO else TipoLibro.COMIC)
                        alCerrar()
                    }
                }
                // Interruptor: si el archivo va en la copia automática (los datos van siempre).
                var incluir by remember(libro.id) { mutableStateOf(libro.incluirArchivoEnCopia) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = androidx.compose.ui.semantics.Role.Switch) {
                            incluir = !incluir
                            acciones.cambiarIncluirEnCopia(libro, incluir)
                        }
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_reloj), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.menu_incluir_en_copia), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            android.text.format.Formatter.formatShortFileSize(LocalContext.current, libro.tamanoBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = incluir,
                        onCheckedChange = {
                            incluir = it
                            acciones.cambiarIncluirEnCopia(libro, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
                OpcionMenu(R.drawable.ic_exportar, stringResource(R.string.menu_exportar)) {
                    acciones.exportar(libro)
                    alCerrar()
                }
                OpcionMenu(
                    R.drawable.ic_eliminar,
                    stringResource(R.string.menu_eliminar),
                    color = MaterialTheme.colorScheme.error,
                ) { dialogo = Dialogo.ELIMINAR }
            }
        }
        Dialogo.EDITAR -> DialogoEditar(libro, acciones, alCerrar)
        Dialogo.ESTADO -> DialogoEstado(libro, acciones, alCerrar)
        Dialogo.ESTANTERIAS -> DialogoEstanterias(libro, estanterias, estanteriasDelLibro, acciones, alCerrar)
        Dialogo.ELIMINAR -> DialogoEliminar(libro, acciones, alCerrar)
        Dialogo.SENTIDO -> DialogoSentido(libro, acciones, alCerrar)
    }
}

@Composable
private fun CabeceraMenu(libro: Libro) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortadaLibro(libro, Modifier.width(52.dp).height(76.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = libro.titulo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val estado = textoEstado(libro.estado)
            Text(
                text = if (libro.autor.isBlank()) estado else stringResource(R.string.menu_subtitulo, libro.autor, estado),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OpcionMenu(
    @DrawableRes icono: Int,
    texto: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    alPulsar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = alPulsar)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icono), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(20.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/** Editar título, autor y portada, con la opción de renombrar el archivo. */
@Composable
private fun DialogoEditar(libro: Libro, acciones: AccionesLibro, alCerrar: () -> Unit) {
    var titulo by rememberSaveable { mutableStateOf(libro.titulo) }
    var autor by rememberSaveable { mutableStateOf(libro.autor) }
    var portadaNueva by rememberSaveable { mutableStateOf<Uri?>(null) }
    var renombrar by rememberSaveable { mutableStateOf(false) }
    var tomo by rememberSaveable { mutableStateOf(libro.tomo?.toString().orEmpty()) }
    val elegirImagen = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) portadaNueva = uri
    }
    val tituloCambiado = titulo.trim().isNotEmpty() && titulo.trim() != libro.titulo

    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.editar_titulo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text(stringResource(R.string.campo_titulo)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = autor,
                    onValueChange = { autor = it },
                    label = { Text(stringResource(R.string.campo_autor)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (libro.tipo == TipoLibro.COMIC) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tomo,
                        onValueChange = { nuevo -> tomo = nuevo.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.campo_tomo)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val modificador = Modifier.width(56.dp).height(82.dp).clip(RoundedCornerShape(4.dp))
                    val elegida = portadaNueva
                    if (elegida != null) ImagenElegida(elegida, modificador) else PortadaLibro(libro, modificador)
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = {
                            elegirImagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_imagen), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cambiar_portada))
                    }
                }
                if (tituloCambiado && libro.uriArchivo != null && acciones.estaEnCarpeta(libro)) {
                    Spacer(Modifier.height(12.dp))
                    FilaCasilla(
                        marcada = renombrar,
                        alCambiar = { renombrar = it },
                        texto = stringResource(R.string.renombrar_archivo),
                        nota = stringResource(R.string.renombrar_archivo_nota),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                acciones.guardarDatos(libro, titulo, autor, portadaNueva, renombrar && tituloCambiado)
                if (libro.tipo == TipoLibro.COMIC) acciones.cambiarTomo(libro, tomo.toIntOrNull())
                alCerrar()
            }) { Text(stringResource(R.string.boton_guardar)) }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_cancelar)) }
        },
    )
}

/** Vista previa de la imagen elegida en la galería. */
@Composable
private fun ImagenElegida(uri: Uri, modifier: Modifier) {
    val contexto = LocalContext.current
    val imagen: ImageBitmap? by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opciones = BitmapFactory.Options().apply { inSampleSize = 4 }
                contexto.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opciones)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    imagen?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier) }
}

/** Elegir entre Leyendo, Pendiente y Terminado. */
@Composable
private fun DialogoEstado(libro: Libro, acciones: AccionesLibro, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.menu_cambiar_estado)) },
        text = {
            Column {
                EstadoLectura.entries.forEach { estado ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                acciones.cambiarEstado(libro, estado)
                                alCerrar()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = libro.estado == estado, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(textoEstado(estado), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_cancelar)) } },
    )
}

/** Marcar en qué estanterías está el libro, y crear estanterías nuevas. */
@Composable
private fun DialogoEstanterias(
    libro: Libro,
    estanterias: List<Estanteria>,
    estanteriasDelLibro: Set<String>,
    acciones: AccionesLibro,
    alCerrar: () -> Unit,
) {
    var nueva by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.menu_anadir_estanteria)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (estanterias.isEmpty()) {
                    Text(
                        stringResource(R.string.estanterias_vacio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                estanterias.forEach { estanteria ->
                    val dentro = estanteria.id in estanteriasDelLibro
                    FilaCasilla(
                        marcada = dentro,
                        alCambiar = { acciones.ponerEnEstanteria(libro, estanteria, it) },
                        texto = estanteria.nombre,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nueva,
                        onValueChange = { nueva = it },
                        label = { Text(stringResource(R.string.nueva_estanteria)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = nueva.isNotBlank(),
                        onClick = {
                            acciones.crearEstanteria(nueva, libro)
                            nueva = ""
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.boton_crear)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_listo)) } },
    )
}

/** Confirmar antes de eliminar; el archivo solo se borra si se marca la casilla. */
@Composable
private fun DialogoEliminar(libro: Libro, acciones: AccionesLibro, alCerrar: () -> Unit) {
    var borrarArchivo by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.eliminar_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.eliminar_mensaje, libro.titulo), style = MaterialTheme.typography.bodyLarge)
                if (libro.uriArchivo != null && acciones.estaEnCarpeta(libro)) {
                    Spacer(Modifier.height(12.dp))
                    FilaCasilla(
                        marcada = borrarArchivo,
                        alCambiar = { borrarArchivo = it },
                        texto = stringResource(R.string.eliminar_archivo),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                acciones.eliminar(libro, borrarArchivo)
                alCerrar()
            }) {
                Text(stringResource(R.string.boton_eliminar), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_cancelar)) } },
    )
}

/** Sentido de lectura de un cómic: de izquierda a derecha o de derecha a izquierda (manga). */
@Composable
private fun DialogoSentido(libro: Libro, acciones: AccionesLibro, alCerrar: () -> Unit) {
    val actual = libro.sentidoLectura ?: SentidoLectura.IZQUIERDA_A_DERECHA
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.menu_sentido_lectura)) },
        text = {
            Column {
                listOf(
                    SentidoLectura.IZQUIERDA_A_DERECHA to R.string.sentido_izquierda_derecha,
                    SentidoLectura.DERECHA_A_IZQUIERDA to R.string.sentido_derecha_izquierda,
                ).forEach { (sentido, texto) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                acciones.cambiarSentido(libro, sentido)
                                alCerrar()
                            },
                    ) {
                        RadioButton(selected = actual == sentido, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(texto), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.boton_cancelar)) } },
    )
}

/** Casilla con su texto; toda la fila se puede tocar. */
@Composable
private fun FilaCasilla(marcada: Boolean, alCambiar: (Boolean) -> Unit, texto: String, nota: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { alCambiar(!marcada) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = marcada,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(texto, style = MaterialTheme.typography.bodyLarge)
            if (nota != null) {
                Text(nota, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
