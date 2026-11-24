package org.libertalia.reader.datos

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Cuánto se ha leído un día. Sirve para la racha de lectura y para las estadísticas.
 *
 * - [fecha]: el día, como «2026-11-23» (hora del móvil).
 * - [segundos]: tiempo activo leyendo ese día (libros y cómics juntos; también escuchando).
 * - [segundosLibros] y [segundosComics]: ese mismo tiempo, separado (desde la versión 8 de
 *   la base de datos; los días anteriores tienen 0 en los dos).
 * - [palabras] y [segundosPalabras]: palabras leídas en libros pasando página a página y el
 *   tiempo que llevaron, para la velocidad de lectura (palabras por minuto).
 */
@Entity(tableName = "dias_lectura")
data class DiaLectura(
    @PrimaryKey val fecha: String,
    val segundos: Long,
    @ColumnInfo(defaultValue = "0") val segundosLibros: Long = 0,
    @ColumnInfo(defaultValue = "0") val segundosComics: Long = 0,
    @ColumnInfo(defaultValue = "0") val palabras: Long = 0,
    @ColumnInfo(defaultValue = "0") val segundosPalabras: Long = 0,
)

@Dao
interface DiaLecturaDao {

    /** Suma lo leído a un día (lo crea si aún no existe). */
    @Query(
        """INSERT INTO dias_lectura (fecha, segundos, segundosLibros, segundosComics, palabras, segundosPalabras)
           VALUES (:fecha, :segundos, :libros, :comics, :palabras, :segundosPalabras)
           ON CONFLICT(fecha) DO UPDATE SET segundos = segundos + :segundos,
               segundosLibros = segundosLibros + :libros, segundosComics = segundosComics + :comics,
               palabras = palabras + :palabras, segundosPalabras = segundosPalabras + :segundosPalabras"""
    )
    suspend fun sumar(fecha: String, segundos: Long, libros: Long, comics: Long, palabras: Long, segundosPalabras: Long)

    /**
     * Al importar una copia: de cada día se queda lo mayor (no se suma, para que importar dos
     * veces la misma copia no duplique el tiempo leído).
     */
    @Query(
        """INSERT INTO dias_lectura (fecha, segundos, segundosLibros, segundosComics, palabras, segundosPalabras)
           VALUES (:fecha, :segundos, :libros, :comics, :palabras, :segundosPalabras)
           ON CONFLICT(fecha) DO UPDATE SET segundos = MAX(segundos, :segundos),
               segundosLibros = MAX(segundosLibros, :libros), segundosComics = MAX(segundosComics, :comics),
               palabras = MAX(palabras, :palabras), segundosPalabras = MAX(segundosPalabras, :segundosPalabras)"""
    )
    suspend fun guardarMaximo(fecha: String, segundos: Long, libros: Long, comics: Long, palabras: Long, segundosPalabras: Long)

    /** Los días leídos desde una fecha («» = todos). */
    @Query("SELECT * FROM dias_lectura WHERE fecha >= :desde ORDER BY fecha DESC")
    suspend fun desde(desde: String): List<DiaLectura>
}
