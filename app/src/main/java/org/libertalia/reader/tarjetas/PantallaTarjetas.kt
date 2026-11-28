package org.libertalia.reader.tarjetas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.libertalia.reader.R
import org.libertalia.reader.datos.ColorSubrayado
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.ui.biblioteca.PortadaLibro

/**
 * Las tarjetas de un libro (boceto 4): dos por fila, en el orden del libro, con filtros
 * por color. Solo subrayados: las páginas destacadas tienen su propia lista.
 */
@Composable
fun PantallaTarjetas(
    estado: TarjetasViewModel,
    alVolver: () -> Unit,
    alAbrir: (Tarjeta) -> Unit,
    /** Tocar una etiqueta: todas las tarjetas (de todos los libros) con esa etiqueta. */
    alEtiqueta: (String) -> Unit = {},
) {
    val libro = estado.libro
    val todas = estado.todas
    var buscando by rememberSaveable { mutableStateOf(estado.consulta.isNotEmpty()) }
    var menuEtiquetas by remember { mutableStateOf(false) }
    var menuExportar by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val foco = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_app))
            .statusBarsPadding(),
    ) {
        // Cabecera: volver, portada, título y número de tarjetas.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            if (libro != null) {
                PortadaLibro(libro, Modifier.width(40.dp).height(58.dp), mostrarTitulo = false)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                Text(
                    libro?.titulo.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (todas != null) {
                    Text(
                        stringResource(
                            R.string.tarjetas_subtitulo,
                            pluralStringResource(R.plurals.numero_tarjetas, todas.size, todas.size),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Buscar dentro de las tarjetas de este libro.
            IconButton(onClick = {
                buscando = !buscando
                if (!buscando) estado.consulta = ""
            }) {
                Icon(painterResource(R.drawable.ic_buscar), contentDescription = stringResource(R.string.cd_buscar_en_tarjetas))
            }
            // Exportar las tarjetas de este libro a Markdown, CSV o Anki.
            if (libro != null && !todas.isNullOrEmpty()) {
                Box {
                    IconButton(onClick = { menuExportar = true }) {
                        Icon(painterResource(R.drawable.ic_exportar), contentDescription = stringResource(R.string.exportar_tarjetas_titulo))
                    }
                    DropdownMenu(expanded = menuExportar, onDismissRequest = { menuExportar = false }) {
                        listOf(
                            FormatoTarjetas.MARKDOWN to R.string.exportar_tarjetas_markdown,
                            FormatoTarjetas.CSV to R.string.exportar_tarjetas_csv,
                            FormatoTarjetas.ANKI to R.string.exportar_tarjetas_anki,
                        ).forEach { (formato, texto) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(texto)) },
                                onClick = {
                                    menuExportar = false
                                    ExportarTarjetas.compartir(contexto, libro, todas, formato)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (buscando) {
            OutlinedTextField(
                value = estado.consulta,
                onValueChange = { estado.consulta = it },
                placeholder = { Text(stringResource(R.string.buscar_en_tarjetas_pista)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_buscar), contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp).focusRequester(foco),
            )
            LaunchedEffect(Unit) { foco.requestFocus() }
        }
        // Filtros: todas, un color o una etiqueta.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            item {
                ChipFiltro(
                    elegido = estado.filtroColor == null && estado.filtroEtiqueta == null,
                    descripcion = stringResource(R.string.tarjetas_todas),
                    alPulsar = {
                        estado.filtroColor = null
                        estado.filtroEtiqueta = null
                    },
                ) {
                    Text(stringResource(R.string.tarjetas_todas), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(ColorSubrayado.entries) { color ->
                ChipFiltro(
                    elegido = estado.filtroColor == color,
                    descripcion = stringResource(R.string.tarjetas_filtro_color, stringResource(color.nombre)),
                    alPulsar = { estado.filtroColor = if (estado.filtroColor == color) null else color },
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(colorResource(color.color)))
                }
            }
            // Etiquetas: un botón que despliega las del libro (solo si hay alguna).
            val etiquetas = estado.etiquetas
            if (etiquetas.isNotEmpty()) {
                item {
                    Box {
                        val elegida = estado.filtroEtiqueta
                        ChipFiltro(
                            elegido = elegida != null,
                            descripcion = stringResource(R.string.tarjetas_filtro_etiquetas),
                            alPulsar = { if (elegida != null) estado.filtroEtiqueta = null else menuEtiquetas = true },
                        ) {
                            Text(
                                if (elegida != null) "$elegida  ×" else stringResource(R.string.tarjetas_etiquetas),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        DropdownMenu(
                            expanded = menuEtiquetas,
                            onDismissRequest = { menuEtiquetas = false },
                            containerColor = colorResource(R.color.fondo_tarjeta),
                        ) {
                            etiquetas.forEach { etiqueta ->
                                DropdownMenuItem(
                                    text = { Text(etiqueta, style = MaterialTheme.typography.bodyLarge) },
                                    onClick = {
                                        estado.filtroEtiqueta = etiqueta
                                        menuEtiquetas = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when {
            todas == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            todas.isEmpty() -> Aviso(stringResource(R.string.tarjetas_vacio))
            estado.visibles.isEmpty() -> Aviso(
                stringResource(if (estado.consulta.isNotBlank()) R.string.tarjetas_vacio_busqueda else R.string.tarjetas_vacio_filtro),
            )
            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                // Número de cada tarjeta: su orden en el libro, contando todas (no cambia al filtrar).
                val numeros = numerosEnSuLibro(todas)
                items(estado.visibles, key = { it.id }) { tarjeta ->
                    TarjetaPequena(tarjeta, libro, alPulsar = { alAbrir(tarjeta) }, alEtiqueta = alEtiqueta, numero = numeros[tarjeta.id])
                }
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(28.dp),
    )
}

@Composable
private fun ChipFiltro(elegido: Boolean, descripcion: String, alPulsar: () -> Unit, contenido: @Composable () -> Unit) {
    val oscuro = colorResource(R.color.texto)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (elegido) oscuro else Color.Transparent)
            .border(BorderStroke(1.dp, if (elegido) oscuro else colorResource(R.color.borde)), RoundedCornerShape(24.dp))
            .clickable(role = Role.Tab, onClick = alPulsar)
            .semantics {
                contentDescription = descripcion
                selected = elegido
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (elegido) colorResource(R.color.fondo_tarjeta) else oscuro,
        ) { contenido() }
    }
}

/**
 * El número de cada subrayado dentro de su libro (1, 2, 3...), por orden de aparición.
 * [subrayados] tiene que venir ordenado por libro y, dentro de cada libro, por su sitio.
 */
fun numerosEnSuLibro(subrayados: List<Tarjeta>): Map<String, Int> {
    val cuenta = mutableMapOf<String, Int>()
    return subrayados.associate { tarjeta ->
        val n = (cuenta[tarjeta.idLibro] ?: 0) + 1
        cuenta[tarjeta.idLibro] = n
        tarjeta.id to n
    }
}

/**
 * Una tarjeta en la cuadrícula: color, página (en PDF) y capítulo; la frase; la nota al pie;
 * etiquetas (tocar una lleva a todas las tarjetas con esa etiqueta). Con [conTitulo], arriba
 * va el título del libro (en las listas que mezclan tarjetas de varios libros).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaPequena(
    tarjeta: Tarjeta,
    libro: Libro?,
    alPulsar: () -> Unit,
    alEtiqueta: (String) -> Unit = {},
    conTitulo: Boolean = false,
    numero: Int? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.fondo_tarjeta))
            .border(BorderStroke(1.dp, colorResource(R.color.borde)), RoundedCornerShape(16.dp))
            .clickable(onClick = alPulsar)
            .padding(14.dp),
    ) {
        // «3. Título del libro»: el orden de la tarjeta en su libro y de qué libro es.
        if ((conTitulo || numero != null) && libro != null) {
            Text(
                if (numero != null) stringResource(R.string.tarjeta_numero_titulo, numero, libro.titulo) else libro.titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            tarjeta.color?.let { Box(Modifier.size(10.dp).clip(CircleShape).background(colorResource(it.color))) }
            val pagina = paginaDe(tarjeta, libro)
            if (pagina != null) {
                Spacer(Modifier.width(8.dp))
                Text(pagina, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            // El capítulo, a la derecha.
            Text(
                tarjeta.capitulo?.takeIf { it.isNotBlank() }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            fraseResaltada(tarjeta, recortar = true),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 9,
            overflow = TextOverflow.Ellipsis,
        )
        // La nota, al pie, separada por una línea discontinua (como una nota al pie de página).
        tarjeta.nota?.let { nota ->
            Spacer(Modifier.height(10.dp))
            LineaDiscontinua()
            Spacer(Modifier.height(8.dp))
            Text(
                nota,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val etiquetas = Etiquetas.de(tarjeta)
        if (etiquetas.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                etiquetas.forEach { etiqueta -> EtiquetaPequena(etiqueta, alPulsar = { alEtiqueta(etiqueta) }) }
            }
        }
    }
}

/** Una etiqueta pequeña; al tocarla, se ven todas las tarjetas con esa etiqueta. */
@Composable
fun EtiquetaPequena(texto: String, alPulsar: (() -> Unit)? = null) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorResource(R.color.borde).copy(alpha = 0.6f))
            .then(if (alPulsar != null) Modifier.clickable(onClick = alPulsar) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun LineaDiscontinua() {
    val color = colorResource(R.color.borde)
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

/** En los PDF, la página del archivo (que no cambia); en EPUB las páginas dependen de la letra. */
@Composable
fun paginaDe(tarjeta: Tarjeta, libro: Libro?): String? =
    tarjeta.pagina?.takeIf { libro?.formato == Formato.PDF }?.let { stringResource(R.string.tarjetas_pagina, it) }

/**
 * La frase completa con la parte subrayada en su color (y en negrita). Con [recortar]
 * (tarjetas pequeñas), si hay mucho texto antes del subrayado se corta con «…» para que
 * el subrayado se vea siempre.
 */
@Composable
fun fraseResaltada(tarjeta: Tarjeta, recortar: Boolean = false): AnnotatedString {
    val fondo = tarjeta.color?.let { colorResource(it.color).copy(alpha = 0.45f) } ?: Color.Transparent
    val texto = colorResource(R.color.texto)
    val alrededor = colorResource(R.color.texto_secundario)
    val frase = tarjeta.contexto ?: tarjeta.texto
    val subrayado = tarjeta.texto
    val inicio = frase.indexOf(subrayado).takeIf { it >= 0 }
    return buildAnnotatedString {
        if (inicio == null) {
            // No se encuentra el subrayado dentro de la frase (por ejemplo, tras editarla): todo resaltado.
            withStyle(SpanStyle(background = fondo, color = texto, fontWeight = FontWeight.SemiBold)) { append(subrayado) }
            if (frase != subrayado && !recortar) {
                withStyle(SpanStyle(color = alrededor)) { append("  ·  $frase") }
            }
        } else {
            var antes = frase.substring(0, inicio)
            if (recortar && antes.length > MAXIMO_ANTES) {
                // Se corta por un espacio, para no partir palabras.
                val corte = antes.length - MAXIMO_ANTES
                val espacio = antes.indexOf(' ', corte).takeIf { it in corte until antes.length } ?: corte
                antes = "…" + antes.substring(espacio).trimStart()
            }
            withStyle(SpanStyle(color = alrededor)) { append(antes) }
            withStyle(SpanStyle(background = fondo, color = texto, fontWeight = FontWeight.SemiBold)) { append(subrayado) }
            withStyle(SpanStyle(color = alrededor)) { append(frase.substring(inicio + subrayado.length)) }
        }
    }
}

/** En las tarjetas pequeñas, cuánto texto se deja como mucho antes del subrayado. */
private const val MAXIMO_ANTES = 70
