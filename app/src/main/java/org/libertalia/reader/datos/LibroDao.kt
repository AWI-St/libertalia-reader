package org.libertalia.reader.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Las consultas a la base de datos sobre libros. */
@Dao
interface LibroDao {

    /** Los libros de la biblioteca (sin los eliminados); la lista se actualiza sola. */
    @Query("SELECT * FROM libros WHERE eliminado = 0 ORDER BY titulo COLLATE NOCASE")
    fun observarTodos(): Flow<List<Libro>>

    /** Los libros de la biblioteca de una vez (para exportar y para las copias). */
    @Query("SELECT * FROM libros WHERE eliminado = 0 ORDER BY titulo COLLATE NOCASE")
    suspend fun todos(): List<Libro>

    @Query("SELECT * FROM libros WHERE id = :id")
    suspend fun buscarPorId(id: String): Libro?

    /** El último libro o cómic abierto que no está terminado (el de «Seguir leyendo»). */
    @Query(
        """SELECT * FROM libros WHERE eliminado = 0 AND fechaUltimaLectura IS NOT NULL
           AND estado != 'TERMINADO' ORDER BY fechaUltimaLectura DESC LIMIT 1"""
    )
    suspend fun ultimoLeido(): Libro?

    /** Un libro, que se actualiza solo si cambian sus datos (título, autor...). */
    @Query("SELECT * FROM libros WHERE id = :id")
    fun observar(id: String): Flow<Libro?>

    @Query("SELECT * FROM libros WHERE huella = :huella")
    suspend fun buscarPorHuella(huella: String): Libro?

    @Insert
    suspend fun insertar(libro: Libro)

    @Update
    suspend fun actualizar(libro: Libro)

    /**
     * Quita el libro de la biblioteca. Sus datos se guardan ocultos para recuperarlos
     * si el usuario vuelve a añadir el mismo libro.
     */
    @Query("UPDATE libros SET eliminado = 1, fechaModificacion = :fecha WHERE id = :id")
    suspend fun eliminar(id: String, fecha: Long)

    /** Al pasar a terminado se apunta la fecha (si ya lo estaba, se conserva la de entonces). */
    @Query(
        """UPDATE libros SET estado = :estado, fechaModificacion = :fecha,
           fechaTerminado = CASE WHEN :estado = 'TERMINADO'
               THEN COALESCE(CASE WHEN estado = 'TERMINADO' THEN fechaTerminado END, :fecha)
               ELSE NULL END
           WHERE id = :id"""
    )
    suspend fun cambiarEstado(id: String, estado: EstadoLectura, fecha: Long)

    /** Guarda por dónde se va leyendo (y «Pág. X de Y», si se sabe). */
    @Query(
        """UPDATE libros SET posicion = :posicion, progreso = :progreso,
           capituloActual = :capitulo, fechaUltimaLectura = :fecha,
           paginaLector = :pagina, paginasLector = :paginas WHERE id = :id"""
    )
    suspend fun guardarPosicion(
        id: String,
        posicion: String,
        progreso: Double,
        capitulo: String?,
        fecha: Long,
        pagina: Int?,
        paginas: Int?,
    )

    /** «Incluir el archivo en la copia automática». */
    @Query("UPDATE libros SET incluirArchivoEnCopia = :incluir, fechaModificacion = :fecha WHERE id = :id")
    suspend fun cambiarIncluirEnCopia(id: String, incluir: Boolean, fecha: Long)

    /** El ISBN que escribe el usuario (null = sin ISBN). Solo toca esa columna. */
    @Query("UPDATE libros SET isbn = :isbn, fechaModificacion = :fecha WHERE id = :id")
    suspend fun cambiarIsbn(id: String, isbn: String?, fecha: Long)

    /** Sentido de lectura de un cómic («ltr» o «rtl»). */
    @Query("UPDATE libros SET sentidoLectura = :sentido, fechaModificacion = :fecha WHERE id = :id")
    suspend fun cambiarSentido(id: String, sentido: String, fecha: Long)

    /** Tamaño de letra, tipografía, márgenes e interlineado de un libro. */
    @Query("UPDATE libros SET ajustesTexto = :ajustes WHERE id = :id")
    suspend fun guardarAjustesTexto(id: String, ajustes: String)

    /** Pone (o quita, con null) el marcapáginas de un libro. */
    @Query("UPDATE libros SET marcapaginas = :marcapaginas, fechaModificacion = :fecha WHERE id = :id")
    suspend fun guardarMarcapaginas(id: String, marcapaginas: String?, fecha: Long)

    /** Al abrir un libro: se apunta la fecha y, si estaba pendiente, pasa a «leyendo». */
    @Query(
        """UPDATE libros SET fechaUltimaLectura = :fecha,
           estado = CASE WHEN estado = 'PENDIENTE' THEN 'LEYENDO' ELSE estado END
           WHERE id = :id"""
    )
    suspend fun marcarAbierto(id: String, fecha: Long)
}
