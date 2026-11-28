package org.libertalia.reader.datos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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
    // (Los subrayados unidos cuentan como una sola tarjeta: solo las principales.)
    @Query("SELECT idLibro, COUNT(*) AS numero FROM tarjetas WHERE tipo = 'SUBRAYADO' AND eliminada = 0 AND idGrupo IS NULL GROUP BY idLibro")
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

    // ---- Subrayados unidos en una tarjeta («Unir con otro subrayado») ----

    /** Todos los subrayados de la tarjeta a la que pertenece [idCabeza] (ella incluida). */
    @Query("SELECT * FROM tarjetas WHERE (id = :idCabeza OR idGrupo = :idCabeza) AND eliminada = 0 ORDER BY progreso, fechaCreacion")
    suspend fun deGrupo(idCabeza: String): List<Tarjeta>

    /** Los subrayados de la tarjeta de [id] (sea la principal o una de las unidas). */
    suspend fun grupoDe(id: String): List<Tarjeta> {
        val tarjeta = buscarPorId(id) ?: return emptyList()
        return deGrupo(tarjeta.idGrupo ?: tarjeta.id)
    }

    /**
     * Une las tarjetas de [a] y [b] en una sola. La principal es el subrayado que va antes en
     * el libro (así «Ir al libro» lleva al primero); las notas se juntan y las etiquetas se suman.
     */
    @Transaction
    suspend fun unir(a: String, b: String, fecha: Long) {
        val todos = (grupoDe(a) + grupoDe(b)).distinctBy { it.id }.sortedWith(compareBy({ it.progreso }, { it.fechaCreacion }))
        if (todos.size < 2) return
        reagrupar(todos, fecha)
    }

    /** Saca [id] de su tarjeta común: vuelve a ser una tarjeta propia. */
    @Transaction
    suspend fun separar(id: String, fecha: Long) {
        val grupo = grupoDe(id)
        val tarjeta = grupo.firstOrNull { it.id == id } ?: return
        if (grupo.size < 2) return
        val resto = grupo.filter { it.id != id }
        // Si era la principal, la nota y las etiquetas siguen con el resto de la tarjeta.
        if (tarjeta.idGrupo == null) {
            reagrupar(resto.mapIndexed { i, t -> if (i == 0) t.copy(nota = tarjeta.nota, etiquetas = tarjeta.etiquetas) else t }, fecha)
            actualizar(tarjeta.copy(idGrupo = null, fechaModificacion = fecha))
        } else {
            actualizar(tarjeta.copy(idGrupo = null, nota = null, etiquetas = null, fechaModificacion = fecha))
            reagrupar(resto, fecha)
        }
    }

    /** Quita un subrayado del libro; si era la principal de una tarjeta unida, pasa a serlo el siguiente. */
    @Transaction
    suspend fun eliminarFragmento(id: String, fecha: Long) {
        val grupo = grupoDe(id)
        val tarjeta = grupo.firstOrNull { it.id == id } ?: return
        eliminar(id, fecha)
        val resto = grupo.filter { it.id != id }
        if (resto.isNotEmpty() && tarjeta.idGrupo == null) {
            reagrupar(resto.mapIndexed { i, t -> if (i == 0) t.copy(nota = tarjeta.nota, etiquetas = tarjeta.etiquetas) else t }, fecha)
        }
    }

    /** Borra una tarjeta entera (con todos sus subrayados unidos). */
    @Transaction
    suspend fun eliminarGrupo(id: String, fecha: Long) {
        grupoDe(id).ifEmpty { listOfNotNull(buscarPorId(id)) }.forEach { eliminar(it.id, fecha) }
    }

    /** El color de una tarjeta se aplica a todos sus subrayados. */
    @Query("UPDATE tarjetas SET color = :color, fechaModificacion = :fecha WHERE id = :idCabeza OR idGrupo = :idCabeza")
    suspend fun cambiarColorGrupo(idCabeza: String, color: ColorSubrayado, fecha: Long)

    /** Deja [grupo] (ordenado) como una tarjeta: el primero es la principal, con la nota y las etiquetas de todos. */
    private suspend fun reagrupar(grupo: List<Tarjeta>, fecha: Long) {
        val ordenado = grupo.sortedWith(compareBy({ it.progreso }, { it.fechaCreacion }))
        val cabeza = ordenado.firstOrNull() ?: return
        val nota = ordenado.mapNotNull { it.nota?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString("\n\n").ifEmpty { null }
        val etiquetas = ordenado.flatMap { org.libertalia.reader.tarjetas.Etiquetas.de(it) }.distinct()
            .joinToString(", ").ifEmpty { null }
        ordenado.forEach { t ->
            val principal = t.id == cabeza.id
            actualizar(
                t.copy(
                    idGrupo = if (principal) null else cabeza.id,
                    nota = if (principal) nota else null,
                    etiquetas = if (principal) etiquetas else null,
                    // Todos los subrayados de una tarjeta llevan su color (el de la principal).
                    color = cabeza.color,
                    fechaModificacion = fecha,
                )
            )
        }
    }
}

/** Número de tarjetas de un libro. */
data class ConteoTarjetas(val idLibro: String, val numero: Int)
