package org.libertalia.reader.lector

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.libertalia.reader.R
import kotlin.math.roundToInt

/**
 * Panel de ajustes de lectura (boceto 9), que sube desde abajo con el botón Aa.
 * Cada cambio se aplica al momento y se guarda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelAjustes(
    ajustes: AjustesLectura,
    esPdf: Boolean,
    /** Solo en el modo texto de un PDF: si se separan las notas al pie (null = no se muestra). */
    notas: Boolean?,
    alCambiarNotas: (Boolean) -> Unit,
    alCambiar: (AjustesLectura) -> Unit,
    alCerrar: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // Sin oscurecer el libro, para ver cómo quedan los cambios.
        scrimColor = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Tema
            Etiqueta(stringResource(R.string.ajustes_tema))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TemaLector.entries.forEach { tema ->
                    BotonTema(
                        tema = tema,
                        elegido = ajustes.tema == tema,
                        alPulsar = { alCambiar(ajustes.copy(tema = tema)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (esPdf) {
                Text(
                    stringResource(R.string.ajustes_nota_pdf),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FilaTamano(ajustes, alCambiar)
                Etiqueta(stringResource(R.string.ajustes_tipografia))
                FilaTipografias(ajustes, alCambiar)
                FilaMargenesEInterlineado(ajustes, alCambiar)
            }

            FilaBrillo(ajustes, alCambiar)

            Column {
                if (notas != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    FilaInterruptor(stringResource(R.string.ajustes_notas), notas, alCambiarNotas)
                    Text(
                        stringResource(R.string.ajustes_notas_explicacion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                FilaInterruptor(stringResource(R.string.ajustes_volumen), ajustes.pasarConVolumen) {
                    alCambiar(ajustes.copy(pasarConVolumen = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                FilaInterruptor(stringResource(R.string.ajustes_pantalla_encendida), ajustes.pantallaEncendida) {
                    alCambiar(ajustes.copy(pantallaEncendida = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                FilaInterruptor(stringResource(R.string.ajustes_filtro_pdf), ajustes.filtroCalidoPdf) {
                    alCambiar(ajustes.copy(filtroCalidoPdf = it))
                }
            }
        }
    }
}

@Composable
private fun Etiqueta(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Botón de un tema, pintado con sus propios colores. El elegido lleva borde marrón. */
@Composable
private fun BotonTema(tema: TemaLector, elegido: Boolean, alPulsar: () -> Unit, modifier: Modifier) {
    val nombre = stringResource(
        when (tema) {
            TemaLector.CREMA -> R.string.tema_crema
            TemaLector.SEPIA -> R.string.tema_sepia
            TemaLector.OSCURO -> R.string.tema_oscuro
        }
    )
    Surface(
        onClick = alPulsar,
        shape = RoundedCornerShape(12.dp),
        color = colorResource(tema.fondo),
        contentColor = colorResource(tema.texto),
        border = BorderStroke(
            if (elegido) 2.5.dp else 1.dp,
            if (elegido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier
            .heightIn(min = 52.dp)
            .semantics { selected = elegido },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
            Text(
                nombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Tamaño de letra: A pequeña, barra y A grande. */
@Composable
private fun FilaTamano(ajustes: AjustesLectura, alCambiar: (AjustesLectura) -> Unit) {
    // Mientras se arrastra, solo se mueve el punto; al soltar, se aplica (así el libro
    // no se recoloca a cada milímetro).
    var arrastrando by remember { mutableStateOf<Float?>(null) }
    fun cambiar(nuevo: Double) = alCambiar(
        ajustes.copy(tamanoLetra = nuevo.coerceIn(AjustesLectura.TAMANO_MINIMO, AjustesLectura.TAMANO_MAXIMO))
    )
    val pasos = ((AjustesLectura.TAMANO_MAXIMO - AjustesLectura.TAMANO_MINIMO) / AjustesLectura.PASO_TAMANO)
        .roundToInt()
    val descripcion = stringResource(R.string.cd_tamano_letra)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Etiqueta(stringResource(R.string.ajustes_tamano), Modifier.widthIn(min = 80.dp).padding(end = 8.dp))
        BotonRedondo("A", 15, stringResource(R.string.cd_letra_menor)) {
            cambiar(ajustes.tamanoLetra - AjustesLectura.PASO_TAMANO)
        }
        val minimo = AjustesLectura.TAMANO_MINIMO.toFloat()
        val rango = (AjustesLectura.TAMANO_MAXIMO - AjustesLectura.TAMANO_MINIMO).toFloat()
        BarraProgreso(
            progreso = ((arrastrando ?: ajustes.tamanoLetra.toFloat()) - minimo) / rango,
            alMover = { proporcion ->
                // Se ajusta al paso más cercano (0,125) para que los tamaños sean redondos.
                val paso = (proporcion * pasos).roundToInt()
                arrastrando = minimo + paso * rango / pasos
            },
            alSoltar = {
                arrastrando?.let { cambiar(it.toDouble()) }
                arrastrando = null
            },
            descripcion = descripcion,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        BotonRedondo("A", 24, stringResource(R.string.cd_letra_mayor)) {
            cambiar(ajustes.tamanoLetra + AjustesLectura.PASO_TAMANO)
        }
    }
}

@Composable
private fun BotonRedondo(letra: String, tamanoSp: Int, descripcion: String, alPulsar: () -> Unit) {
    Surface(
        onClick = alPulsar,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = descripcion },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(letra, fontSize = tamanoSp.sp, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Una pastilla por tipografía, cada una escrita con su propia letra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaTipografias(ajustes: AjustesLectura, alCambiar: (AjustesLectura) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TipografiaLector.entries.forEach { tipografia ->
            val elegida = ajustes.tipografia == tipografia
            val nombre = stringResource(
                when (tipografia) {
                    TipografiaLector.LITERATA -> R.string.tipografia_literata
                    TipografiaLector.LORA -> R.string.tipografia_lora
                    TipografiaLector.SANS -> R.string.tipografia_sans
                    TipografiaLector.DISLEXIA -> R.string.tipografia_dislexia
                }
            )
            Surface(
                onClick = { alCambiar(ajustes.copy(tipografia = tipografia)) },
                shape = RoundedCornerShape(50),
                color = if (elegida) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                contentColor = if (elegida) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                border = if (elegida) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .semantics { selected = elegida },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(nombre, fontFamily = familiaDe(tipografia), fontSize = 16.sp)
                }
            }
        }
    }
}

/** La tipografía como familia de Compose, para escribir su nombre con ella. */
@Composable
private fun familiaDe(tipografia: TipografiaLector): FontFamily {
    val assets = LocalContext.current.assets
    return remember(tipografia, assets) {
        FontFamily(
            tipografia.caras.map { cara ->
                Font(
                    cara.ruta,
                    assets,
                    FontWeight(cara.grosores.first.coerceAtLeast(400).coerceAtMost(cara.grosores.last)),
                    if (cara.cursiva) FontStyle.Italic else FontStyle.Normal,
                )
            }
        )
    }
}

/** Márgenes e interlineado, uno al lado del otro (si no caben, uno debajo del otro). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaMargenesEInterlineado(ajustes: AjustesLectura, alCambiar: (AjustesLectura) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 2,
    ) {
        Column(Modifier.weight(1f).widthIn(min = 140.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiqueta(stringResource(R.string.ajustes_margenes))
            Segmentos(ajustes.margenes, { alCambiar(ajustes.copy(margenes = it)) }) { nivel, color ->
                IconoMargenes(nivel, color)
            }
        }
        Column(Modifier.weight(1f).widthIn(min = 140.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiqueta(stringResource(R.string.ajustes_interlineado))
            Segmentos(ajustes.interlineado, { alCambiar(ajustes.copy(interlineado = it)) }) { nivel, color ->
                IconoInterlineado(nivel, color)
            }
        }
    }
}

/** Tres botones pegados (poco, normal, mucho); el elegido con fondo algo más oscuro. */
@Composable
private fun Segmentos(
    actual: Nivel,
    alElegir: (Nivel) -> Unit,
    icono: @Composable (Nivel, androidx.compose.ui.graphics.Color) -> Unit,
) {
    val forma = RoundedCornerShape(12.dp)
    val borde = MaterialTheme.colorScheme.outline
    val colorIcono = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, borde, forma)
            .background(MaterialTheme.colorScheme.surface, forma)
    ) {
        Nivel.entries.forEachIndexed { i, nivel ->
            val elegido = nivel == actual
            val descripcion = stringResource(
                when (nivel) {
                    Nivel.POCO -> R.string.cd_nivel_poco
                    Nivel.NORMAL -> R.string.cd_nivel_normal
                    Nivel.MUCHO -> R.string.cd_nivel_mucho
                }
            )
            if (i > 0) Box(Modifier.width(1.dp).height(48.dp).background(borde))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(if (elegido) borde.copy(alpha = 0.55f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { alElegir(nivel) }
                    .semantics {
                        contentDescription = descripcion
                        selected = elegido
                        role = Role.RadioButton
                    },
            ) {
                icono(nivel, colorIcono)
            }
        }
    }
}

/** Tres líneas: más largas con pocos márgenes, más cortas con muchos. */
@Composable
private fun IconoMargenes(nivel: Nivel, color: androidx.compose.ui.graphics.Color) {
    val ancho = when (nivel) {
        Nivel.POCO -> 1f
        Nivel.NORMAL -> 0.7f
        Nivel.MUCHO -> 0.4f
    }
    Canvas(Modifier.size(24.dp)) {
        val grosor = 1.8.dp.toPx()
        val largo = size.width * ancho
        val x0 = (size.width - largo) / 2
        listOf(0.3f, 0.5f, 0.7f).forEach { y ->
            drawLine(color, Offset(x0, size.height * y), Offset(x0 + largo, size.height * y), grosor, StrokeCap.Round)
        }
    }
}

/** Tres líneas: más juntas con poco interlineado, más separadas con mucho. */
@Composable
private fun IconoInterlineado(nivel: Nivel, color: androidx.compose.ui.graphics.Color) {
    val separacion = when (nivel) {
        Nivel.POCO -> 0.12f
        Nivel.NORMAL -> 0.22f
        Nivel.MUCHO -> 0.32f
    }
    Canvas(Modifier.size(24.dp)) {
        val grosor = 1.8.dp.toPx()
        listOf(-1, 0, 1).forEach { n ->
            val y = size.height * (0.5f + n * separacion)
            drawLine(color, Offset(size.width * 0.15f, y), Offset(size.width * 0.85f, y), grosor, StrokeCap.Round)
        }
    }
}

/** Brillo dentro de la app. «Auto» vuelve al brillo del móvil. */
@Composable
private fun FilaBrillo(ajustes: AjustesLectura, alCambiar: (AjustesLectura) -> Unit) {
    val descripcion = stringResource(R.string.cd_brillo)
    val descripcionAuto = stringResource(R.string.cd_brillo_auto)
    val auto = ajustes.brillo == null
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Etiqueta(stringResource(R.string.ajustes_brillo), Modifier.widthIn(min = 80.dp).padding(end = 8.dp))
        Canvas(Modifier.size(14.dp)) {
            drawCircle(color, radius = size.minDimension / 3, style = Stroke(1.5.dp.toPx()))
        }
        BarraProgreso(
            progreso = ajustes.brillo ?: 0.5f,
            alMover = { alCambiar(ajustes.copy(brillo = it.coerceIn(0.02f, 1f))) },
            alSoltar = {},
            descripcion = descripcion,
            apagada = auto,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Icon(painterResource(R.drawable.ic_sol), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = { alCambiar(ajustes.copy(brillo = null)) },
            shape = RoundedCornerShape(50),
            color = if (auto) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            contentColor = if (auto) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            border = if (auto) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .heightIn(min = 44.dp)
                .semantics {
                    contentDescription = descripcionAuto
                    selected = auto
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.ajustes_brillo_auto), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FilaInterruptor(texto: String, activado: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { alCambiar(!activado) },
    ) {
        Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = activado,
            onCheckedChange = alCambiar,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
