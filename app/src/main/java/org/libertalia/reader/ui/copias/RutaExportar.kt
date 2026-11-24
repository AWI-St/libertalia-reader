package org.libertalia.reader.ui.copias

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.libertalia.reader.R
import org.libertalia.reader.copias.EventoExportar
import org.libertalia.reader.copias.ExportarViewModel

/**
 * La pantalla Exportar con lo que necesita de Android: el selector para «Guardar en…»
 * (móvil, Drive u otra nube) y el menú de compartir.
 *
 * [idInicial]: el libro que viene marcado (desde su menú); null = todos.
 */
@Composable
fun RutaExportar(idInicial: String?, alVolver: () -> Unit) {
    val estado: ExportarViewModel = viewModel()
    val contexto = LocalContext.current
    val libros by estado.libros.collectAsStateWithLifecycle()
    val conteo by estado.conteoTarjetas.collectAsStateWithLifecycle()

    LaunchedEffect(libros) { libros?.let { estado.iniciar(it, idInicial) } }
    // Títulos cuyo archivo no se pudo meter en la copia (para avisar).
    var sinArchivo by remember { mutableStateOf<List<String>>(emptyList()) }

    // «Guardar en…»: el selector de Android crea el archivo donde diga el usuario.
    val guardarEn = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) estado.guardarEn(uri) }

    LaunchedEffect(Unit) {
        estado.eventos.collect { evento ->
            when (evento) {
                is EventoExportar.Compartir -> {
                    val uri = FileProvider.getUriForFile(contexto, contexto.packageName + ".archivos", evento.archivo)
                    val enviar = Intent(Intent.ACTION_SEND)
                        .setType("application/octet-stream")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    enviar.clipData = ClipData.newRawUri(evento.archivo.name, uri)
                    contexto.startActivity(Intent.createChooser(enviar, contexto.getString(R.string.exportar_compartir_titulo)))
                }
                is EventoExportar.Guardada -> {
                    // Si algún archivo no se pudo leer, se avisa con un cuadro que no se va solo.
                    if (evento.resumen.sinArchivo.isEmpty()) {
                        Toast.makeText(contexto, R.string.exportar_guardada, Toast.LENGTH_LONG).show()
                    } else {
                        sinArchivo = evento.resumen.sinArchivo
                    }
                }
                EventoExportar.Error -> Toast.makeText(contexto, R.string.exportar_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Mientras se prepara la copia no se sale (se perdería a medias).
    BackHandler(enabled = estado.progreso != null) {}

    if (sinArchivo.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { sinArchivo = emptyList() },
            containerColor = colorResource(R.color.fondo_tarjeta),
            titleContentColor = colorResource(R.color.texto),
            textContentColor = colorResource(R.color.texto),
            title = { Text(stringResource(R.string.exportar_sin_archivo_titulo)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.exportar_sin_archivo, sinArchivo.size, sinArchivo.size) +
                        "\n\n" + sinArchivo.joinToString("\n") { "· $it" },
                )
            },
            confirmButton = {
                TextButton(onClick = { sinArchivo = emptyList() }) { Text(stringResource(R.string.boton_entendido)) }
            },
        )
    }

    val lista = libros ?: return
    PantallaExportar(
        libros = lista,
        conteoTarjetas = conteo,
        seleccion = estado.seleccion.orEmpty(),
        incluirArchivos = estado.incluirArchivos,
        progreso = estado.progreso,
        pesoEstimado = estado::pesoEstimado,
        alCambiarIncluirArchivos = { estado.incluirArchivos = it },
        alAlternar = estado::alternar,
        alSeleccionarTodos = estado::seleccionarTodos,
        alGuardarEn = { guardarEn.launch(estado.nombreSugerido()) },
        alCompartir = estado::compartir,
        alVolver = alVolver,
    )
}
