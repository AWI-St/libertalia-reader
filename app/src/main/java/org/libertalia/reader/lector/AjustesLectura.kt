package org.libertalia.reader.lector

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.edit
import org.libertalia.reader.R

/** Tema del lector: colores de fondo y de texto. Nunca blanco brillante. */
enum class TemaLector(
    @ColorRes val fondo: Int,
    @ColorRes val texto: Int,
    @ColorRes val barra: Int,
    @ColorRes val textoSecundario: Int,
    @ColorRes val borde: Int,
    val oscuro: Boolean,
) {
    CREMA(R.color.lector_crema_fondo, R.color.lector_crema_texto, R.color.lector_crema_barra, R.color.lector_claro_texto_secundario, R.color.lector_crema_borde, false),
    SEPIA(R.color.lector_sepia_fondo, R.color.lector_sepia_texto, R.color.lector_sepia_barra, R.color.lector_claro_texto_secundario, R.color.lector_sepia_borde, false),
    OSCURO(R.color.lector_oscuro_fondo, R.color.lector_oscuro_texto, R.color.lector_oscuro_barra, R.color.lector_oscuro_texto_secundario, R.color.lector_oscuro_borde, true),
}

/**
 * Tipografías del lector, todas incluidas en la app (licencia SIL OFL).
 * [nombre] es el nombre que se usa dentro del EPUB; [caras] son los archivos de assets/fonts.
 */
enum class TipografiaLector(val nombre: String, val caras: List<CaraTipografia>) {
    LITERATA(
        "Literata",
        listOf(
            CaraTipografia("fonts/Literata.ttf", cursiva = false, grosores = 200..900),
            CaraTipografia("fonts/Literata-Italic.ttf", cursiva = true, grosores = 200..900),
        ),
    ),
    LORA(
        "Lora",
        listOf(
            CaraTipografia("fonts/Lora.ttf", cursiva = false, grosores = 400..700),
            CaraTipografia("fonts/Lora-Italic.ttf", cursiva = true, grosores = 400..700),
        ),
    ),
    SANS(
        "Atkinson Hyperlegible",
        listOf(
            CaraTipografia("fonts/AtkinsonHyperlegible-Regular.ttf", cursiva = false, grosores = 400..400),
            CaraTipografia("fonts/AtkinsonHyperlegible-Bold.ttf", cursiva = false, grosores = 700..700),
            CaraTipografia("fonts/AtkinsonHyperlegible-Italic.ttf", cursiva = true, grosores = 400..400),
            CaraTipografia("fonts/AtkinsonHyperlegible-BoldItalic.ttf", cursiva = true, grosores = 700..700),
        ),
    ),
    DISLEXIA(
        "OpenDyslexic",
        listOf(
            CaraTipografia("fonts/OpenDyslexic-Regular.otf", cursiva = false, grosores = 400..400),
            CaraTipografia("fonts/OpenDyslexic-Bold.otf", cursiva = false, grosores = 700..700),
            CaraTipografia("fonts/OpenDyslexic-Italic.otf", cursiva = true, grosores = 400..400),
            CaraTipografia("fonts/OpenDyslexic-BoldItalic.otf", cursiva = true, grosores = 700..700),
        ),
    ),
}

/** Un archivo de tipografía: normal o cursiva, y qué grosores cubre. */
data class CaraTipografia(val ruta: String, val cursiva: Boolean, val grosores: IntRange)

/** Tres opciones para márgenes e interlineado (boceto 9). */
enum class Nivel { POCO, NORMAL, MUCHO }

/**
 * Ajustes de lectura (panel Aa). Son los mismos para todos los libros.
 * El tamaño, la tipografía, los márgenes y el interlineado solo afectan a los EPUB.
 */
data class AjustesLectura(
    val tema: TemaLector = TemaLector.CREMA,
    /** 1.0 es el tamaño normal; de 0.75 a 1.625. */
    val tamanoLetra: Double = 1.0,
    val tipografia: TipografiaLector = TipografiaLector.LITERATA,
    val margenes: Nivel = Nivel.NORMAL,
    val interlineado: Nivel = Nivel.NORMAL,
    /** Brillo de 0 a 1 dentro de la app; null = el brillo del móvil. */
    val brillo: Float? = null,
    val pasarConVolumen: Boolean = true,
    val pantallaEncendida: Boolean = true,
    val filtroCalidoPdf: Boolean = true,
) {
    companion object {
        const val TAMANO_MINIMO = 0.75
        const val TAMANO_MAXIMO = 1.625
        const val PASO_TAMANO = 0.125

        private const val ARCHIVO = "lector"

        fun cargar(contexto: Context): AjustesLectura {
            val p = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            val base = AjustesLectura()
            return AjustesLectura(
                tema = enumOr(p.getString("tema", null), base.tema),
                tamanoLetra = p.getFloat("tamano_letra", base.tamanoLetra.toFloat()).toDouble()
                    .coerceIn(TAMANO_MINIMO, TAMANO_MAXIMO),
                tipografia = enumOr(p.getString("tipografia", null), base.tipografia),
                margenes = enumOr(p.getString("margenes", null), base.margenes),
                interlineado = enumOr(p.getString("interlineado", null), base.interlineado),
                brillo = p.getFloat("brillo", -1f).takeIf { it >= 0f },
                pasarConVolumen = p.getBoolean("pasar_con_volumen", base.pasarConVolumen),
                pantallaEncendida = p.getBoolean("pantalla_encendida", base.pantallaEncendida),
                filtroCalidoPdf = p.getBoolean("filtro_calido_pdf", base.filtroCalidoPdf),
            )
        }

        /**
         * Los ajustes para abrir un libro: el tema, el brillo y los interruptores son
         * de toda la app; la letra, los márgenes y el interlineado son de cada libro.
         * Un libro que nunca se ha ajustado empieza con los últimos que se usaron.
         */
        fun paraLibro(contexto: Context, ajustesDelLibro: String?): AjustesLectura =
            cargar(contexto).conTexto(ajustesDelLibro)

        private inline fun <reified T : Enum<T>> enumOr(nombre: String?, porDefecto: T): T =
            nombre?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: porDefecto
    }

    fun guardar(contexto: Context) {
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit {
            putString("tema", tema.name)
            putFloat("tamano_letra", tamanoLetra.toFloat())
            putString("tipografia", tipografia.name)
            putString("margenes", margenes.name)
            putString("interlineado", interlineado.name)
            putFloat("brillo", brillo ?: -1f)
            putBoolean("pasar_con_volumen", pasarConVolumen)
            putBoolean("pantalla_encendida", pantallaEncendida)
            putBoolean("filtro_calido_pdf", filtroCalidoPdf)
        }
    }

    /**
     * Los ajustes que van con cada libro, en una sola línea para guardarlos en la
     * base de datos: «tamaño|tipografía|márgenes|interlineado».
     */
    val textoCodificado: String
        get() = "$tamanoLetra|${tipografia.name}|${margenes.name}|${interlineado.name}"

    /** Los mismos ajustes, pero con el texto (tamaño, tipografía...) guardado de un libro. */
    fun conTexto(codificado: String?): AjustesLectura {
        val partes = codificado?.split('|') ?: return this
        if (partes.size < 4) return this
        return copy(
            tamanoLetra = partes[0].toDoubleOrNull()?.coerceIn(TAMANO_MINIMO, TAMANO_MAXIMO) ?: tamanoLetra,
            tipografia = enumOr(partes[1], tipografia),
            margenes = enumOr(partes[2], margenes),
            interlineado = enumOr(partes[3], interlineado),
        )
    }

    /** ¿Cambia algo de lo que va con cada libro? */
    fun mismoTexto(otros: AjustesLectura): Boolean = textoCodificado == otros.textoCodificado

    /** Márgenes de la página en EPUB (valor de Readium). */
    val margenesEpub: Double
        get() = when (margenes) {
            Nivel.POCO -> 0.5
            Nivel.NORMAL -> 1.0
            Nivel.MUCHO -> 1.6
        }

    /** Altura de línea en EPUB. */
    val interlineadoEpub: Double
        get() = when (interlineado) {
            Nivel.POCO -> 1.3
            Nivel.NORMAL -> 1.55
            Nivel.MUCHO -> 1.85
        }
}
