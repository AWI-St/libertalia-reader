package org.libertalia.reader.datos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Las consultas sobre estanterías creadas por el usuario. */
@Dao
interface EstanteriaDao {

    @Query("SELECT * FROM estanterias ORDER BY orden, nombre COLLATE NOCASE")
    fun observarTodas(): Flow<List<Estanteria>>

    /** Qué libro está en qué estantería. */
    @Query("SELECT * FROM libros_estanterias")
    fun observarRelaciones(): Flow<List<LibroEnEstanteria>>

    /** Las estanterías de una vez (para las copias). */
    @Query("SELECT * FROM estanterias ORDER BY orden, nombre COLLATE NOCASE")
    suspend fun todas(): List<Estanteria>

    /** Las relaciones libro–estantería de una vez (para las copias). */
    @Query("SELECT * FROM libros_estanterias")
    suspend fun relaciones(): List<LibroEnEstanteria>

    @Query("SELECT COALESCE(MAX(orden), 0) FROM estanterias")
    suspend fun ordenMaximo(): Int

    @Insert
    suspend fun insertar(estanteria: Estanteria)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun anadirLibro(relacion: LibroEnEstanteria)

    @Delete
    suspend fun quitarLibro(relacion: LibroEnEstanteria)

    @Query("UPDATE estanterias SET nombre = :nombre, fechaModificacion = :fecha WHERE id = :id")
    suspend fun renombrar(id: String, nombre: String, fecha: Long)

    @Query("UPDATE estanterias SET orden = :orden WHERE id = :id")
    suspend fun cambiarOrden(id: String, orden: Int)

    /** Borra la estantería; los libros siguen en la biblioteca. */
    @Delete
    suspend fun eliminar(estanteria: Estanteria)
}
