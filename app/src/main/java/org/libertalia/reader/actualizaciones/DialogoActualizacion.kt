package org.libertalia.reader.actualizaciones

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import org.libertalia.reader.R

/** Cómo ha ido «Buscar ahora» en Ajustes. */
sealed interface BusquedaActualizacion {
    data object Buscando : BusquedaActualizacion
    data object AlDia : BusquedaActualizacion
    data object Error : BusquedaActualizacion
    data class Nueva(val version: VersionNueva) : BusquedaActualizacion

    /** Hay versión nueva y ya se ha respondido al aviso. */
    data class Avisada(val version: VersionNueva) : BusquedaActualizacion
}

/**
 * «Hay una versión nueva»: «Descargar» abre el APK de GitHub en el navegador (Android lo
 * descarga y, al abrirlo, lo instala encima de la versión actual, sin perder nada).
 */
@Composable
fun DialogoActualizacion(version: VersionNueva, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.actualizacion_titulo)) },
        text = {
            Text(stringResource(R.string.actualizacion_texto, version.version, Actualizaciones.versionActual(contexto)))
        },
        confirmButton = {
            TextButton(onClick = {
                val direccion = version.urlApk ?: version.urlPagina
                runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(direccion))) }
                alCerrar()
            }) { Text(stringResource(R.string.actualizacion_descargar)) }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) { Text(stringResource(R.string.actualizacion_ahora_no)) }
        },
    )
}
