package org.libertalia.reader.ui.ajustes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R

/** Una librería o tipografía incluida en la app, con su licencia. Los nombres propios no se traducen. */
private data class Licencia(val nombre: String, val autor: String, val licencia: String, val nota: Int? = null)

/** Todo lo que lleva la app de otros y su licencia (todas permiten incluirlas en la app). */
private val LICENCIAS = listOf(
    Licencia("Readium Kotlin Toolkit", "Readium Foundation", "BSD 3-Clause"),
    Licencia("PdfiumAndroid", "Barteksc, marain87 et al.", "Apache 2.0"),
    Licencia("PDFium", "The PDFium Authors (Google)", "BSD 3-Clause"),
    Licencia("PdfBox-Android", "Tom Roush · Apache PDFBox", "Apache 2.0"),
    Licencia("Bouncy Castle", "Legion of the Bouncy Castle", "MIT"),
    Licencia("junrar", "junrar contributors · RARLAB", "UnRAR", R.string.acerca_unrar),
    Licencia("Jetpack Compose, Room, Navigation, Glance, WorkManager, AppCompat", "Google · AOSP", "Apache 2.0"),
    Licencia("Media3 (ExoPlayer, MediaSession)", "Google · AOSP", "Apache 2.0"),
    Licencia("Kotlin, kotlinx.coroutines", "JetBrains", "Apache 2.0"),
    Licencia("Literata", "TypeTogether para Google", "SIL Open Font License 1.1"),
    Licencia("Lora", "Cyreal", "SIL Open Font License 1.1"),
    Licencia("Atkinson Hyperlegible", "Braille Institute of America", "SIL Open Font License 1.1"),
    Licencia("OpenDyslexic", "Abbie Gonzalez", "SIL Open Font License 1.1"),
)

/** «Acerca de Libertalia Reader»: icono, versión, la filosofía de la app y las licencias. */
@Composable
fun PantallaAcercaDe(alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val version = runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName }.getOrNull().orEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.icono_libertalia), contentDescription = null, modifier = Modifier.size(112.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.acerca_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.acerca_filosofia), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        Bloque(stringResource(R.string.acerca_licencias)) {
            LICENCIAS.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = colorResource(R.color.borde))
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(l.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(l.autor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(l.licencia, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    l.nota?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Text(
                stringResource(R.string.acerca_licencias_nota),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
