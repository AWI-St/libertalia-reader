package org.libertalia.reader.ui.tema

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.libertalia.reader.R

/**
 * Literata, la tipografía de toda la app.
 * Es una "fuente variable": un solo archivo contiene todos los grosores,
 * así que pedimos cada grosor (normal, negrita...) al mismo archivo.
 * Los archivos están en assets/fonts para que el lector de EPUB también pueda usarlos.
 */
@Composable
fun familiaLiterata(): FontFamily {
    val assets = LocalContext.current.assets
    return remember(assets) {
        val grosores = listOf(
            FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
            FontWeight.SemiBold, FontWeight.Bold,
        )
        FontFamily(
            grosores.flatMap { grosor ->
                listOf(
                    Font("fonts/Literata.ttf", assets, grosor, FontStyle.Normal),
                    Font("fonts/Literata-Italic.ttf", assets, grosor, FontStyle.Italic),
                )
            }
        )
    }
}

/**
 * Cualquiera de las tipografías incluidas (las mismas que en el lector), para usarla en toda
 * la app. Las fuentes variables cubren varios grosores con un solo archivo.
 */
@Composable
fun familiaDe(tipografia: org.libertalia.reader.lector.TipografiaLector): FontFamily {
    if (tipografia == org.libertalia.reader.lector.TipografiaLector.LITERATA) return familiaLiterata()
    val assets = LocalContext.current.assets
    return remember(assets, tipografia) {
        val grosores = listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
        FontFamily(
            tipografia.caras.flatMap { cara ->
                // Cada grosor pedido va al archivo que lo cubre (o al más cercano).
                grosores.filter { it.weight in cara.grosores || cara.grosores.first == cara.grosores.last }
                    .filter { cara.grosores.first != cara.grosores.last || (it.weight >= 600) == (cara.grosores.first >= 600) }
                    .map { Font(cara.ruta, assets, it, if (cara.cursiva) FontStyle.Italic else FontStyle.Normal) }
            },
        )
    }
}

/** Aplica la misma tipografía a todos los estilos de texto de Material. */
private fun tipografiaCon(familia: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = familia),
        displayMedium = base.displayMedium.copy(fontFamily = familia),
        displaySmall = base.displaySmall.copy(fontFamily = familia),
        headlineLarge = base.headlineLarge.copy(fontFamily = familia),
        headlineMedium = base.headlineMedium.copy(fontFamily = familia),
        headlineSmall = base.headlineSmall.copy(fontFamily = familia),
        titleLarge = base.titleLarge.copy(fontFamily = familia),
        titleMedium = base.titleMedium.copy(fontFamily = familia),
        titleSmall = base.titleSmall.copy(fontFamily = familia),
        bodyLarge = base.bodyLarge.copy(fontFamily = familia),
        bodyMedium = base.bodyMedium.copy(fontFamily = familia),
        bodySmall = base.bodySmall.copy(fontFamily = familia),
        labelLarge = base.labelLarge.copy(fontFamily = familia),
        labelMedium = base.labelMedium.copy(fontFamily = familia),
        labelSmall = base.labelSmall.copy(fontFamily = familia),
    )
}

/**
 * Tema de Libertalia Reader: colores crema y marrón (definidos en res/values/colors.xml)
 * y la tipografía Literata. Todas las pantallas de Compose van dentro de este tema.
 */
@Composable
fun TemaLibertalia(contenido: @Composable () -> Unit) {
    val colores = lightColorScheme(
        primary = colorResource(R.color.marron),
        onPrimary = colorResource(R.color.fondo_tarjeta),
        secondary = colorResource(R.color.madera),
        onSecondary = colorResource(R.color.fondo_tarjeta),
        background = colorResource(R.color.fondo_app),
        onBackground = colorResource(R.color.texto),
        surface = colorResource(R.color.fondo_tarjeta),
        onSurface = colorResource(R.color.texto),
        surfaceVariant = colorResource(R.color.fondo_lectura),
        onSurfaceVariant = colorResource(R.color.texto_secundario),
        outline = colorResource(R.color.borde),
        // Bordes de los botones de contorno y separadores (si no, salen morados).
        outlineVariant = colorResource(R.color.borde),
        // Menús desplegables, hojas y diálogos: crema (si no, salen lilas).
        surfaceContainerLowest = colorResource(R.color.fondo_tarjeta),
        surfaceContainerLow = colorResource(R.color.fondo_tarjeta),
        surfaceContainer = colorResource(R.color.fondo_tarjeta),
        surfaceContainerHigh = colorResource(R.color.fondo_tarjeta),
        surfaceContainerHighest = colorResource(R.color.fondo_lectura),
        // Rellenos de interruptores, deslizadores y chips (si no, salen lilas, sobre todo en oscuro).
        primaryContainer = colorResource(R.color.borde),
        onPrimaryContainer = colorResource(R.color.texto),
        secondaryContainer = colorResource(R.color.borde),
        onSecondaryContainer = colorResource(R.color.texto),
        tertiary = colorResource(R.color.madera),
        onTertiary = colorResource(R.color.fondo_tarjeta),
        error = colorResource(R.color.rojo),
        // Avisos (snackbars) y menús oscuros: marrón muy oscuro, no negro.
        inverseSurface = colorResource(R.color.texto),
        inverseOnSurface = colorResource(R.color.fondo_tarjeta),
        inversePrimary = colorResource(R.color.borde),
    )
    MaterialTheme(
        colorScheme = colores,
        typography = tipografiaCon(familiaDe(org.libertalia.reader.ui.ajustes.AjustesApp.tipografia)),
    ) {
        // El texto, por defecto, en el marrón oscuro de la app (nunca negro puro).
        CompositionLocalProvider(LocalContentColor provides colores.onBackground, content = contenido)
    }
}
