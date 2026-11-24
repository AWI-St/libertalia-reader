package org.libertalia.reader.ui.biblioteca

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.libertalia.reader.R
import org.libertalia.reader.biblioteca.FiltroBiblioteca
import org.libertalia.reader.datos.EstadoLectura
import org.libertalia.reader.datos.Estanteria
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.TipoLibro
import org.libertalia.reader.comic.SentidoLectura
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import kotlin.math.absoluteValue

/** Orden de los estantes y el texto de cada uno. */
private val estados = listOf(
    EstadoLectura.LEYENDO to R.string.estado_leyendo,
    EstadoLectura.PENDIENTE to R.string.estado_pendientes,
    EstadoLectura.TERMINADO to R.string.estado_terminados,
)

/** Contenido de la biblioteca cuando hay libros: filtros, «Seguir leyendo» y estantes. */
@Composable
fun Estanterias(
    libros: List<Libro>,
    estanterias: List<Estanteria>,
    librosPorEstanteria: Map<String, Set<String>>,
    filtro: FiltroBiblioteca,
    seguirLeyendo: Libro?,
    alAbrir: (Libro) -> Unit,
    alMantener: (Libro) -> Unit,
    alElegirFiltro: (FiltroBiblioteca) -> Unit,
    alOrganizar: () -> Unit,
    /** Modo Tarjetas: cuántas tarjetas tiene cada libro. Null en el modo Libros. */
    conteoTarjetas: Map<String, Int>? = null,
    alAlternarModo: () -> Unit = {},
    /** Modo Tarjetas: buscador de etiquetas (lo pone quien sabe todas las tarjetas). */
    buscadorEtiquetas: @Composable () -> Unit = {},
) {
    // Los cómics y mangas tienen su propio estante (siempre, sin tener que crearlo) y además
    // están en «Leyendo» y «Terminados» (de frente, como revistas, así se distinguen).
    // Los pendientes se quedan solo en su estante, para no llenar «Pendientes».
    val comics = libros.filter { it.tipo == TipoLibro.COMIC }
    val porEstado = libros
        .filterNot { it.tipo == TipoLibro.COMIC && it.estado == EstadoLectura.PENDIENTE }
        .groupBy { it.estado }

    // Cada estante: clave, título y sus libros.
    data class DatosEstante(
        val clave: String,
        val titulo: String,
        val libros: List<Libro>,
        val propia: Boolean,
        val plural: Int = R.plurals.numero_libros,
    )
    val deEstado = estados.map { (estado, titulo) ->
        DatosEstante(estado.name, stringResource(titulo), ordenarEstante(porEstado[estado].orEmpty()), propia = false)
    }
    val tituloComics = stringResource(R.string.estante_comics)
    fun estanteComics(lista: List<Libro>) =
        listOfNotNull(DatosEstante("comics", tituloComics, ordenarEstante(lista), propia = false, plural = R.plurals.numero_comics).takeIf { lista.isNotEmpty() })
    val propios = estanterias.map { estanteria ->
        val ids = librosPorEstanteria[estanteria.id].orEmpty()
        DatosEstante("e-${estanteria.id}", estanteria.nombre, ordenarEstante(libros.filter { it.id in ids }), propia = true)
    }
    val visibles = when (filtro) {
        FiltroBiblioteca.Todos -> deEstado.filter { it.libros.isNotEmpty() } + estanteComics(comics) + propios
        // Con el filtro «Pendientes», los cómics pendientes se ven en su estante.
        is FiltroBiblioteca.PorEstado -> deEstado.filter { it.clave == filtro.estado.name } +
            if (filtro.estado == EstadoLectura.PENDIENTE) estanteComics(comics.filter { it.estado == EstadoLectura.PENDIENTE }) else emptyList()
        is FiltroBiblioteca.PorEstanteria -> propios.filter { it.clave == "e-${filtro.idEstanteria}" }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item { FiltrosBiblioteca(filtro = filtro, estanterias = estanterias, alElegir = alElegirFiltro) }
        if (seguirLeyendo != null && filtro == FiltroBiblioteca.Todos) {
            item {
                TarjetaSeguirLeyendo(
                    libro = seguirLeyendo,
                    alAbrir = { alAbrir(seguirLeyendo) },
                    alMantener = { alMantener(seguirLeyendo) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // Botón «Tarjetas» / «Libros» (y, en modo Tarjetas, el aviso de qué hacer).
        item { BotonModo(modoTarjetas = conteoTarjetas != null, alPulsar = alAlternarModo) }
        if (conteoTarjetas != null) item { buscadorEtiquetas() }
        items(visibles, key = { it.clave }) { estante ->
            Estante(
                titulo = estante.titulo,
                libros = estante.libros,
                plural = estante.plural,
                alAbrir = alAbrir,
                alMantener = alMantener,
                conteoTarjetas = conteoTarjetas,
                // En las estanterías propias, un botón para ordenarlas, renombrarlas o borrarlas.
                alOrganizar = if (estante.propia) alOrganizar else null,
            )
        }
    }
}

/**
 * Botón oscuro que cambia entre los modos Libros y Tarjetas (boceto 7). En modo Libros
 * dice «Tarjetas»; en modo Tarjetas dice «Libros» y al lado sale el aviso de qué hacer.
 */
@Composable
private fun BotonModo(modoTarjetas: Boolean, alPulsar: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Surface(
            onClick = alPulsar,
            shape = RoundedCornerShape(50),
            color = colorResource(R.color.texto),
            contentColor = colorResource(R.color.fondo_tarjeta),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Icon(
                    painterResource(if (modoTarjetas) R.drawable.ic_estanteria else R.drawable.ic_tarjetas),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (modoTarjetas) R.string.modo_libros else R.string.modo_tarjetas),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (modoTarjetas) {
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.modo_tarjetas_aviso),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Fila de botones «Todos · Leyendo · Pendientes · Terminados» y las estanterías del usuario. */
@Composable
private fun FiltrosBiblioteca(
    filtro: FiltroBiblioteca,
    estanterias: List<Estanteria>,
    alElegir: (FiltroBiblioteca) -> Unit,
) {
    val opciones: List<Pair<FiltroBiblioteca, String>> =
        listOf<Pair<FiltroBiblioteca, String>>(FiltroBiblioteca.Todos to stringResource(R.string.filtro_todos)) +
            estados.map { (estado, texto) -> FiltroBiblioteca.PorEstado(estado) to stringResource(texto) } +
            estanterias.map { FiltroBiblioteca.PorEstanteria(it.id) to it.nombre }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(opciones, key = { it.first.toString() }) { (opcion, texto) ->
            val elegido = opcion == filtro
            Surface(
                onClick = { alElegir(opcion) },
                shape = RoundedCornerShape(50),
                color = if (elegido) colorResource(R.color.texto) else MaterialTheme.colorScheme.background,
                contentColor = if (elegido) colorResource(R.color.fondo_tarjeta) else colorResource(R.color.texto),
                border = if (elegido) null else BorderStroke(1.dp, colorResource(R.color.borde)),
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(texto, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Tarjeta grande con el último libro abierto (boceto 1, arriba). */
@Composable
private fun TarjetaSeguirLeyendo(
    libro: Libro,
    alAbrir: () -> Unit,
    alMantener: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vibracion = LocalHapticFeedback.current
    val forma = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(colorResource(R.color.fondo_tarjeta))
            .border(1.dp, colorResource(R.color.borde), forma)
            .combinedClickable(
                onClick = alAbrir,
                onLongClick = {
                    vibracion.performHapticFeedback(HapticFeedbackType.LongPress)
                    alMantener()
                },
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortadaLibro(
            libro,
            Modifier
                .width(64.dp)
                .height(94.dp)
                .shadow(4.dp, RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.seguir_leyendo).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = libro.titulo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = textoProgreso(libro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { libro.progreso.toFloat().coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = colorResource(R.color.borde),
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
            )
        }
    }
}

/**
 * «Capítulo I · Pág. 34 de 210» (o solo «Pág. 52 de 234» en cómics). Nunca porcentaje:
 * se usan las páginas del lector tal como se vieron la última vez.
 */
@Composable
private fun textoProgreso(libro: Libro): String {
    val pagina = libro.paginaLector
    val paginas = libro.paginasLector
    val textoPagina = if (pagina != null && paginas != null && paginas > 0) {
        stringResource(R.string.lector_pagina, pagina.coerceIn(1, paginas), paginas)
    } else {
        null
    }
    val capitulo = libro.capituloActual?.takeIf { it.isNotBlank() && libro.tipo != TipoLibro.COMIC }
    return when {
        capitulo != null && textoPagina != null -> stringResource(R.string.capitulo_y_progreso, capitulo, textoPagina)
        else -> capitulo ?: textoPagina.orEmpty()
    }
}

/**
 * Orden dentro de cada estante: primero los que se están leyendo (el último leído delante),
 * luego los pendientes y al final los terminados; dentro de cada grupo, por título.
 */
private fun ordenarEstante(libros: List<Libro>): List<Libro> = libros.sortedWith(
    compareBy<Libro> {
        when (it.estado) {
            EstadoLectura.LEYENDO -> 0
            EstadoLectura.PENDIENTE -> 1
            EstadoLectura.TERMINADO -> 2
        }
    }
        .thenByDescending { if (it.estado == EstadoLectura.LEYENDO) it.fechaUltimaLectura ?: 0L else 0L }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.titulo }
)

/** Un estante: título con el número de libros, y los libros de pie sobre la madera. */
@Composable
private fun Estante(
    titulo: String,
    libros: List<Libro>,
    alAbrir: (Libro) -> Unit,
    alMantener: (Libro) -> Unit,
    alOrganizar: (() -> Unit)?,
    plural: Int = R.plurals.numero_libros,
    conteoTarjetas: Map<String, Int>? = null,
) {
    Column(Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(plural, libros.size, libros.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (alOrganizar != null) {
                IconButton(onClick = alOrganizar) {
                    Icon(
                        painterResource(R.drawable.ic_organizar),
                        contentDescription = stringResource(R.string.organizar_estanterias),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (libros.isEmpty()) {
            Text(
                text = stringResource(R.string.estante_vacio),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.heightIn(min = 128.dp),
            ) {
                items(libros, key = { it.id }) { libro ->
                    if (libro.tipo == TipoLibro.COMIC) {
                        // En modo Tarjetas, los cómics (que no tienen tarjetas) se ven apagados y no se tocan.
                        ComicDeFrente(
                            libro = libro,
                            alAbrir = { alAbrir(libro) },
                            alMantener = { alMantener(libro) },
                            apagado = conteoTarjetas != null,
                        )
                    } else {
                        LibroDePie(
                            libro = libro,
                            alAbrir = { alAbrir(libro) },
                            alMantener = { alMantener(libro) },
                            tarjetas = conteoTarjetas?.let { it[libro.id] ?: 0 },
                        )
                    }
                }
            }
        }
        TablaDeMadera(Modifier.padding(horizontal = 16.dp))
    }
}

/** Un libro de pie. Cada uno tiene una altura algo distinta, como en una estantería real. */
@Composable
private fun LibroDePie(libro: Libro, alAbrir: () -> Unit, alMantener: () -> Unit, tarjetas: Int? = null) {
    val vibracion = LocalHapticFeedback.current
    val alto = 112.dp + (libro.id.hashCode().absoluteValue % 4 * 4).dp
    Box {
        PortadaLibro(
            libro,
            Modifier
                .width(82.dp)
                .height(alto)
                // Los libros que vinieron de una copia sin su archivo se ven más tenues.
                .alpha(if (libro.uriArchivo == null) 0.45f else 1f)
                .shadow(3.dp, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .combinedClickable(
                    onClick = alAbrir,
                    onLongClick = {
                        vibracion.performHapticFeedback(HapticFeedbackType.LongPress)
                        alMantener()
                    },
                ),
        )
        IconoEstado(libro.estado, Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 5.dp))
        // Modo Tarjetas: cuántas tarjetas tiene, en un círculo arriba a la derecha.
        if (tarjetas != null && tarjetas > 0) {
            val descripcion = pluralStringResource(R.plurals.numero_tarjetas, tarjetas, tarjetas)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(36.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colorResource(R.color.fondo_tarjeta))
                    .border(1.dp, colorResource(R.color.borde), CircleShape)
                    .semantics { contentDescription = descripcion },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tarjetas.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.texto),
                )
            }
        }
    }
}

/**
 * Un cómic o manga, de frente como una revista apoyada en el estante: con su portada
 * a la vista, un borde grueso oscuro, algo inclinado, y la etiqueta «CÓMIC · 2» o «MANGA · 1».
 */
@Composable
private fun ComicDeFrente(libro: Libro, alAbrir: () -> Unit, alMantener: () -> Unit, apagado: Boolean = false) {
    val vibracion = LocalHapticFeedback.current
    val tinta = Color(0xFF1E1A18)
    // Cada cómic se inclina un poco hacia un lado, siempre igual para el mismo cómic.
    val giro = listOf(-3f, 2.5f, -1.5f, 3f)[libro.id.hashCode().absoluteValue % 4]
    Box(
        Modifier
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .rotate(giro)
            .alpha(if (libro.uriArchivo == null || apagado) 0.4f else 1f)
            .shadow(4.dp, RoundedCornerShape(3.dp))
            .border(BorderStroke(4.dp, tinta), RoundedCornerShape(3.dp))
            .combinedClickable(
                enabled = !apagado,
                onClick = alAbrir,
                onLongClick = {
                    vibracion.performHapticFeedback(HapticFeedbackType.LongPress)
                    alMantener()
                },
            ),
    ) {
        PortadaLibro(libro, Modifier.padding(4.dp).width(96.dp).height(132.dp), forma = RoundedCornerShape(1.dp))
        EtiquetaComic(libro, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 9.dp))
        IconoEstado(libro.estado, Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp))
    }
}

/** Etiqueta negra «CÓMIC» o «MANGA» con el número de tomo, como en el boceto. */
@Composable
fun EtiquetaComic(libro: Libro, modifier: Modifier = Modifier) {
    val clase = stringResource(if (esManga(libro)) R.string.etiqueta_manga else R.string.etiqueta_comic)
    val texto = libro.tomo?.let { stringResource(R.string.etiqueta_comic_tomo, clase, it) } ?: clase
    Text(
        text = texto,
        color = Color(0xFFF3EADB),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = modifier
            .background(Color(0xFF1E1A18), RoundedCornerShape(5.dp))
            .border(BorderStroke(1.dp, Color(0xFFF3EADB).copy(alpha = 0.6f)), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Un manga se lee de derecha a izquierda. */
fun esManga(libro: Libro): Boolean = libro.sentidoLectura == SentidoLectura.DERECHA_A_IZQUIERDA

/**
 * Circulito en la esquina de la portada: tic verde si está terminado,
 * libro abierto amarillo si se está leyendo, y nada si está pendiente.
 */
@Composable
fun IconoEstado(estado: EstadoLectura, modifier: Modifier = Modifier) {
    val fondo: Int
    val icono: Int
    val tinta: Int
    when (estado) {
        EstadoLectura.TERMINADO -> {
            fondo = R.color.estado_terminado; icono = R.drawable.ic_hecho; tinta = R.color.fondo_tarjeta
        }
        EstadoLectura.LEYENDO -> {
            fondo = R.color.estado_leyendo; icono = R.drawable.ic_libro_abierto; tinta = R.color.texto
        }
        EstadoLectura.PENDIENTE -> return
    }
    Box(
        modifier = modifier
            .size(26.dp)
            .shadow(2.dp, CircleShape)
            .background(colorResource(fondo), CircleShape)
            .border(1.5.dp, colorResource(R.color.fondo_tarjeta), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icono),
            contentDescription = textoEstado(estado),
            tint = colorResource(tinta),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** La tabla del estante, con un borde superior más claro y una sombra debajo. */
@Composable
private fun TablaDeMadera(modifier: Modifier = Modifier) {
    val madera = colorResource(R.color.madera)
    Box(modifier.fillMaxWidth()) {
        // Sombra suave bajo el estante.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .offset(y = 10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(madera.copy(alpha = 0.15f))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.verticalGradient(
                        0f to madera.copy(red = minOf(1f, madera.red * 1.25f), green = minOf(1f, madera.green * 1.2f)),
                        0.25f to madera,
                        1f to madera,
                    )
                )
        )
    }
}
