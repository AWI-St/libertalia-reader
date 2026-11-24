package org.libertalia.reader.datos

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Libro de texto o cómic. Decide qué lector se usa y si tiene tarjetas. */
enum class TipoLibro { TEXTO, COMIC }

/** Formato del archivo. */
enum class Formato { EPUB, PDF, CBZ, CBR }

/** En qué punto de la lectura está. */
enum class EstadoLectura { LEYENDO, PENDIENTE, TERMINADO }

/**
 * Un libro o cómic de la biblioteca.
 *
 * - [id]: identificador único que no cambia nunca (aunque el archivo cambie de nombre).
 * - [huella]: SHA-256 del contenido del archivo; sirve para reconocerlo al importar copias.
 * - [uriArchivo]: dónde está el archivo dentro de la carpeta de la app. Es null si el libro
 *   llegó desde una copia sin su archivo ("sin archivo").
 * - [titulo], [autor], [tomo]: tal como los ve y edita el usuario.
 * - [posicion]: posición exacta de Readium (en JSON) para volver al mismo sitio.
 */
@Entity(
    tableName = "libros",
    indices = [Index(value = ["huella"], unique = true)],
)
data class Libro(
    @PrimaryKey val id: String,
    val tipo: TipoLibro,
    val formato: Formato,
    val huella: String,
    val titulo: String,
    val autor: String,
    val tomo: Int? = null,
    val tituloOriginal: String,
    val autorOriginal: String,
    val uriArchivo: String?,
    val nombreArchivo: String?,
    val tamanoBytes: Long,
    val tienePortada: Boolean,
    val estado: EstadoLectura = EstadoLectura.PENDIENTE,
    val posicion: String? = null,
    /** De 0 a 1: cuánto se ha leído del libro. */
    val progreso: Double = 0.0,
    /** Título del capítulo por el que se va (para «Seguir leyendo»). */
    val capituloActual: String? = null,
    val incluirEnTarjetaDelDia: Boolean = true,
    val incluirArchivoEnCopia: Boolean = true,
    /** Solo cómics: "izquierda_a_derecha" o "derecha_a_izquierda". */
    val sentidoLectura: String? = null,
    /** Número de páginas de la edición impresa (para la página estimada). */
    val paginasImpresas: Int? = null,
    val isbn: String? = null,
    val idioma: String? = null,
    val fechaAnadido: Long,
    val fechaUltimaLectura: Long? = null,
    val fechaModificacion: Long,
    /** Cuándo se marcó como terminado (para las estadísticas); null si no está terminado. */
    val fechaTerminado: Long? = null,
    /**
     * El usuario lo quitó de la biblioteca. No se borra de verdad: así, si vuelve
     * a añadir el mismo libro (misma huella), recupera su página, notas y tarjetas.
     */
    @ColumnInfo(defaultValue = "0")
    val eliminado: Boolean = false,
    /**
     * Tamaño de letra, tipografía, márgenes e interlineado de este libro
     * (cada libro tiene los suyos). Null = todavía no se han cambiado.
     */
    val ajustesTexto: String? = null,
    /**
     * Marcapáginas que pone el usuario al seleccionar texto: posición exacta de Readium
     * (en JSON) de la frase marcada. Al abrir el libro se va ahí.
     */
    val marcapaginas: String? = null,
    /**
     * Página del lector por la que se va y total de páginas («Pág. 34 de 210»), tal como
     * se vio la última vez en el lector (para «Seguir leyendo»). En EPUB dependen del
     * tamaño de letra; en PDF y cómics son las páginas del archivo.
     */
    val paginaLector: Int? = null,
    val paginasLector: Int? = null,
)
