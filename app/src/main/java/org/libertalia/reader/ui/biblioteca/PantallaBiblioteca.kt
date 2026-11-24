package org.libertalia.reader.ui.biblioteca

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.libertalia.reader.R
import org.libertalia.reader.biblioteca.ResultadoImportacion
import org.libertalia.reader.biblioteca.ResumenImportacion
import org.libertalia.reader.biblioteca.FiltroBiblioteca
import org.libertalia.reader.datos.Estanteria
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.ui.tema.TemaLibertalia

/**
 * Pantalla principal. Si no hay libros, muestra la biblioteca vacía (boceto 10).
 * Si hay libros: filtros, «Seguir leyendo» y los estantes de madera (boceto 1).
 */
@Composable
fun PantallaBiblioteca(
    libros: List<Libro>?,
    estanterias: List<Estanteria>,
    librosPorEstanteria: Map<String, Set<String>>,
    filtro: FiltroBiblioteca,
    seguirLeyendo: Libro?,
    importando: Boolean,
    resumen: ResumenImportacion?,
    mensaje: String?,
    alAnadir: () -> Unit,
    alImportarCopia: () -> Unit = {},
    alAjustes: () -> Unit,
    alAbrir: (Libro) -> Unit,
    alElegirFiltro: (FiltroBiblioteca) -> Unit,
    acciones: AccionesLibro,
    accionesEstanterias: AccionesEstanterias,
    alResumenMostrado: () -> Unit,
    alMensajeMostrado: () -> Unit,
    /** Modo Tarjetas (null en modo Libros): cuántas tarjetas tiene cada libro. */
    conteoTarjetas: Map<String, Int>? = null,
    alAlternarModo: () -> Unit = {},
    alBuscar: () -> Unit = {},
    buscadorEtiquetas: @Composable () -> Unit = {},
) {
    val avisos = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val recursos = LocalContext.current.resources
    // Libro cuyo menú está abierto (por su id, para ver siempre sus datos al día).
    var idLibroMenu by rememberSaveable { mutableStateOf<String?>(null) }
    val libroMenu = libros?.find { it.id == idLibroMenu }
    var organizando by rememberSaveable { mutableStateOf(false) }

    // Al terminar de añadir libros, un aviso abajo con lo que ha pasado.
    LaunchedEffect(resumen) {
        if (resumen == null) return@LaunchedEffect
        val partes = listOf(
            ResultadoImportacion.ANADIDO to R.plurals.importacion_anadidos,
            ResultadoImportacion.RECUPERADO to R.plurals.importacion_recuperados,
            ResultadoImportacion.COMPLETADO to R.plurals.importacion_completados,
            ResultadoImportacion.YA_EXISTIA to R.plurals.importacion_ya_existian,
            ResultadoImportacion.NO_COMPATIBLE to R.plurals.importacion_no_compatibles,
            ResultadoImportacion.RAR5 to R.plurals.importacion_rar5,
            ResultadoImportacion.ERROR to R.plurals.importacion_errores,
        ).mapNotNull { (resultado, plural) ->
            val n = resumen.de(resultado)
            if (n > 0) recursos.getQuantityString(plural, n, n) else null
        }
        alResumenMostrado()
        // Se muestra aparte para que no se cancele al marcar el resumen como visto.
        if (partes.isNotEmpty()) alcance.launch { avisos.showSnackbar(partes.joinToString(". ")) }
    }
    LaunchedEffect(mensaje) {
        if (mensaje == null) return@LaunchedEffect
        alMensajeMostrado()
        alcance.launch { avisos.showSnackbar(mensaje) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(avisos) },
    ) { margenes ->
        Column(Modifier.fillMaxSize().padding(margenes)) {
            Cabecera(
                titulo = stringResource(if (conteoTarjetas != null) R.string.titulo_tarjetas else R.string.biblioteca_titulo),
                mostrarAnadir = !libros.isNullOrEmpty(),
                alAnadir = alAnadir,
                alAjustes = alAjustes,
                alBuscar = alBuscar,
            )
            if (importando) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                Text(
                    text = stringResource(R.string.importando),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            when {
                libros == null -> Unit // cargando: un instante, no hace falta mostrar nada
                libros.isEmpty() -> BibliotecaVacia(importando = importando, alAnadir = alAnadir, alImportarCopia = alImportarCopia)
                else -> Estanterias(
                    libros = libros,
                    estanterias = estanterias,
                    librosPorEstanteria = librosPorEstanteria,
                    filtro = filtro,
                    seguirLeyendo = seguirLeyendo,
                    alAbrir = alAbrir,
                    alMantener = { idLibroMenu = it.id },
                    alElegirFiltro = alElegirFiltro,
                    alOrganizar = { organizando = true },
                    conteoTarjetas = conteoTarjetas,
                    alAlternarModo = alAlternarModo,
                    buscadorEtiquetas = buscadorEtiquetas,
                )
            }
        }
    }

    if (organizando) {
        OrganizarEstanterias(
            estanterias = estanterias,
            acciones = accionesEstanterias,
            alCerrar = { organizando = false },
        )
    }
    if (libroMenu != null) {
        MenuLibro(
            libro = libroMenu,
            estanterias = estanterias,
            estanteriasDelLibro = librosPorEstanteria.filterValues { libroMenu.id in it }.keys,
            acciones = acciones,
            alCerrar = { idLibroMenu = null },
        )
    }
}

@Composable
private fun Cabecera(
    titulo: String,
    mostrarAnadir: Boolean,
    alAnadir: () -> Unit,
    alAjustes: () -> Unit,
    alBuscar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        // Ajustes (copias, exportar...).
        IconButton(onClick = alAjustes, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_ajustes),
                contentDescription = stringResource(R.string.cd_ajustes),
                modifier = Modifier.size(28.dp),
            )
        }
        if (mostrarAnadir) {
            // Buscar en las tarjetas de todos los libros.
            IconButton(onClick = alBuscar, modifier = Modifier.size(56.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_buscar),
                    contentDescription = stringResource(R.string.cd_buscar_tarjetas),
                    modifier = Modifier.size(28.dp),
                )
            }
            FilledIconButton(
                onClick = alAnadir,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_anadir),
                    contentDescription = stringResource(R.string.cd_anadir_libros),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun BibliotecaVacia(importando: Boolean, alAnadir: () -> Unit, alImportarCopia: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EstanteVacio(Modifier.fillMaxWidth().height(180.dp))
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vacia_titulo),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.vacia_mensaje),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = alAnadir,
            enabled = !importando,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(painterResource(R.drawable.ic_anadir), contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.boton_anadir_desde_movil), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        // Para cambiar de móvil: recuperar la biblioteca de una copia .libertalia.
        OutlinedButton(
            onClick = alImportarCopia,
            enabled = !importando,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(painterResource(R.drawable.ic_guardar_en), contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.vacia_importar), textAlign = TextAlign.Center)
        }
    }
}

/** Dibujo de la biblioteca vacía: tres libros "fantasma" sobre un estante de madera. */
@Composable
private fun EstanteVacio(modifier: Modifier = Modifier) {
    val relleno = colorResource(R.color.borde)
    val trazo = colorResource(R.color.texto_secundario).copy(alpha = 0.5f)
    val madera = colorResource(R.color.madera)
    Canvas(modifier) {
        val unidad = size.height / 180f
        val anchoEstante = minOf(size.width, 300.dp.toPx())
        val izquierda = (size.width - anchoEstante) / 2
        val baseLibros = size.height - 28 * unidad
        val discontinuo = PathEffect.dashPathEffect(floatArrayOf(6f * unidad, 4f * unidad))

        fun libro(x: Float, ancho: Float, alto: Float) {
            val origen = Offset(x, baseLibros - alto)
            val tamano = Size(ancho, alto)
            drawRoundRect(relleno, origen, tamano, CornerRadius(3 * unidad))
            drawRoundRect(
                trazo, origen, tamano, CornerRadius(3 * unidad),
                style = Stroke(width = 1.5f * unidad, pathEffect = discontinuo),
            )
        }
        val centro = size.width / 2
        libro(centro - 50 * unidad, 26 * unidad, 96 * unidad)
        libro(centro - 18 * unidad, 30 * unidad, 112 * unidad)
        rotate(degrees = -10f, pivot = Offset(centro + 30 * unidad, baseLibros)) {
            libro(centro + 18 * unidad, 26 * unidad, 88 * unidad)
        }
        // Estante con su sombra.
        drawRoundRect(
            color = madera.copy(alpha = 0.18f),
            topLeft = Offset(izquierda + 6 * unidad, baseLibros + 10 * unidad),
            size = Size(anchoEstante - 12 * unidad, 14 * unidad),
            cornerRadius = CornerRadius(7 * unidad),
        )
        drawRoundRect(
            color = madera,
            topLeft = Offset(izquierda, baseLibros),
            size = Size(anchoEstante, 14 * unidad),
            cornerRadius = CornerRadius(3 * unidad),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3EADB, heightDp = 760)
@Composable
private fun VistaPreviaBibliotecaVacia() {
    TemaLibertalia {
        Box {
            PantallaBiblioteca(
                libros = emptyList(), estanterias = emptyList(), librosPorEstanteria = emptyMap(),
                filtro = FiltroBiblioteca.Todos, seguirLeyendo = null, importando = false,
                resumen = null, mensaje = null, alAnadir = {}, alAjustes = {}, alAbrir = {}, alElegirFiltro = {},
                acciones = AccionesLibro({ _, _ -> }, { _, _, _, _, _ -> }, { _, _ -> }, { _, _, _ -> }, { _, _ -> }),
                accionesEstanterias = AccionesEstanterias({ _, _ -> }, { _, _ -> }, {}),
                alResumenMostrado = {}, alMensajeMostrado = {},
            )
        }
    }
}
