package org.libertalia.reader.comic

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.libertalia.reader.R
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.lector.BarraProgreso
import org.libertalia.reader.lector.TemaControlesLector
import org.libertalia.reader.lector.TemaLector
import kotlin.math.max
import kotlin.math.roundToInt

/** Pantalla del lector de cómics: las páginas y, encima, las barras (boceto 13). */
@Composable
fun PantallaComic(estado: LectorComicViewModel, alVolver: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fondo_comic)),
    ) {
        val error = estado.error
        when {
            error != null -> Aviso(stringResource(error), alVolver)
            estado.total == 0 -> Abriendo(estado.libro?.titulo, alVolver)
            // Al cambiar de modo (o al girar el móvil) se monta de nuevo, en la misma página.
            else -> key(estado.modo) {
                if (estado.modo == ModoComic.SCROLL) Desplazamiento(estado) else Hojas(estado, doble = estado.modo == ModoComic.DOBLE)
            }
        }
        val libro = estado.libro
        if (libro != null && estado.total > 0) {
            TemaControlesLector(TemaLector.OSCURO) {
                BarrasComic(estado, libro, alVolver)
            }
        }
    }
}

/**
 * Mientras se abre el cómic. Si está en Google Drive u otra nube, la primera vez la nube
 * lo descarga entero y puede tardar bastante: se explica, y se puede volver atrás.
 */
@Composable
private fun Abriendo(titulo: String?, alVolver: () -> Unit) {
    val color = colorResource(R.color.lector_oscuro_texto)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = color)
        Spacer(Modifier.heightIn(min = 24.dp))
        Text(
            if (titulo != null) stringResource(R.string.comic_abriendo, titulo) else stringResource(R.string.comic_abriendo_sin_titulo),
            color = color,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.heightIn(min = 8.dp))
        Text(
            stringResource(R.string.comic_abriendo_nube),
            color = color.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.heightIn(min = 16.dp))
        TextButton(onClick = alVolver) { Text(stringResource(R.string.cd_volver), color = color) }
    }
}

@Composable
private fun Aviso(texto: String, alVolver: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(texto, color = colorResource(R.color.lector_oscuro_texto), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.heightIn(min = 16.dp))
        TextButton(onClick = alVolver) { Text(stringResource(R.string.cd_volver)) }
    }
}

/**
 * Las páginas, pasando de una en una o, en doble página, de dos en dos (la portada sola).
 * En manga (derecha a izquierda) el orden va al revés.
 */
@Composable
private fun Hojas(estado: LectorComicViewModel, doble: Boolean) {
    val unidades = if (doble) estado.pliegos else estado.total
    fun unidadDe(indice: Int) = if (doble) estado.pliegoDe(indice) else indice
    fun paginasDe(unidad: Int) = if (doble) estado.paginasDelPliego(unidad) else listOf(unidad)

    val hojas = rememberPagerState(initialPage = unidadDe(estado.pagina)) { unidades }
    val alcance = rememberCoroutineScope()

    // La página visible se apunta (y se guarda) en el estado. Si ya se ve la página guardada
    // (por ejemplo, la segunda de un par), no se cambia: así no se mueve al girar el móvil.
    LaunchedEffect(hojas) {
        snapshotFlow { hojas.currentPage }.collect { unidad ->
            val vistas = paginasDe(unidad)
            if (estado.pagina !in vistas) estado.paginaCambiada(vistas.first())
        }
    }
    // Toques en los lados y botones de volumen.
    LaunchedEffect(hojas) {
        estado.saltos.collect { cuantas ->
            val destino = (hojas.currentPage + cuantas).coerceIn(0, unidades - 1)
            if (destino != hojas.currentPage) alcance.launch { hojas.animateScrollToPage(destino) }
        }
    }
    // Saltos desde la barra de progreso o los marcadores.
    LaunchedEffect(hojas) {
        estado.destinos.collect { destino ->
            val indice = destino.coerceIn(0, estado.total - 1)
            hojas.scrollToPage(unidadDe(indice))
            estado.paginaCambiada(indice)
        }
    }

    HorizontalPager(
        state = hojas,
        reverseLayout = estado.derechaAIzquierda,
        // Con la página ampliada, deslizar mueve la imagen en vez de pasar de página.
        userScrollEnabled = !estado.ampliada,
        beyondViewportPageCount = 1,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { unidad ->
        PaginaComic(estado, paginasDe(unidad), esLaActual = hojas.currentPage == unidad)
    }
}

/**
 * Una página (o dos, una junto a otra): se ajusta a la pantalla y se puede ampliar con dos
 * dedos o con doble toque (y mover cuando está ampliada). Un toque en un lado pasa de
 * página; en el centro, muestra u oculta las barras.
 */
@Composable
private fun PaginaComic(estado: LectorComicViewModel, indices: List<Int>, esLaActual: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val densidad = LocalDensity.current
        val anchoPx = with(densidad) { maxWidth.toPx() }
        val altoPx = with(densidad) { maxHeight.toPx() }
        val recortar = estado.recortar
        // Se lee con algo más de resolución que la pantalla, para que se vea bien al ampliar.
        val imagenes by produceState<List<Bitmap>?>(null, indices, recortar) {
            val anchoCada = (anchoPx * 1.5f / indices.size).roundToInt()
            val leidas = indices.map { estado.imagen(it, anchoCada, (altoPx * 1.5f).roundToInt()) }
            value = if (leidas.any { it == null }) null else leidas.filterNotNull()
        }
        // En manga, la primera página del par va a la derecha.
        val enPantalla = imagenes?.let { if (estado.derechaAIzquierda) it.reversed() else it }
        // Proporción (ancho / alto) del conjunto, con las páginas a la misma altura.
        val proporcion = enPantalla?.sumOf { it.width.toDouble() / it.height }?.toFloat()

        var escala by remember { mutableFloatStateOf(1f) }
        var desplazamiento by remember { mutableStateOf(Offset.Zero) }
        val tamano = IntSize(anchoPx.roundToInt(), altoPx.roundToInt())

        /**
         * Hasta dónde se puede mover la página ampliada sin que se vea el fondo de alrededor.
         * Se mide con el dibujo de verdad, no con la pantalla: la página casi nunca la llena
         * (quedan franjas oscuras arriba y abajo, o a los lados), y si no se tiene en cuenta,
         * al ampliar cerca del borde se ve casi todo el fondo.
         */
        fun limitar(d: Offset, e: Float): Offset {
            var anchoVisto = tamano.width.toFloat()
            var altoVisto = tamano.height.toFloat()
            if (proporcion != null && proporcion > 0f) {
                if (proporcion > anchoVisto / altoVisto) altoVisto = anchoVisto / proporcion else anchoVisto = altoVisto * proporcion
            }
            val maxX = max(0f, (anchoVisto * e - tamano.width) / 2f)
            val maxY = max(0f, (altoVisto * e - tamano.height) / 2f)
            // Por seguridad: un desplazamiento no válido (NaN) dejaría la página fuera de la vista.
            val x = if (d.x.isNaN()) 0f else d.x
            val y = if (d.y.isNaN()) 0f else d.y
            return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
        }

        // Al dejar de ser la página visible, vuelve a su tamaño.
        LaunchedEffect(esLaActual) {
            if (!esLaActual) {
                escala = 1f
                desplazamiento = Offset.Zero
            }
        }
        if (esLaActual) estado.ampliada = escala > 1.01f

        /** Doble toque: amplía hacia el punto tocado o vuelve al tamaño normal. */
        fun alternarZoom(toque: Offset) {
            if (escala > 1.01f) {
                escala = 1f
                desplazamiento = Offset.Zero
            } else {
                val nueva = 2.5f
                val centro = Offset(tamano.width / 2f, tamano.height / 2f)
                escala = nueva
                desplazamiento = limitar((centro - toque) * (nueva - 1f), nueva)
            }
        }
        // Solo para pruebas: el lector puede pedir un doble toque (adb no sabe hacerlo).
        if (esLaActual) {
            LaunchedEffect(Unit) {
                estado.pruebasDobleToque.collect { (x, y) -> alternarZoom(Offset(x * tamano.width, y * tamano.height)) }
            }
        }

        val descripcion = stringResource(R.string.cd_pagina_comic, indices.first() + 1)
        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = descripcion }
                // Dos dedos: ampliar; un dedo con la página ampliada: moverla.
                .pointerInput(indices) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val evento = awaitPointerEvent()
                            val dedos = evento.changes.count { it.pressed }
                            // Ojo: al levantar el dedo del doble toque, este mismo evento activa
                            // el zoom (más abajo) y llega aquí ya sin ningún dedo en la pantalla.
                            // Sin dedos no hay punto central: daría un número no válido y la
                            // página se dibujaría fuera de la pantalla. Se ignora.
                            if (dedos > 0 && (dedos >= 2 || escala > 1.01f)) {
                                val zoom = evento.calculateZoom()
                                val nuevaEscala = (escala * zoom).coerceIn(1f, 5f)
                                val factor = nuevaEscala / escala
                                val centro = Offset(tamano.width / 2f, tamano.height / 2f)
                                val foco = evento.calculateCentroid(useCurrent = true) - centro
                                val movido = desplazamiento * factor + foco * (1 - factor) + evento.calculatePan()
                                escala = nuevaEscala
                                desplazamiento = if (escala <= 1.01f) Offset.Zero else limitar(movido, escala)
                                evento.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (evento.changes.any { it.pressed })
                    }
                }
                .pointerInput(indices) {
                    detectTapGestures(
                        onDoubleTap = { toque -> alternarZoom(toque) },
                        onTap = { toque ->
                            when {
                                toque.x < tamano.width * 0.28f -> estado.tocarLado(izquierdo = true)
                                toque.x > tamano.width * 0.72f -> estado.tocarLado(izquierdo = false)
                                else -> estado.barrasVisibles = !estado.barrasVisibles
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (enPantalla == null || proporcion == null) {
                CircularProgressIndicator(color = colorResource(R.color.lector_oscuro_texto).copy(alpha = 0.6f))
            } else {
                // Las páginas, una junto a otra a la misma altura, lo más grandes que quepan.
                Row(
                    Modifier
                        .aspectRatio(proporcion)
                        .graphicsLayer {
                            scaleX = escala
                            scaleY = escala
                            translationX = desplazamiento.x
                            translationY = desplazamiento.y
                        },
                ) {
                    enPantalla.forEach { imagen ->
                        Image(
                            bitmap = remember(imagen) { imagen.asImageBitmap() },
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.weight(imagen.width.toFloat() / imagen.height).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scroll vertical continuo (webtoons y manhwas): las páginas una debajo de otra, a todo
 * el ancho. Los toques en los lados y el volumen bajan o suben casi una pantalla.
 */
@Composable
private fun Desplazamiento(estado: LectorComicViewModel) {
    val lista = rememberLazyListState(initialFirstVisibleItemIndex = estado.pagina)
    val alcance = rememberCoroutineScope()

    // Página actual: la que ocupa el primer tercio de la pantalla.
    LaunchedEffect(lista) {
        snapshotFlow {
            val info = lista.layoutInfo
            val corte = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 3
            info.visibleItemsInfo.firstOrNull { it.offset + it.size > corte }?.index ?: lista.firstVisibleItemIndex
        }.collect { estado.paginaCambiada(it) }
    }
    LaunchedEffect(lista) {
        estado.saltos.collect { cuantas ->
            val alto = lista.layoutInfo.viewportSize.height
            alcance.launch { lista.animateScrollBy(cuantas * alto * 0.85f) }
        }
    }
    LaunchedEffect(lista) {
        estado.destinos.collect { destino ->
            val indice = destino.coerceIn(0, estado.total - 1)
            lista.scrollToItem(indice)
            estado.paginaCambiada(indice)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val anchoPx = with(LocalDensity.current) { maxWidth.toPx() }.roundToInt()
        val altoPantalla = maxHeight
        LazyColumn(
            state = lista,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { toque ->
                        when {
                            toque.x < size.width * 0.28f -> estado.tocarLado(izquierdo = true)
                            toque.x > size.width * 0.72f -> estado.tocarLado(izquierdo = false)
                            else -> estado.barrasVisibles = !estado.barrasVisibles
                        }
                    }
                },
        ) {
            items(count = estado.total, key = { it }) { indice ->
                val tiras by produceState<List<Bitmap>?>(null, indice) { value = estado.tiras(indice, anchoPx) }
                val actuales = tiras
                if (actuales == null) {
                    Box(Modifier.fillMaxWidth().height(altoPantalla), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colorResource(R.color.lector_oscuro_texto).copy(alpha = 0.6f))
                    }
                } else {
                    val descripcion = stringResource(R.string.cd_pagina_comic, indice + 1)
                    Column(Modifier.fillMaxWidth().semantics { contentDescription = descripcion }) {
                        actuales.forEach { tira ->
                            Image(
                                bitmap = remember(tira) { tira.asImageBitmap() },
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxWidth().aspectRatio(tira.width.toFloat() / tira.height),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Barra de arriba (volver, título y tomo, recorte, marcador) y de abajo (página, progreso y modos). */
@Composable
private fun BarrasComic(estado: LectorComicViewModel, libro: Libro, alVolver: () -> Unit) {
    val fondo = MaterialTheme.colorScheme.surface
    val manga = estado.derechaAIzquierda
    val modo = estado.modo
    val textoSentido = stringResource(if (manga) R.string.sentido_rtl_corto else R.string.sentido_ltr_corto)
    var verMarcadores by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = estado.barrasVisibles,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(Modifier.fillMaxWidth().background(fondo).statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
                ) {
                    IconButton(onClick = alVolver) {
                        Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        val tomo = libro.tomo
                        Text(
                            text = if (tomo != null) stringResource(R.string.comic_titulo_tomo, libro.titulo, tomo) else libro.titulo,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.comic_subtitulo,
                                stringResource(if (manga) R.string.comic_tipo_manga else R.string.comic_tipo_comic),
                                textoSentido.lowercase(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Recortar los márgenes blancos (no en scroll: ahí no se recorta).
                    if (modo != ModoComic.SCROLL) {
                        val activo = estado.recortar
                        IconButton(onClick = estado::alternarRecorte) {
                            Icon(
                                painterResource(if (activo) R.drawable.ic_recortar else R.drawable.ic_recortar_no),
                                contentDescription = stringResource(if (activo) R.string.cd_recorte_activado else R.string.cd_recorte_desactivado),
                                tint = if (activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                    // Marcar esta página (cinta llena si ya lo está). Con «Solo leer» no hay marcadores.
                    if (!estado.soloLeer) {
                        val marcada = estado.hayMarcadorAqui
                        IconButton(onClick = estado::alternarMarcador) {
                            Icon(
                                painterResource(if (marcada) R.drawable.ic_marcapaginas_lleno else R.drawable.ic_marcapaginas),
                                contentDescription = stringResource(if (marcada) R.string.cd_quitar_marcador else R.string.cd_poner_marcador),
                                tint = if (marcada) colorResource(R.color.rojo) else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { verMarcadores = true }) {
                            Icon(painterResource(R.drawable.ic_indice), contentDescription = stringResource(R.string.cd_lista_marcadores))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }

        AnimatedVisibility(
            visible = estado.barrasVisibles,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().background(fondo).navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                // Mientras se arrastra la barra, se ve la página a la que se irá.
                var arrastrando by remember { mutableStateOf<Int?>(null) }
                val visibles = estado.paginasVisibles
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val arrastrada = arrastrando
                    Text(
                        when {
                            arrastrada != null -> stringResource(R.string.comic_pagina_de, arrastrada + 1, estado.total)
                            visibles.size == 2 -> stringResource(R.string.comic_paginas_de, visibles[0] + 1, visibles[1] + 1, estado.total)
                            else -> stringResource(R.string.comic_pagina_de, estado.pagina + 1, estado.total)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        textoSentido,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // En manga la barra se llena de derecha a izquierda (se dibuja «en espejo»).
                val ultima = (estado.total - 1).coerceAtLeast(1)
                val espejo = manga && modo != ModoComic.SCROLL
                BarraProgreso(
                    progreso = ((arrastrando ?: estado.pagina).toFloat() / ultima).coerceIn(0f, 1f),
                    alMover = { arrastrando = (it * ultima).roundToInt() },
                    alSoltar = {
                        arrastrando?.let { estado.saltarA(it) }
                        arrastrando = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .graphicsLayer { scaleX = if (espejo) -1f else 1f },
                    descripcion = stringResource(R.string.cd_progreso_comic),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BotonModo(stringResource(R.string.comic_modo_pagina), elegido = modo == ModoComic.PAGINA, Modifier.weight(1f)) {
                        estado.elegirModo(ModoComic.PAGINA)
                    }
                    BotonModo(stringResource(R.string.comic_modo_doble), elegido = modo == ModoComic.DOBLE, Modifier.weight(1f)) {
                        estado.elegirModo(ModoComic.DOBLE)
                    }
                    BotonModo(stringResource(R.string.comic_modo_scroll), elegido = modo == ModoComic.SCROLL, Modifier.weight(1f)) {
                        estado.elegirModo(ModoComic.SCROLL)
                    }
                    BotonModo(
                        if (manga) "←" else "→",
                        elegido = false,
                        modifier = Modifier.weight(0.8f),
                        descripcion = stringResource(R.string.cd_cambiar_sentido),
                        alPulsar = estado::cambiarSentido,
                    )
                }
            }
        }
    }
    if (verMarcadores) {
        ListaMarcadores(
            estado,
            alIr = { indice ->
                estado.saltarA(indice)
                verMarcadores = false
            },
            alCerrar = { verMarcadores = false },
        )
    }
}

/** La lista de páginas marcadas: tocar una lleva a ella; la papelera la quita. */
@Composable
private fun ListaMarcadores(estado: LectorComicViewModel, alIr: (Int) -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = colorResource(R.color.fondo_tarjeta),
        titleContentColor = colorResource(R.color.texto),
        textContentColor = colorResource(R.color.texto),
        title = { Text(stringResource(R.string.comic_marcadores_titulo)) },
        text = {
            if (estado.marcadores.isEmpty()) {
                Text(stringResource(R.string.comic_marcadores_vacio))
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(estado.marcadores, key = { it }) { indice ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { alIr(indice) }
                                .padding(start = 8.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_marcapaginas_lleno),
                                contentDescription = null,
                                tint = colorResource(R.color.rojo),
                            )
                            Text(
                                stringResource(R.string.cd_pagina_comic, indice + 1),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            IconButton(onClick = { estado.quitarMarcador(indice) }) {
                                Icon(
                                    painterResource(R.drawable.ic_eliminar),
                                    contentDescription = stringResource(R.string.cd_quitar_marcador_de, indice + 1),
                                    tint = colorResource(R.color.texto_secundario),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = alCerrar) { Text(stringResource(R.string.comic_marcadores_cerrar)) }
        },
    )
}

@Composable
private fun BotonModo(
    texto: String,
    elegido: Boolean,
    modifier: Modifier = Modifier,
    descripcion: String? = null,
    alPulsar: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = alPulsar,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (elegido) 2.dp else 1.dp, if (elegido) color else MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        modifier = modifier
            .heightIn(min = 52.dp)
            .then(if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier),
    ) {
        // Las flechas del sentido de lectura, más grandes (en la letra normal salen diminutas).
        val esFlecha = texto == "←" || texto == "→"
        Text(
            texto,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontSize = if (esFlecha) 30.sp else MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = if (elegido) FontWeight.SemiBold else null,
            textAlign = TextAlign.Center,
            // En otros idiomas la palabra puede ser más larga: mejor en dos líneas que cortada.
            maxLines = 2,
        )
    }
}
