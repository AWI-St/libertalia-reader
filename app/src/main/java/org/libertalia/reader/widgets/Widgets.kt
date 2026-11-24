package org.libertalia.reader.widgets

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.MainActivity
import org.libertalia.reader.R
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.TipoLibro
import org.libertalia.reader.racha.CalculoRacha
import org.libertalia.reader.racha.EstadoRacha
import org.libertalia.reader.racha.Racha

/** Para volver a dibujar los widgets cuando cambia algo (se lee, se cambia de libro...). */
object Widgets {
    suspend fun actualizar(contexto: Context) {
        runCatching { WidgetRacha().updateAll(contexto) }
        runCatching { WidgetSeguirLeyendo().updateAll(contexto) }
    }
}

// ---------------------------------------------------------------------------------------
// Racha de lectura: el pirata con su brújula, los días seguidos y una frase según el estado.
// ---------------------------------------------------------------------------------------

class WidgetRacha : GlanceAppWidget() {

    // Con el tamaño exacto del widget, el pirata crece si el widget es grande.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val racha = CalculoRacha.actual(context.applicationContext as LibertaliaApp)
        provideContent { ContenidoRacha(racha) }
    }

    companion object {
        val PEQUENO = DpSize(110.dp, 110.dp)
        val ANCHO = DpSize(220.dp, 110.dp)
    }
}

class ReceptorWidgetRacha : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetRacha()
}

@Composable
private fun ContenidoRacha(racha: Racha) {
    val contexto = LocalContext.current
    val tamano = LocalSize.current
    // Apaisado (más ancho que alto): el pirata a la izquierda y el texto a la derecha.
    val ancho = tamano.width >= WidgetRacha.ANCHO.width && tamano.width > tamano.height * 1.4f
    // El pirata (de cuerpo entero, 3 de ancho por 4 de alto) ocupa lo que deja libre el texto.
    val altoPirata = if (ancho) {
        minOf(tamano.height.value - 20, (tamano.width.value * 0.45f) / PROPORCION_PIRATA)
    } else {
        minOf(tamano.height.value - 74, (tamano.width.value - 20) / PROPORCION_PIRATA)
    }.coerceIn(60f, 260f)
    val fotogramas = when (racha.estado) {
        EstadoRacha.CONTENTO -> FOTOGRAMAS_CONTENTO
        EstadoRacha.TRANQUILO -> FOTOGRAMAS_TRANQUILO
        EstadoRacha.ASUSTADO -> FOTOGRAMAS_ASUSTADO
        EstadoRacha.TRISTE -> FOTOGRAMAS_TRISTE
    }
    val frase = contexto.getString(
        when (racha.estado) {
            EstadoRacha.CONTENTO -> R.string.racha_contento
            EstadoRacha.TRANQUILO -> if (racha.dias == 0) R.string.racha_empezar else R.string.racha_tranquilo
            EstadoRacha.ASUSTADO -> R.string.racha_asustado
            EstadoRacha.TRISTE -> R.string.racha_triste
        }
    )
    val dias = contexto.resources.getQuantityString(R.plurals.racha_dias, racha.dias, racha.dias)
    val texto = ColorProvider(R.color.texto)
    val secundario = ColorProvider(R.color.texto_secundario)
    val modificador = GlanceModifier
        .fillMaxSize()
        .cornerRadius(24.dp)
        .background(ColorProvider(R.color.fondo_tarjeta))
        .clickable(actionStartActivity(Intent(contexto, MainActivity::class.java)))
        .padding(10.dp)
    val imagen = @Composable {
        PirataAnimado(
            contexto = contexto,
            fotogramas = fotogramas,
            descripcion = frase,
            modifier = GlanceModifier.width((altoPirata * PROPORCION_PIRATA).dp).height(altoPirata.dp),
        )
    }
    if (ancho) {
        Row(modifier = modificador, verticalAlignment = Alignment.CenterVertically) {
            imagen()
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(
                    contexto.getString(R.string.racha_titulo).uppercase(),
                    style = TextStyle(color = ColorProvider(R.color.marron), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
                Text(dias, style = TextStyle(color = texto, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
                Text(frase, style = TextStyle(color = secundario, fontSize = 13.sp, fontFamily = FontFamily.Serif), maxLines = 2)
            }
        }
    } else {
        Column(modifier = modificador, horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            imagen()
            Text(dias, style = TextStyle(color = texto, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
            Text(frase, style = TextStyle(color = secundario, fontSize = 12.sp, fontFamily = FontFamily.Serif), maxLines = 2)
        }
    }
}

/** Ancho entre alto del dibujo del pirata (120 × 160). */
private const val PROPORCION_PIRATA = 0.75f

// Los 4 fotogramas de cada estado (los dibuja herramientas/crear_pirata.py).
private val FOTOGRAMAS_CONTENTO = listOf(
    R.drawable.pirata_contento_1, R.drawable.pirata_contento_2, R.drawable.pirata_contento_3, R.drawable.pirata_contento_4,
)
private val FOTOGRAMAS_TRANQUILO = listOf(
    R.drawable.pirata_tranquilo_1, R.drawable.pirata_tranquilo_2, R.drawable.pirata_tranquilo_3, R.drawable.pirata_tranquilo_4,
)
private val FOTOGRAMAS_ASUSTADO = listOf(
    R.drawable.pirata_asustado_1, R.drawable.pirata_asustado_2, R.drawable.pirata_asustado_3, R.drawable.pirata_asustado_4,
)
private val FOTOGRAMAS_TRISTE = listOf(
    R.drawable.pirata_triste_1, R.drawable.pirata_triste_2, R.drawable.pirata_triste_3, R.drawable.pirata_triste_4,
)
private val HUECOS_FOTOGRAMA = listOf(
    R.id.pirata_fotograma_1, R.id.pirata_fotograma_2, R.id.pirata_fotograma_3, R.id.pirata_fotograma_4,
)

/**
 * El pirata animado. Glance no sabe animar, así que se usa una vista clásica de Android
 * (un ViewFlipper, que el propio lanzador va pasando de un dibujo al siguiente).
 */
@Composable
private fun PirataAnimado(contexto: Context, fotogramas: List<Int>, descripcion: String, modifier: GlanceModifier) {
    val vistas = RemoteViews(contexto.packageName, R.layout.widget_pirata_animado).apply {
        HUECOS_FOTOGRAMA.zip(fotogramas).forEach { (hueco, dibujo) ->
            setImageViewResource(hueco, dibujo)
            setContentDescription(hueco, descripcion)
        }
    }
    AndroidRemoteViews(remoteViews = vistas, modifier = modifier)
}

// ---------------------------------------------------------------------------------------
// Seguir leyendo: el libro o cómic actual, su capítulo y página y una barra de progreso
// (sin porcentaje ni minutos). Al tocarlo se abre ese libro.
// ---------------------------------------------------------------------------------------

class WidgetSeguirLeyendo : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val libro = (context.applicationContext as LibertaliaApp).baseDatos.libros().ultimoLeido()
        provideContent { ContenidoSeguirLeyendo(libro) }
    }
}

class ReceptorWidgetSeguirLeyendo : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetSeguirLeyendo()
}

@Composable
private fun ContenidoSeguirLeyendo(libro: Libro?) {
    val contexto = LocalContext.current
    val claro = ColorProvider(R.color.fondo_tarjeta)
    val accion = if (libro != null) {
        actionStartActivity(MainActivity.abrirLibroIntent(contexto, libro.id))
    } else {
        actionStartActivity(Intent(contexto, MainActivity::class.java))
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(ColorProvider(R.color.marron))
            .clickable(accion)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                contexto.getString(R.string.seguir_leyendo).uppercase(),
                style = TextStyle(color = ColorProvider(R.color.borde), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            if (libro == null) {
                Text(
                    contexto.getString(R.string.widget_sin_libro),
                    style = TextStyle(color = claro, fontSize = 14.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif),
                )
            } else {
                Text(
                    libro.titulo,
                    style = TextStyle(color = claro, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
                    maxLines = 2,
                )
                // Capítulo y página en líneas separadas, para que no se corte la página.
                lineasProgreso(contexto, libro).forEach {
                    Text(it, style = TextStyle(color = ColorProvider(R.color.borde), fontSize = 12.sp, fontFamily = FontFamily.Serif), maxLines = 1)
                }
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = libro.progreso.toFloat().coerceIn(0f, 1f),
                    color = ColorProvider(R.color.estado_leyendo),
                    backgroundColor = ColorProvider(R.color.madera),
                    modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                )
            }
        }
    }
}

/** «Cap. I» y «Pág. 34 de 210» (en cómics, solo la página). Nunca porcentaje. */
private fun lineasProgreso(contexto: Context, libro: Libro): List<String> {
    val pagina = libro.paginaLector
    val paginas = libro.paginasLector
    val textoPagina = if (pagina != null && paginas != null && paginas > 0) {
        contexto.getString(R.string.lector_pagina, pagina.coerceIn(1, paginas), paginas)
    } else {
        null
    }
    val capitulo = libro.capituloActual?.takeIf { it.isNotBlank() && libro.tipo != TipoLibro.COMIC }
    return listOfNotNull(capitulo, textoPagina)
}
