package org.libertalia.reader.datos

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Estantería o colección creada por el usuario. */
@Entity(tableName = "estanterias")
data class Estanteria(
    @PrimaryKey val id: String,
    val nombre: String,
    val orden: Int,
    val fechaModificacion: Long,
)

/** Relación "este libro está en esta estantería". Un libro puede estar en varias. */
@Entity(
    tableName = "libros_estanterias",
    primaryKeys = ["libroId", "estanteriaId"],
    foreignKeys = [
        ForeignKey(
            entity = Libro::class, parentColumns = ["id"], childColumns = ["libroId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Estanteria::class, parentColumns = ["id"], childColumns = ["estanteriaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("estanteriaId")],
)
data class LibroEnEstanteria(
    val libroId: String,
    val estanteriaId: String,
)
