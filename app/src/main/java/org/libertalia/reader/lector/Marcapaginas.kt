package org.libertalia.reader.lector

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.ColorInt
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate

/**
 * Estilo del marcapáginas dentro del EPUB: una cintita de color en el margen izquierdo,
 * a la altura de la frase marcada (como la cinta de tela de un libro de papel).
 */
data class EstiloMarcapaginas(@ColorInt val color: Int) : Decoration.Style {
    override fun describeContents(): Int = 0

    override fun writeToParcel(destino: Parcel, flags: Int) {
        destino.writeInt(color)
    }

    companion object CREATOR : Parcelable.Creator<EstiloMarcapaginas> {
        override fun createFromParcel(origen: Parcel) = EstiloMarcapaginas(origen.readInt())
        override fun newArray(tamano: Int) = arrayOfNulls<EstiloMarcapaginas>(tamano)

        private const val CLASE = "libertalia-marcapaginas"

        /**
         * Cómo dibuja Readium el marcapáginas: un rectángulo con la punta en V,
         * pegado al borde izquierdo de la página y a la altura de la primera línea marcada.
         */
        fun plantilla(): HtmlDecorationTemplate = HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOUNDS,
            width = HtmlDecorationTemplate.Width.PAGE,
            element = { decoracion ->
                val color = (decoracion.style as? EstiloMarcapaginas)?.color ?: 0xFFC8322B.toInt()
                val css = String.format("#%06X", color and 0xFFFFFF)
                """<div><div class="$CLASE" style="background-color: $css !important"></div></div>"""
            },
            stylesheet = """
                .$CLASE {
                    float: left;
                    margin-left: 4px;
                    width: 12px;
                    height: 26px;
                    clip-path: polygon(0 0, 100% 0, 100% 100%, 50% 72%, 0 100%);
                }
            """.trimIndent(),
        )
    }
}
