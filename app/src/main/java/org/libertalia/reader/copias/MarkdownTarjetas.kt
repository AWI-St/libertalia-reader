package org.libertalia.reader.copias

import android.content.Context
import org.libertalia.reader.R
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.datos.TipoTarjeta
import org.libertalia.reader.tarjetas.Etiquetas
import java.text.DateFormat
import java.util.Date

/**
 * Las tarjetas de un libro en Markdown, para leerlas fuera de la app (en las copias, en
 * «legible/», y más adelante al exportar las tarjetas de un libro).
 *
 * «Tarjetas» (subrayados) y «Destacadas» (páginas) van en secciones separadas, como en la app.
 * Los textos fijos salen de strings.xml (se traducen); los del usuario no.
 */
object MarkdownTarjetas {

    /** null si el libro no tiene tarjetas ni destacadas. */
    fun de(contexto: Context, libro: Libro, tarjetas: List<Tarjeta>, ahora: Long = System.currentTimeMillis()): String? {
        val vivas = tarjetas.filter { !it.eliminada }.sortedWith(compareBy({ it.progreso }, { it.fechaCreacion }))
        val subrayados = vivas.filter { it.tipo == TipoTarjeta.SUBRAYADO }
        val destacadas = vivas.filter { it.tipo == TipoTarjeta.PAGINA }
        if (subrayados.isEmpty() && destacadas.isEmpty()) return null

        val md = StringBuilder()
        md.append("# ").append(unaLinea(libro.titulo)).append("\n\n")
        if (libro.autor.isNotBlank()) md.append("*").append(unaLinea(libro.autor)).append("*\n\n")

        if (subrayados.isNotEmpty()) {
            md.append("## ").append(contexto.getString(R.string.md_tarjetas)).append("\n\n")
            subrayados.forEach { t ->
                md.append(cita(fraseConSubrayado(t))).append("\n\n")
                lugar(contexto, libro, t)?.let { md.append(it).append("\n\n") }
                t.nota?.takeIf { it.isNotBlank() }?.let {
                    md.append("**").append(contexto.getString(R.string.md_nota)).append("** ")
                        .append(it.trim().replace("\n", "  \n")).append("\n\n")
                }
                val etiquetas = Etiquetas.de(t)
                if (etiquetas.isNotEmpty()) {
                    md.append(contexto.getString(R.string.md_etiquetas)).append(" ")
                        .append(etiquetas.joinToString(" ") { "#" + it.replace(" ", "_") }).append("\n\n")
                }
                md.append("---\n\n")
            }
        }

        if (destacadas.isNotEmpty()) {
            md.append("## ").append(contexto.getString(R.string.md_destacadas)).append("\n\n")
            destacadas.forEach { t ->
                val titulo = t.descripcion?.takeIf { it.isNotBlank() } ?: contexto.getString(R.string.md_destacada_sin_descripcion)
                md.append("### ").append(unaLinea(titulo)).append("\n\n")
                lugar(contexto, libro, t)?.let { md.append(it).append("\n\n") }
                if (t.texto.isNotBlank()) md.append(cita(t.texto)).append("\n\n")
            }
        }

        val fecha = org.libertalia.reader.ui.ajustes.AjustesApp.fecha(contexto, ahora)
        md.append("*").append(contexto.getString(R.string.md_exportado, fecha)).append("*\n")
        return md.toString()
    }

    /** La frase completa con la parte subrayada en negrita. */
    internal fun fraseConSubrayado(t: Tarjeta): String {
        // Se comparan sin saltos de línea ni espacios dobles: la frase y el subrayado pueden diferir en eso.
        val subrayado = unaLinea(t.texto)
        val frase = t.contexto?.let { unaLinea(it) } ?: return "**$subrayado**"
        val inicio = frase.indexOf(subrayado)
        if (inicio < 0 || subrayado.isEmpty()) return "**$subrayado** — $frase"
        val fin = inicio + subrayado.length
        return frase.substring(0, inicio) + "**" + subrayado + "**" + frase.substring(fin)
    }

    /** «Capítulo · Pág. 12» (la página solo en los PDF, como en la app). */
    internal fun lugar(contexto: Context, libro: Libro, t: Tarjeta): String? {
        val pagina = t.pagina?.takeIf { libro.formato == Formato.PDF }?.let { contexto.getString(R.string.tarjetas_pagina, it) }
        val partes = listOfNotNull(t.capitulo?.takeIf { it.isNotBlank() }?.let(::unaLinea), pagina)
        return partes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun cita(texto: String) = texto.trim().lines().joinToString("\n") { "> $it" }

    internal fun unaLinea(texto: String) = texto.replace(Regex("\\s+"), " ").trim()
}
