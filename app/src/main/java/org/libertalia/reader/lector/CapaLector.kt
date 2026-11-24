package org.libertalia.reader.lector

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.runtime.collectAsState
import org.libertalia.reader.R
import org.libertalia.reader.voz.AvisoProblemaVoz
import org.libertalia.reader.voz.LecturaEnVoz
import org.libertalia.reader.voz.PanelVoz
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

/** Alto fijo de la barra superior (sin contar la barra de estado del móvil) y de su línea inferior. */
const val ALTO_BARRA_SUPERIOR_DP = 64
const val ALTO_LINEA_BARRA_DP = 1

/** Alto de la pestañita de la barra inferior. */
const val ALTO_PESTANA_DP = 30

/** Alto del panel de la lectura en voz alta con su margen (el libro le deja sitio mientras se escucha). */
const val ALTO_PANEL_VOZ_DP = 76

/**
 * Colores de las barras y paneles según el tema de lectura: en el tema oscuro,
 * también los controles son oscuros (nada de barras claras sobre un libro oscuro).
 * surface = barras y panel; background = fondo de lectura (índice, búsqueda).
 */
@Composable
fun TemaControlesLector(tema: TemaLector, contenido: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val texto = colorResource(tema.texto)
    val colores = base.copy(
        primary = if (tema.oscuro) colorResource(R.color.lector_oscuro_principal) else base.primary,
        surface = colorResource(tema.barra),
        onSurface = texto,
        background = colorResource(tema.fondo),
        onBackground = texto,
        surfaceVariant = colorResource(tema.fondo),
        onSurfaceVariant = colorResource(tema.textoSecundario),
        outline = colorResource(tema.borde),
        surfaceContainerLow = colorResource(tema.barra),
    )
    MaterialTheme(colorScheme = colores, typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalContentColor provides texto, content = contenido)
    }
}

/** Lo que la capa de controles pide hacer al lector. */
class AccionesLector(
    val volver: () -> Unit,
    val irA: (Locator) -> Unit,
    val irAEnlace: (Link) -> Unit,
    val irAPagina: (Int) -> Unit,
    val irAMarcapaginas: () -> Unit,
    /** Ir a una posición exacta (por ejemplo, una página destacada). */
    val irAPosicion: (Locator) -> Unit,
    /** Abrir las tarjetas (subrayados) de este libro. */
    val abrirTarjetas: () -> Unit,
    /** PDF original ↔ modo texto. */
    val cambiarModo: () -> Unit,
    /** Modo texto: separar o no las notas al pie (se vuelve a preparar el texto). */
    val cambiarNotas: (Boolean) -> Unit,
    /** Empezar a leer en voz alta desde la página que se ve (en un PDF, pasa antes al modo texto). */
    val escuchar: () -> Unit,
)

/**
 * Controles que van encima del libro (boceto 2): barra superior, barra inferior plegable,
 * índice y búsqueda. Las zonas vacías dejan pasar los toques al libro.
 */
@Composable
fun CapaLector(
    estado: LectorViewModel,
    titulo: String,
    autor: String,
    acciones: AccionesLector,
) {
    Box(Modifier.fillMaxSize()) {
        // Barra superior: siempre visible y de alto fijo, para que el libro sepa desde
        // el principio dónde empieza (y no tenga que reordenarse después).
        Box(Modifier.align(Alignment.TopCenter)) {
            BarraSuperior(
                titulo = titulo,
                autor = autor,
                sePuedeBuscar = estado.sePuedeBuscar,
                alVolver = acciones.volver,
                alBuscar = { estado.busquedaAbierta = true },
                // En los PDF de cómic no hay modo texto (son imágenes).
                botonModo = if (estado.esPdfOriginal && !estado.esComic) estado.enModoTexto else null,
                alCambiarModo = acciones.cambiarModo,
                destacada = if (estado.sePuedeDestacar) estado.paginaDestacada != null else null,
                alDestacar = estado::pulsarDestacar,
            )
        }

        // Abajo: la pestañita (barra plegada) o la barra inferior completa, que sube
        // por encima del texto sin moverlo. Encima, el panel de la lectura en voz alta.
        val vozActiva by LecturaEnVoz.activa.collectAsState()
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (vozActiva) {
                PanelVoz(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
            }
            if (estado.inferiorPlegada) {
                Box(Modifier.navigationBarsPadding()) {
                    Pestana(
                        haciaArriba = true,
                        descripcion = stringResource(R.string.cd_desplegar_barra),
                        alPulsar = { estado.plegarInferior(false) },
                    )
                }
            }
            AnimatedVisibility(
                visible = !estado.inferiorPlegada,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                Column {
                    Pestana(
                        haciaArriba = false,
                        descripcion = stringResource(R.string.cd_plegar_barra),
                        alPulsar = { estado.plegarInferior(true) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    BarraInferior(estado = estado, acciones = acciones)
                }
            }
        }

        // Aviso al subrayar, encima de la barra inferior.
        AnimatedVisibility(
            visible = estado.avisoSubrayado,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 200.dp),
        ) {
            AvisoSubrayado(alVer = acciones.abrirTarjetas)
        }
    }
    AvisoProblemaVoz()

    if (estado.listaDestacadasAbierta) {
        ListaDestacadas(
            destacadas = estado.destacadas,
            esPdf = estado.esPdfOriginal,
            alIr = { tarjeta ->
                estado.listaDestacadasAbierta = false
                estado.destinoDe(tarjeta)?.let(acciones.irAPosicion)
            },
            alEditar = { tarjeta ->
                estado.listaDestacadasAbierta = false
                estado.destacadaAbierta = tarjeta to false
            },
            alCerrar = { estado.listaDestacadasAbierta = false },
        )
    }
    estado.subrayadoAbierto?.let { tarjeta ->
        HojaSubrayado(
            tarjeta = tarjeta,
            alCambiarColor = { estado.cambiarColor(tarjeta, it) },
            alEscribirNota = {
                estado.subrayadoAbierto = null
                estado.notaAbierta = tarjeta
            },
            alBorrarNota = {
                estado.subrayadoAbierto = null
                estado.guardarNota(tarjeta, null)
            },
            alAnadirEtiqueta = { estado.etiquetaAbierta = tarjeta },
            alQuitarEtiqueta = { estado.quitarEtiqueta(tarjeta, it) },
            alQuitar = {
                estado.subrayadoAbierto = null
                estado.quitarTarjeta(tarjeta)
            },
            alCerrar = { estado.subrayadoAbierto = null },
        )
    }
    // Poner una etiqueta al subrayado, con sugerencias de las que ya existen.
    estado.etiquetaAbierta?.let { tarjeta ->
        org.libertalia.reader.tarjetas.DialogoEtiqueta(
            existentes = org.libertalia.reader.tarjetas.TodasLasEtiquetas.de(estado.todosLosSubrayados),
            yaPuestas = org.libertalia.reader.tarjetas.Etiquetas.de(tarjeta).toSet(),
            alGuardar = {
                estado.etiquetaAbierta = null
                estado.anadirEtiqueta(tarjeta, it)
            },
            alCerrar = { estado.etiquetaAbierta = null },
        )
    }
    estado.notaAbierta?.let { tarjeta ->
        DialogoNota(
            tarjeta = tarjeta,
            alGuardar = {
                estado.notaAbierta = null
                estado.guardarNota(tarjeta, it)
            },
            alBorrar = {
                estado.notaAbierta = null
                estado.guardarNota(tarjeta, null)
            },
            alCerrar = { estado.notaAbierta = null },
        )
    }
    estado.destacadaAbierta?.let { (tarjeta, nueva) ->
        DialogoDestacada(
            tarjeta = tarjeta,
            nueva = nueva,
            alGuardar = {
                estado.destacadaAbierta = null
                estado.guardarDescripcion(tarjeta, it)
            },
            alQuitar = {
                estado.destacadaAbierta = null
                estado.quitarTarjeta(tarjeta)
            },
            alCerrar = { estado.destacadaAbierta = null },
        )
    }

    if (estado.indiceAbierto) {
        Indice(
            estado = estado,
            alElegir = {
                estado.indiceAbierto = false
                acciones.irAEnlace(it)
            },
            alCerrar = { estado.indiceAbierto = false },
        )
    }
    estado.preparandoTexto?.let { (pagina, total) ->
        // Creando el modo texto (solo la primera vez): progreso y aviso de lo que se pierde.
        AlertDialog(
            onDismissRequest = {},
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onBackground,
            title = { Text(stringResource(R.string.modo_texto_preparando)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { pagina.toFloat() / total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.modo_texto_progreso, pagina, total))
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(stringResource(R.string.modo_texto_aviso), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = estado::cancelarModoTexto) { Text(stringResource(R.string.boton_cancelar)) }
            },
        )
    }
    estado.avisoModoTexto?.let { aviso ->
        AlertDialog(
            onDismissRequest = { estado.avisoModoTexto = null },
            containerColor = MaterialTheme.colorScheme.background,
            textContentColor = MaterialTheme.colorScheme.onBackground,
            text = { Text(stringResource(aviso)) },
            confirmButton = {
                TextButton(onClick = { estado.avisoModoTexto = null }) { Text(stringResource(R.string.boton_entendido)) }
            },
        )
    }
    if (estado.confirmarMarcapaginas) {
        // Antes de saltar al marcapáginas se pregunta, para no perder la página actual sin querer.
        AlertDialog(
            onDismissRequest = { estado.confirmarMarcapaginas = false },
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onBackground,
            title = { Text(stringResource(R.string.marcapaginas_confirmar_titulo)) },
            text = { Text(stringResource(R.string.marcapaginas_confirmar_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    estado.confirmarMarcapaginas = false
                    acciones.irAMarcapaginas()
                }) { Text(stringResource(R.string.marcapaginas_confirmar_ir)) }
            },
            dismissButton = {
                TextButton(onClick = { estado.confirmarMarcapaginas = false }) {
                    Text(stringResource(R.string.boton_cancelar))
                }
            },
        )
    }
    if (estado.ajustesAbiertos) {
        PanelAjustes(
            ajustes = estado.ajustes,
            esPdf = estado.esPdf,
            notas = if (estado.enModoTexto) estado.notasActivas else null,
            alCambiarNotas = { activas ->
                estado.ajustesAbiertos = false
                acciones.cambiarNotas(activas)
            },
            alCambiar = estado::cambiarAjustes,
            alCerrar = { estado.ajustesAbiertos = false },
        )
    }
    if (estado.busquedaAbierta) {
        Busqueda(
            estado = estado,
            alElegir = {
                estado.busquedaAbierta = false
                acciones.irA(it)
            },
            alCerrar = { estado.busquedaAbierta = false },
        )
    }
}

@Composable
private fun BarraSuperior(
    titulo: String,
    autor: String,
    sePuedeBuscar: Boolean,
    alVolver: () -> Unit,
    alBuscar: () -> Unit,
    /** null: no es un PDF; false: se ve el PDF; true: se ve en modo texto. */
    botonModo: Boolean?,
    alCambiarModo: () -> Unit,
    /** null: no se pueden destacar páginas; true: la página que se ve está destacada. */
    destacada: Boolean?,
    alDestacar: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(ALTO_BARRA_SUPERIOR_DP.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (autor.isNotBlank()) {
                    Text(
                        autor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (botonModo != null) {
                IconButton(onClick = alCambiarModo) {
                    Icon(
                        painterResource(if (botonModo) R.drawable.ic_modo_pdf else R.drawable.ic_modo_texto),
                        contentDescription = stringResource(if (botonModo) R.string.cd_modo_pdf else R.string.cd_modo_texto),
                    )
                }
            }
            if (sePuedeBuscar) {
                IconButton(onClick = alBuscar) {
                    Icon(painterResource(R.drawable.ic_buscar), contentDescription = stringResource(R.string.cd_buscar))
                }
            }
            if (destacada != null) {
                IconButton(onClick = alDestacar) {
                    Icon(
                        painterResource(if (destacada) R.drawable.ic_destacar_lleno else R.drawable.ic_destacar),
                        contentDescription = stringResource(if (destacada) R.string.cd_pagina_destacada else R.string.cd_destacar_pagina),
                        tint = if (destacada) colorResource(R.color.rojo) else LocalContentColor.current,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** Pestaña con flecha para plegar o desplegar la barra inferior. */
@Composable
private fun Pestana(haciaArriba: Boolean, descripcion: String, alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    Surface(
        onClick = alPulsar,
        shape = forma,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.width(64.dp).height(ALTO_PESTANA_DP.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (haciaArriba) R.drawable.ic_subir else R.drawable.ic_bajar),
                contentDescription = descripcion,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BarraInferior(estado: LectorViewModel, acciones: AccionesLector) {
    val paginacion = estado.paginacion
    val total = paginacion?.total ?: 1
    // La barra va por páginas: la primera está al principio y la última al final.
    fun progresoDe(pagina: Int) = if (total > 1) (pagina - 1).toFloat() / (total - 1) else 1f
    fun paginaDe(progreso: Float) = (progreso * (total - 1)).roundToInt() + 1
    // Mientras se arrastra la barra, se muestra la página donde está el dedo; al soltar, se salta allí.
    var arrastrando by remember { mutableStateOf<Int?>(null) }
    val pagina = arrastrando ?: paginacion?.pagina

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            text = textoPosicion(estado, pagina, total.takeIf { paginacion != null }, arrastrando = arrastrando != null),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        BarraProgreso(
            progreso = pagina?.let(::progresoDe) ?: 0f,
            alMover = { if (paginacion != null) arrastrando = paginaDe(it) },
            alSoltar = {
                arrastrando?.let(acciones.irAPagina)
                arrastrando = null
            },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
        ) {
            BotonBarra(R.drawable.ic_indice, stringResource(R.string.lector_indice)) { estado.indiceAbierto = true }
            if (estado.marcapaginas != null && !estado.esPdf) {
                BotonBarra(R.drawable.ic_marcapaginas, stringResource(R.string.lector_marcapaginas)) {
                    estado.confirmarMarcapaginas = true
                }
            }
            if (!estado.esComic && !estado.soloLeer) {
                BotonBarra(R.drawable.ic_tarjetas, stringResource(R.string.lector_tarjetas), acciones.abrirTarjetas)
            }
            if (estado.sePuedeDestacar) {
                BotonBarra(R.drawable.ic_destacar, stringResource(R.string.lector_destacadas)) { estado.listaDestacadasAbierta = true }
            }
            // Lectura en voz alta: solo en libros (los cómics son imágenes).
            if (!estado.esComic && !estado.soloLeer) {
                BotonBarra(R.drawable.ic_escuchar, stringResource(R.string.lector_escuchar), acciones.escuchar)
            }
            BotonBarra(R.drawable.ic_aa, stringResource(R.string.lector_ajustes)) { estado.ajustesAbiertos = true }
        }
    }
}

/**
 * «Capítulo I · Pág. 3 de 7» en EPUB y «Pág. 3 de 7» en PDF.
 * Mientras se arrastra la barra de progreso, muestra la página a la que se va a saltar.
 * Si el libro no tiene páginas, se muestra el porcentaje.
 */
@Composable
private fun textoPosicion(estado: LectorViewModel, pagina: Int?, total: Int?, arrastrando: Boolean): String {
    val capitulo = if (estado.esPdf || arrastrando) null else estado.capituloActual?.takeIf { it.isNotBlank() }
    // Nunca porcentaje: si el libro no tiene páginas, solo se muestra el capítulo.
    val textoPagina = if (pagina != null && total != null) stringResource(R.string.lector_pagina, pagina, total) else null
    return when {
        capitulo != null && textoPagina != null -> stringResource(R.string.capitulo_y_progreso, capitulo, textoPagina)
        else -> capitulo ?: textoPagina ?: estado.capituloActual.orEmpty()
    }
}

@Composable
private fun RowScope.BotonBarra(icono: Int, texto: String, alPulsar: () -> Unit) {
    // Todos los botones con el mismo ancho: caben cuatro sin partir las palabras.
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clickable(onClick = alPulsar)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(26.dp))
        // Si la palabra no cabe (en otros idiomas puede ser larga), la letra se encoge un
        // poco en vez de partir la palabra.
        BasicText(
            texto,
            style = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
        )
    }
}

/** Índice del libro: toca un capítulo para ir a él. El actual aparece marcado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Indice(estado: LectorViewModel, alElegir: (Link) -> Unit, alCerrar: () -> Unit) {
    val hrefActual = estado.posicion?.href?.toString()?.substringBefore('#')
    val actual = estado.indice.indexOfLast { it.enlace.href.toString().substringBefore('#') == hrefActual }
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Text(
            stringResource(R.string.lector_indice),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (estado.indice.isEmpty()) {
            Text(
                stringResource(R.string.indice_vacio),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(estado.indice) { posicion, entrada ->
                    val esActual = posicion == actual
                    Text(
                        text = entrada.titulo,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (esActual) FontWeight.Bold else FontWeight.Normal,
                        color = if (esActual) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { alElegir(entrada.enlace) }
                            .padding(start = (24 + entrada.nivel * 20).dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
                    )
                }
            }
        }
    }
}

/** Pantalla de búsqueda dentro del libro, con cada resultado en su frase. */
@Composable
private fun Busqueda(estado: LectorViewModel, alElegir: (Locator) -> Unit, alCerrar: () -> Unit) {
    BackHandler(onBack = alCerrar)
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    val busqueda = estado.busqueda

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = alCerrar) {
                    Icon(painterResource(R.drawable.ic_volver), contentDescription = stringResource(R.string.cd_volver))
                }
                OutlinedTextField(
                    value = busqueda.consulta,
                    onValueChange = estado::cambiarConsulta,
                    placeholder = { Text(stringResource(R.string.buscar_pista)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { estado.buscar() }),
                    trailingIcon = {
                        IconButton(onClick = estado::buscar) {
                            Icon(painterResource(R.drawable.ic_buscar), contentDescription = stringResource(R.string.cd_buscar))
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(foco),
                )
            }
            when {
                busqueda.buscando && busqueda.resultados.isEmpty() -> Row(
                    Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.buscar_buscando))
                }
                busqueda.buscada && busqueda.resultados.isEmpty() -> Text(
                    stringResource(R.string.buscar_sin_resultados, busqueda.consulta.trim()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                busqueda.resultados.isNotEmpty() -> {
                    Text(
                        pluralStringResource(R.plurals.buscar_resultados, busqueda.resultados.size, busqueda.resultados.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(busqueda.resultados) { _, resultado ->
                            ResultadoBusqueda(resultado, alPulsar = { alElegir(resultado) })
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Un resultado: capítulo y la frase con la palabra buscada resaltada. */
@Composable
private fun ResultadoBusqueda(resultado: Locator, alPulsar: () -> Unit) {
    val resaltado = colorResource(R.color.estado_leyendo).copy(alpha = 0.55f)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        resultado.title?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = buildAnnotatedString {
                append("…" + resultado.text.before.orEmpty().takeLast(60).trimStart())
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = resaltado)) {
                    append(resultado.text.highlight.orEmpty())
                }
                append(resultado.text.after.orEmpty().take(60).trimEnd() + "…")
            },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Barra de progreso del boceto: una línea fina con un punto redondo.
 * Se puede tocar en cualquier sitio o arrastrar el punto; al soltar, se salta allí.
 */
@Composable
fun BarraProgreso(
    progreso: Float,
    alMover: (Float) -> Unit,
    alSoltar: () -> Unit,
    modifier: Modifier = Modifier,
    descripcion: String = stringResource(R.string.cd_progreso_libro),
    apagada: Boolean = false,
) {
    val colorLleno = MaterialTheme.colorScheme.primary.copy(alpha = if (apagada) 0.4f else 1f)
    val colorVacio = MaterialTheme.colorScheme.outline
    val colorBorde = MaterialTheme.colorScheme.surface
    var ancho by remember { mutableStateOf(1f) }
    fun proporcion(x: Float) = (x / ancho).coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics { contentDescription = descripcion }
            .onSizeChanged { ancho = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { punto ->
                    alMover(proporcion(punto.x))
                    alSoltar()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { punto -> alMover(proporcion(punto.x)) },
                    onHorizontalDrag = { cambio, _ -> alMover(proporcion(cambio.position.x)) },
                    onDragEnd = alSoltar,
                    onDragCancel = alSoltar,
                )
            }
    ) {
        val y = size.height / 2
        val grosor = 4.dp.toPx()
        val radio = 9.dp.toPx()
        val xPunto = (size.width * progreso).coerceIn(radio, size.width - radio)
        drawLine(colorVacio, Offset(0f, y), Offset(size.width, y), grosor, StrokeCap.Round)
        drawLine(colorLleno, Offset(0f, y), Offset(xPunto, y), grosor, StrokeCap.Round)
        drawCircle(colorBorde, radio + 3.dp.toPx(), Offset(xPunto, y))
        drawCircle(colorLleno, radio, Offset(xPunto, y))
    }
}
