package org.libertalia.reader.comic

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Recorte automático de los márgenes blancos de las páginas escaneadas: así el dibujo
 * ocupa más pantalla. Se miran las filas y columnas de los bordes hacia dentro y se quitan
 * las que son casi blancas (se perdona algo de polvo o manchas del escáner).
 */
object Recorte {

    /** Como mucho se quita esta parte de cada lado (para no comerse nunca el dibujo). */
    private const val TOPE_POR_LADO = 0.2f

    /** Un píxel con todos sus colores por encima de esto se considera blanco. */
    private const val CLARIDAD_BLANCO = 222

    /** Una fila o columna con menos de esta proporción de píxeles no blancos está «en blanco». */
    private const val MANCHAS_PERMITIDAS = 0.01f

    /** Se deja un poquito de margen (en proporción) para que el dibujo no quede pegado al borde. */
    private const val MARGEN_QUE_SE_DEJA = 0.006f

    /** La página sin sus márgenes blancos (o la misma, si no los tiene). */
    fun recortar(imagen: Bitmap): Bitmap {
        val ancho = imagen.width
        val alto = imagen.height
        if (ancho < 32 || alto < 32) return imagen

        val fila = IntArray(ancho)
        val columna = IntArray(alto)
        fun filaEnBlanco(y: Int, desde: Int, hasta: Int): Boolean {
            imagen.getPixels(fila, 0, ancho, desde, y, hasta - desde, 1)
            return casiBlanca(fila, hasta - desde)
        }
        fun columnaEnBlanco(x: Int, desde: Int, hasta: Int): Boolean {
            imagen.getPixels(columna, 0, 1, x, desde, 1, hasta - desde)
            return casiBlanca(columna, hasta - desde)
        }

        val topeAlto = (alto * TOPE_POR_LADO).toInt()
        val topeAncho = (ancho * TOPE_POR_LADO).toInt()
        var arriba = 0
        while (arriba < topeAlto && filaEnBlanco(arriba, 0, ancho)) arriba++
        var abajo = alto
        while (alto - abajo < topeAlto && filaEnBlanco(abajo - 1, 0, ancho)) abajo--
        var izquierda = 0
        while (izquierda < topeAncho && columnaEnBlanco(izquierda, arriba, abajo)) izquierda++
        var derecha = ancho
        while (ancho - derecha < topeAncho && columnaEnBlanco(derecha - 1, arriba, abajo)) derecha--

        // Un poco de aire alrededor del dibujo.
        val aireX = (ancho * MARGEN_QUE_SE_DEJA).toInt()
        val aireY = (alto * MARGEN_QUE_SE_DEJA).toInt()
        arriba = (arriba - aireY).coerceAtLeast(0)
        abajo = (abajo + aireY).coerceAtMost(alto)
        izquierda = (izquierda - aireX).coerceAtLeast(0)
        derecha = (derecha + aireX).coerceAtMost(ancho)

        // Si apenas hay margen, no merece la pena hacer otra imagen.
        val quitado = (ancho - (derecha - izquierda)).toFloat() / ancho + (alto - (abajo - arriba)).toFloat() / alto
        if (quitado < 0.02f) return imagen
        val recortada = Bitmap.createBitmap(imagen, izquierda, arriba, derecha - izquierda, abajo - arriba)
        if (recortada !== imagen) imagen.recycle()
        return recortada
    }

    private fun casiBlanca(pixeles: IntArray, cuantos: Int): Boolean {
        val permitidas = (cuantos * MANCHAS_PERMITIDAS).toInt()
        var manchas = 0
        for (i in 0 until cuantos) {
            val p = pixeles[i]
            if (Color.red(p) < CLARIDAD_BLANCO || Color.green(p) < CLARIDAD_BLANCO || Color.blue(p) < CLARIDAD_BLANCO) {
                manchas++
                if (manchas > permitidas) return false
            }
        }
        return true
    }
}
