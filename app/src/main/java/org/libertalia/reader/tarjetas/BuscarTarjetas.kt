package org.libertalia.reader.tarjetas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.ui.biblioteca.PortadaLibro
import java.text.Normalizer

/** Buscar dentro de las tarjetas: sin distinguir mayúsculas ni tildes («corazon» encuentra «Corazón»). */
object BuscarTexto {

    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Si la tarjeta contiene la búsqueda en su texto, su frase, su nota o sus etiquetas. */
    fun coincide(tarjeta: Tarjeta, consulta: String): Boolean {
        val buscada = normalizar(consulta.trim())
        if (buscada.isEmpty()) return true
        return listOfNotNull(tarjeta.texto, tarjeta.contexto, tarjeta.nota, tarjeta.etiquetas)
            .any { normalizar(it).contains(buscada) }
    }
}

/**
 * Buscar en las tarjetas de todos los libros, desde la lupa de la biblioteca. Los resultados
 * salen agrupados por libro; tocar uno abre esa tarjeta.
 */
@Composable
fun PantallaBuscarTarjetas(
    libros: List<Libro>,
    tarjetas: List<Tarjeta>?,
    alVolver: () -> Unit,
    alAbrir: (Tarjeta) -> Unit,
) {
    var consulta by rememberSaveable { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    // Solo las tarjetas de libros que siguen en la biblioteca, agrupadas por libro.
    val porLibro = remember(consulta, tarjetas, libros) {
        if (consulta.trim().length < 2) {
            emptyList()
        } else {
            val librosPorId = libros.associateBy { it.id }
            tarjetas.orEmpty()
                .filter { it.idLibro in librosPorId && BuscarTexto.coincide(it, consulta) }
                .groupBy { it.idLibro }
                .mapNotNull { (id, lista) -> librosPorId[id]?.let { it to lista } }
                .sortedBy { it.first.titulo.lowercase() }
        }
    }
    val total = porLibro.sumOf { it.second.size }

    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            OutlinedTextField(
                value = consulta,
                onValueChange = { consulta = it },
                placeholder = { Text(stringResource(R.string.buscar_tarjetas_pista)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_buscar), contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).focusRequester(foco),
            )
        }
        when {
            consulta.trim().length < 2 -> Mensaje(stringResource(R.string.buscar_tarjetas_explicacion))
            total == 0 -> Mensaje(stringResource(R.string.buscar_tarjetas_nada))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                item {
                    Text(
                        pluralStringResource(R.plurals.buscar_tarjetas_encontradas, total, total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                porLibro.forEach { (libro, lista) ->
                    // Cabecera de cada libro: portada pequeña, título y número de resultados.
                    item(key = "libro-${libro.id}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        ) {
                            PortadaLibro(libro, Modifier.width(30.dp).height(44.dp), mostrarTitulo = false)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    libro.titulo,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    pluralStringResource(R.plurals.numero_tarjetas, lista.size, lista.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(lista, key = { it.id }) { tarjeta -> Resultado(tarjeta, alPulsar = { alAbrir(tarjeta) }) }
                }
            }
        }
    }
}

@Composable
private fun Mensaje(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

/** Un resultado: la frase con el subrayado y, si la tiene, la nota. */
@Composable
private fun Resultado(tarjeta: Tarjeta, alPulsar: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colorResource(R.color.fondo_tarjeta))
            .border(BorderStroke(1.dp, colorResource(R.color.borde)), RoundedCornerShape(14.dp))
            .clickable(onClick = alPulsar)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            tarjeta.color?.let {
                androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(CircleShape).background(colorResource(it.color)))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                tarjeta.capitulo.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(fraseResaltada(tarjeta, recortar = true), style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis)
        tarjeta.nota?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
