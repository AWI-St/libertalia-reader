package org.libertalia.reader.datos

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.libertalia.reader.R

/** Una tarjeta nace de un subrayado o de una página destacada. */
enum class TipoTarjeta { SUBRAYADO, PAGINA }

/** Los cuatro colores de subrayado (pastel, como en los bocetos). */
enum class ColorSubrayado(@ColorRes val color: Int, @StringRes val nombre: Int) {
    AMARILLO(R.color.subrayado_amarillo, R.string.color_amarillo),
    VERDE(R.color.subrayado_verde, R.string.color_verde),
    AZUL(R.color.subrayado_azul, R.string.color_azul),
    ROSA(R.color.subrayado_rosa, R.string.color_rosa),
}

/**
 * Una tarjeta: un subrayado o una página destacada de un libro de texto.
 *
 * Guarda su propio texto completo (no solo una referencia al libro), para poder verla
 * aunque el archivo del libro no esté. El título y el autor no se copian: se toman del
 * libro, así que si el usuario los cambia, todas sus tarjetas cambian solas.
 *
 * - [texto]: lo subrayado (en una página destacada, el principio de la página).
 * - [contexto]: la frase completa donde está el subrayado (de punto a punto).
 * - [descripcion]: solo páginas destacadas: una descripción breve que hace de título.
 * - [nota]: nota opcional del usuario (solo subrayados). Se ve al pie de la tarjeta.
 * - [posicion]: posición exacta de Readium (JSON) para volver justo a ese punto del libro.
 * - [progreso]: de 0 a 1, para ordenar las tarjetas por su sitio en el libro.
 * - [eliminada]: borrada por el usuario. Se guarda oculta para que, al importar una copia
 *   antigua, no vuelva a aparecer.
 */
@Entity(
    tableName = "tarjetas",
    indices = [Index(value = ["idLibro"])],
)
data class Tarjeta(
    @PrimaryKey val id: String,
    val idLibro: String,
    val tipo: TipoTarjeta,
    val color: ColorSubrayado? = null,
    val texto: String,
    val contexto: String? = null,
    val descripcion: String? = null,
    val nota: String? = null,
    /** Etiquetas libres separadas por comas (se usarán en la fase 8). */
    val etiquetas: String? = null,
    val capitulo: String? = null,
    /** Página del libro (en PDF, la del archivo); null si no se sabe. */
    val pagina: Int? = null,
    val progreso: Double = 0.0,
    val posicion: String,
    val fechaCreacion: Long,
    val fechaModificacion: Long,
    @ColumnInfo(defaultValue = "0")
    val eliminada: Boolean = false,
    /**
     * «Unir con otro subrayado»: varios subrayados forman una sola tarjeta. El primero (el que
     * va antes en el libro) es la tarjeta «principal» y tiene idGrupo = null; los demás guardan
     * aquí el id de la principal. Cada subrayado sigue pintado en su sitio del libro.
     */
    val idGrupo: String? = null,
)
