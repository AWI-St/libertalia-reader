package org.libertalia.reader.ui.ajustes

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.R
import java.security.MessageDigest

/** Un archivo de la carpeta Libertalia que no usa ningún libro de la biblioteca. */
data class ArchivoSobrante(
    val uri: Uri,
    val nombre: String,
    val tamano: Long,
    /** Título del libro del que es una copia exacta (o el nombre del otro archivo igual); null si no es copia de nada. */
    val copiaDe: String?,
)

/** En qué punto está la búsqueda. */
sealed interface EstadoLimpieza {
    data class Buscando(val progreso: Float) : EstadoLimpieza
    data class Lista(val archivos: List<ArchivoSobrante>) : EstadoLimpieza
    data object Borrando : EstadoLimpieza
    data class Hecho(val liberado: Long, val fallos: Int) : EstadoLimpieza
}

/**
 * «Liberar espacio»: revisa la carpeta Libertalia. Nunca toca el archivo de un libro de la
 * biblioteca. De los demás, los que tienen el mismo contenido que un libro o que otro archivo
 * suelto son «repetidos»; el resto «no son de ningún libro» (pueden ser cosas del usuario).
 */
class LiberarEspacioViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as LibertaliaApp

    var estado by mutableStateOf<EstadoLimpieza>(EstadoLimpieza.Buscando(0f))
        private set

    /** Los marcados para borrar (por defecto, solo los repetidos). */
    var marcados by mutableStateOf<Set<Uri>>(emptySet())
        private set

    init {
        buscar()
    }

    fun alternar(uri: Uri) {
        marcados = if (uri in marcados) marcados - uri else marcados + uri
    }

    private fun buscar() {
        viewModelScope.launch {
            val sobrantes = withContext(Dispatchers.IO) {
                val carpeta = app.carpeta.carpeta() ?: return@withContext emptyList()
                val libros = app.baseDatos.libros().todos()
                // Los archivos que usan los libros (se comparan por su identificador de documento).
                val usados = libros.mapNotNull { l -> l.uriArchivo?.let { idDocumento(Uri.parse(it)) } }.toSet()
                val porHuella = libros.associate { it.huella to it.titulo }
                val sueltos = carpeta.listFiles().filter { it.isFile && idDocumento(it.uri) !in usados }
                val total = sueltos.sumOf { it.length() }.coerceAtLeast(1)
                var hecho = 0L
                val huellas = sueltos.associateWith { archivo ->
                    huellaDe(archivo.uri) { n ->
                        hecho += n
                        estado = EstadoLimpieza.Buscando((hecho.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
                // Repetido: igual que un libro, o igual que otro archivo suelto (se deja el primero).
                val vistas = mutableMapOf<String, String>()
                sueltos.sortedBy { it.name.orEmpty().length }.map { archivo ->
                    val huella = huellas[archivo]
                    val copiaDe = huella?.let { porHuella[it] ?: vistas[it] }
                    if (huella != null && copiaDe == null) vistas[huella] = archivo.name.orEmpty()
                    ArchivoSobrante(archivo.uri, archivo.name.orEmpty(), archivo.length(), copiaDe)
                }.sortedWith(compareBy({ it.copiaDe == null }, { -it.tamano }))
            }
            marcados = sobrantes.filter { it.copiaDe != null }.map { it.uri }.toSet()
            estado = EstadoLimpieza.Lista(sobrantes)
        }
    }

    fun borrar() {
        val lista = (estado as? EstadoLimpieza.Lista)?.archivos ?: return
        val elegidos = lista.filter { it.uri in marcados }
        estado = EstadoLimpieza.Borrando
        viewModelScope.launch {
            var liberado = 0L
            var fallos = 0
            withContext(Dispatchers.IO) {
                for (a in elegidos) {
                    val borrado = runCatching { DocumentsContract.deleteDocument(app.contentResolver, a.uri) }.getOrDefault(false)
                    if (borrado) liberado += a.tamano else fallos++
                }
            }
            estado = EstadoLimpieza.Hecho(liberado, fallos)
        }
    }

    private fun idDocumento(uri: Uri): String? = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()

    private fun huellaDe(uri: Uri, alLeer: (Long) -> Unit): String? = runCatching {
        val resumen = MessageDigest.getInstance("SHA-256")
        app.contentResolver.openInputStream(uri)?.use { entrada ->
            val bufer = ByteArray(256 * 1024)
            while (true) {
                val leidos = entrada.read(bufer)
                if (leidos < 0) break
                resumen.update(bufer, 0, leidos)
                alLeer(leidos.toLong())
            }
        } ?: return null
        resumen.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

/** Pantalla «Liberar espacio». */
@Composable
fun PantallaLiberarEspacio(alVolver: () -> Unit) {
    val estado: LiberarEspacioViewModel = viewModel()
    val contexto = LocalContext.current
    var confirmar by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = alVolver, enabled = estado.estado !is EstadoLimpieza.Borrando) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            Text(stringResource(R.string.ajustes_liberar_espacio), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.limpieza_explicacion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))

        when (val e = estado.estado) {
            is EstadoLimpieza.Buscando -> Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.limpieza_buscando, (e.progreso * 100).toInt()), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { e.progreso },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = colorResource(R.color.borde),
                    drawStopIndicator = {},
                )
            }
            EstadoLimpieza.Borrando -> Text(stringResource(R.string.limpieza_borrando), modifier = Modifier.padding(24.dp))
            is EstadoLimpieza.Hecho -> Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.limpieza_hecho, Formatter.formatShortFileSize(contexto, e.liberado)),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (e.fallos > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pluralStringResource(R.plurals.limpieza_fallos, e.fallos, e.fallos),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorResource(R.color.rojo),
                    )
                }
            }
            is EstadoLimpieza.Lista -> if (e.archivos.isEmpty()) {
                Text(stringResource(R.string.limpieza_nada), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
            } else {
                val elegidos = e.archivos.filter { it.uri in estado.marcados }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(e.archivos.size) { i ->
                        val a = e.archivos[i]
                        val forma = RoundedCornerShape(14.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(forma)
                                .background(colorResource(R.color.fondo_tarjeta))
                                .border(1.dp, colorResource(R.color.borde), forma)
                                .clickable(role = Role.Checkbox) { estado.alternar(a.uri) }
                                .heightIn(min = 60.dp)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Checkbox(
                                checked = a.uri in estado.marcados,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(
                                    Formatter.formatShortFileSize(contexto, a.tamano) + " · " +
                                        (a.copiaDe?.let { stringResource(R.string.limpieza_copia_de, it) } ?: stringResource(R.string.limpieza_ningun_libro)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = colorResource(R.color.borde))
                Button(
                    onClick = { confirmar = true },
                    enabled = elegidos.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.rojo)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.limpieza_borrar,
                            elegidos.size,
                            elegidos.size,
                            Formatter.formatShortFileSize(contexto, elegidos.sumOf { it.tamano }),
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
                if (confirmar) {
                    AlertDialog(
                        onDismissRequest = { confirmar = false },
                        containerColor = colorResource(R.color.fondo_tarjeta),
                        title = { Text(pluralStringResource(R.plurals.limpieza_confirmar_titulo, elegidos.size, elegidos.size)) },
                        text = { Text(stringResource(R.string.limpieza_confirmar_texto)) },
                        confirmButton = {
                            TextButton(onClick = { confirmar = false; estado.borrar() }) {
                                Text(stringResource(R.string.originales_borrar), color = colorResource(R.color.rojo))
                            }
                        },
                        dismissButton = { TextButton(onClick = { confirmar = false }) { Text(stringResource(R.string.boton_cancelar)) } },
                    )
                }
            }
        }
    }
}
