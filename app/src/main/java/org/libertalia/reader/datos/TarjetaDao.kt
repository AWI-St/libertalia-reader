package org.libertalia.reader.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Las consultas a la base de datos sobre tarjetas (subrayados y páginas destacadas). */
@Dao
interface TarjetaDao {

    /** Las tarjetas de un libro, en el orden en que aparecen en él; la lista se actualiza sola. */
    @Query("SELECT * FROM tarjetas WHERE idLibro = :idLibro AND eliminada = 0 ORDER BY progreso, fechaCreacion")
    fun observarDeLibro(idLibro: String): Flow<List<Tarjeta>>

    /** Solo los subrayados de un libro («Tarjetas»; las páginas destacadas van aparte). */
    @Query("SELECT * FROM tarjetas WHERE idLibro = :idLibro AND tipo = 'SUBRAYADO' AND eliminada = 0 ORDER BY progreso, fechaCreacion")
    fun observarSubrayados(idLibro: String): Flow<List<Tarjeta>>

    /** Todos los subrayados de todos los libros (para buscar en las tarjetas desde la biblioteca). */
    @Query("SELECT * FROM tarjetas WHERE tipo = 'SUBRAYADO' AND eliminada = 0 ORDER BY idLibro, progreso, fechaCreacion")
    fun observarTodosLosSubrayados(): Flow<List<Tarjeta>>

    /** Cuántas tarjetas (subrayados) tiene cada libro, para el modo Tarjetas de la biblioteca. */
    @Query("SELECT idLibro, COUNT(*) AS numero FROM tarjetas WHERE tipo = 'SUBRAYADO' AND eliminada = 0 GROUP BY idLibro")
    fun observarConteo(): Flow<List<ConteoTarjetas>>

    /**
     * Todas las tarjetas de un libro, también las borradas (van a las copias para que
     * una copia antigua no las resucite al importarla).
     */
    @Query("SELECT * FROM tarjetas WHERE idLibro = :idLibro ORDER BY progreso, fechaCreacion")
    suspend fun todasDeLibro(idLibro: String): List<Tarjeta>

    @Query("SELECT * FROM tarjetas WHERE id = :id")
    suspend fun buscarPorId(id: String): Tarjeta?

    @Insert
    suspend fun insertar(tarjeta: Tarjeta)

    /** Sustituye una tarjeta entera (al importar una copia con una versión más reciente). */
    @androidx.room.Update
    suspend fun actualizar(tarjeta: Tarjeta)

    /** Corregir el texto subrayado y la frase completa (por ejemplo, erratas del PDF). */
    @Query("UPDATE tarjetas SET texto = :texto, contexto = :contexto, fechaModificacion = :fecha WHERE id = :id")
    suspend fun cambiarTexto(id: String, texto: String, contexto: String?, fecha: Long)

    /** Etiquetas libres, separadas por comas (null si no tiene). */
    @Query("UPDATE tarjetas SET etiquetas = :etiquetas, fechaModificacion = :fecha WHERE id = :id")
    suspend fun guardarEtiquetas(id: String, etiquetas: String?, fecha: Long)

    @Query("UPDATE tarjetas SET color = :color, fechaModificacion = :fecha WHERE id = :id")
    suspend fun cambiarColor(id: String, color: ColorSubrayado, fecha: Long)

    /** Pone, cambia o borra (con null) la nota de un subrayado. */
    @Query("UPDATE tarjetas SET nota = :nota, fechaModificacion = :fecha WHERE id = :id")
    suspend fun guardarNota(id: String, nota: String?, fecha: Long)

    /** Descripción breve de una página destacada (null para quitarla). */
    @Query("UPDATE tarjetas SET descripcion = :descripcion, fechaModificacion = :fecha WHERE id = :id")
    suspend fun guardarDescripcion(id: String, descripcion: String?, fecha: Long)

    /** Quita la tarjeta (se guarda oculta, ver [Tarjeta.eliminada]). */
    @Query("UPDATE tarjetas SET eliminada = 1, fechaModificacion = :fecha WHERE id = :id")
    suspend fun eliminar(id: String, fecha: Long)
}

/** Número de tarjetas de un libro. */
data class ConteoTarjetas(val idLibro: String, val numero: Int)
