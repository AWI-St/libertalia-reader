package org.libertalia.reader.ui.copias

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import org.libertalia.reader.copias.ImportarViewModel

/**
 * La pantalla «Importar una copia» para el archivo [uri] (elegido en Ajustes o en la
 * biblioteca vacía, o recibido con «Abrir con»).
 */
@Composable
fun RutaImportar(uri: Uri, alVolver: () -> Unit, alTerminar: () -> Unit) {
    val estado: ImportarViewModel = viewModel()
    LaunchedEffect(uri) { estado.iniciar(uri) }
    PantallaImportar(
        estado = estado.estado,
        alImportar = estado::importar,
        alVolver = alVolver,
        alTerminar = alTerminar,
        copiaBorrada = estado.copiaBorrada,
        alBorrarCopia = estado::borrarCopia,
    )
}
