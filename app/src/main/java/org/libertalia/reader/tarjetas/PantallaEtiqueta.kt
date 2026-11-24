package org.libertalia.reader.tarjetas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Tarjeta

/**
 * Las etiquetas que se usan en un grupo de tarjetas, con cuántas tarjetas tiene cada una:
 * de la más usada a la menos (y, si empatan, por orden alfabético).
 */
object TodasLasEtiquetas {
    fun de(tarjetas: List<Tarjeta>): List<Pair<String, Int>> =
        tarjetas.flatMap { Etiquetas.de(it) }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { BuscarTexto.normalizar(it.first) })
}

/**
 * Todas las tarjetas (de todos los libros) que tienen una etiqueta. Cada una lleva arriba
 * el título de su libro, para saber de cuál es. Están ordenadas por libro y, dentro de
 * cada libro, en el orden del libro.
 */
@Composable
fun PantallaEtiqueta(
    etiqueta: String,
    libros: List<Libro>,
    tarjetas: List<Tarjeta>,
    alVolver: () -> Unit,
    alAbrir: (Tarjeta) -> Unit,
    alEtiqueta: (String) -> Unit,
) {
    val librosPorId = remember(libros) { libros.associateBy { it.id } }
    val conEtiqueta = remember(etiqueta, tarjetas, librosPorId) {
        tarjetas
            .filter { it.idLibro in librosPorId && etiqueta in Etiquetas.de(it) }
            .sortedWith(compareBy({ librosPorId[it.idLibro]?.titulo?.lowercase() }, { it.progreso }))
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.etiqueta_titulo, etiqueta),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.numero_tarjetas, conEtiqueta.size, conEtiqueta.size) + " · " +
                        stringResource(R.string.etiqueta_de_todos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        if (conEtiqueta.isEmpty()) {
            Text(
                stringResource(R.string.etiqueta_vacia),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(28.dp),
            )
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(conEtiqueta, key = { it.id }) { tarjeta ->
                    TarjetaPequena(
                        tarjeta = tarjeta,
                        libro = librosPorId[tarjeta.idLibro],
                        alPulsar = { alAbrir(tarjeta) },
                        alEtiqueta = { if (it != etiqueta) alEtiqueta(it) },
                        conTitulo = true,
                    )
                }
            }
        }
    }
}

/**
 * Buscador de etiquetas (modo Tarjetas de la biblioteca): un campo para escribir y debajo
 * las etiquetas que coinciden, con cuántas tarjetas tiene cada una. Tocar una lleva a todas
 * sus tarjetas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuscadorEtiquetas(tarjetas: List<Tarjeta>, alElegir: (String) -> Unit, modifier: Modifier = Modifier) {
    val todas = remember(tarjetas) { TodasLasEtiquetas.de(tarjetas) }
    if (todas.isEmpty()) return
    var consulta by rememberSaveable { mutableStateOf("") }
    val buscada = BuscarTexto.normalizar(consulta.trim())
    val visibles = todas.filter { buscada.isEmpty() || BuscarTexto.normalizar(it.first).contains(buscada) }
    Column(modifier) {
        OutlinedTextField(
            value = consulta,
            onValueChange = { consulta = it },
            placeholder = { Text(stringResource(R.string.buscar_etiqueta_pista)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_buscar), contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (visibles.isEmpty()) {
            Text(
                stringResource(R.string.buscar_etiqueta_nada),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, start = 8.dp),
            )
        } else {
            // Como mucho 3 filas (para no ocupar toda la pantalla); si hay más etiquetas,
            // se deslizan hacia los lados. Con pocas, menos filas. Se leen por filas, de
            // izquierda a derecha: la primera fila tiene las más usadas.
            val filas = when {
                visibles.size <= 4 -> 1
                visibles.size <= 9 -> 2
                else -> 3
            }
            val porFila = (visibles.size + filas - 1) / filas
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                visibles.chunked(porFila).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fila.forEach { (etiqueta, numero) ->
                            EtiquetaPequena("$etiqueta · $numero", alPulsar = { alElegir(etiqueta) })
                        }
                    }
                }
            }
        }
    }
}
