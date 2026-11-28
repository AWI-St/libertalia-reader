package org.libertalia.reader.tarjetas

import org.libertalia.reader.datos.Tarjeta

/**
 * Subrayados unidos en una sola tarjeta («Unir con otro subrayado»). En la base de datos
 * cada subrayado es una fila (para pintarlo en su sitio del libro); en las pantallas de
 * tarjetas se ven juntos, por orden del texto: «fragmento 1 […] fragmento 2 […] fragmento 3».
 */
object Fragmentos {

    /** Lo que separa los fragmentos en el texto de una tarjeta unida. */
    const val SEPARADOR = " […] "

    /**
     * Una tarjeta por grupo: la principal, con el texto de todos sus fragmentos en orden.
     * Las tarjetas sueltas quedan igual. Se conserva el orden de [lista].
     */
    fun agrupar(lista: List<Tarjeta>): List<Tarjeta> {
        val porGrupo = lista.groupBy { it.idGrupo ?: it.id }
        return lista.filter { it.idGrupo == null }.map { cabeza ->
            val grupo = porGrupo[cabeza.id].orEmpty()
            if (grupo.size < 2) cabeza
            else cabeza.copy(
                texto = grupo.sortedWith(compareBy({ it.progreso }, { it.fechaCreacion })).joinToString(SEPARADOR) { it.texto.trim() },
                // La frase de alrededor ya no vale para varios fragmentos.
                contexto = null,
            )
        }
    }

    /** Si la tarjeta junta varios subrayados. */
    fun esUnida(tarjeta: Tarjeta): Boolean = tarjeta.contexto == null && SEPARADOR in tarjeta.texto
}
