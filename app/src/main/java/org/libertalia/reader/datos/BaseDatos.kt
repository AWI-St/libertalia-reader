package org.libertalia.reader.datos

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * La base de datos de la app (un archivo SQLite dentro del móvil).
 * Cada vez que cambie su estructura se sube "version" y se añade una migración,
 * para que los datos del usuario se conserven al actualizar la app.
 */
@Database(
    entities = [Libro::class, Estanteria::class, LibroEnEstanteria::class, Tarjeta::class, DiaLectura::class],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2: se añade el capítulo actual de cada libro.
        AutoMigration(from = 1, to = 2),
        // 2 → 3: los libros eliminados se ocultan en lugar de borrarse (conservan sus datos).
        AutoMigration(from = 2, to = 3),
        // 3 → 4: ajustes de texto y marcapáginas de cada libro.
        AutoMigration(from = 3, to = 4),
        // 4 → 5: página del lector y total, para «Seguir leyendo» sin porcentaje.
        AutoMigration(from = 4, to = 5),
        // 5 → 6: tarjetas (subrayados y páginas destacadas).
        AutoMigration(from = 5, to = 6),
        // 6 → 7: tiempo leído cada día (para la racha de lectura).
        AutoMigration(from = 6, to = 7),
        // 7 → 8: estadísticas (fecha de terminado; tiempo de libros y de cómics y palabras por día).
        AutoMigration(from = 7, to = 8, spec = BaseDatos.Migracion7a8::class),
    ],
)
abstract class BaseDatos : RoomDatabase() {

    /**
     * Los libros que ya estaban terminados no sabían cuándo: se toma la última vez que se
     * leyeron (o, si no, la última vez que se cambiaron), que es lo más parecido.
     */
    class Migracion7a8 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE libros SET fechaTerminado = COALESCE(fechaUltimaLectura, fechaModificacion) WHERE estado = 'TERMINADO'"
            )
        }
    }

    abstract fun libros(): LibroDao

    abstract fun estanterias(): EstanteriaDao

    abstract fun tarjetas(): TarjetaDao

    abstract fun diasLectura(): DiaLecturaDao

    companion object {
        fun crear(contexto: Context): BaseDatos =
            Room.databaseBuilder(contexto, BaseDatos::class.java, "libertalia.db").build()
    }
}
