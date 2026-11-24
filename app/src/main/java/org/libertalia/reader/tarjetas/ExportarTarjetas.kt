package org.libertalia.reader.tarjetas

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.libertalia.reader.R
import org.libertalia.reader.copias.MarkdownTarjetas
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.datos.TipoTarjeta
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Los formatos en que se pueden sacar las tarjetas de un libro. */
enum class FormatoTarjetas(val extension: String, val tipo: String) {
    MARKDOWN("md", "text/markdown"),
    CSV("csv", "text/csv"),
    ANKI("txt", "text/plain"),
}

/**
 * Exportar las tarjetas de un libro (con el título editado del libro) a Markdown, CSV o un
 * archivo de texto que Anki sabe importar. Los textos fijos salen de strings.xml.
 */
object ExportarTarjetas {

    /** Escribe el archivo en la caché (carpeta «compartir») y abre el menú de compartir de Android. */
    fun compartir(contexto: Context, libro: Libro, tarjetas: List<Tarjeta>, formato: FormatoTarjetas) {
        val texto = when (formato) {
            FormatoTarjetas.MARKDOWN -> MarkdownTarjetas.de(contexto, libro, tarjetas) ?: return
            FormatoTarjetas.CSV -> csv(contexto, libro, tarjetas)
            FormatoTarjetas.ANKI -> anki(contexto, libro, tarjetas)
        }
        val nombre = nombreArchivo(contexto, libro, formato)
        val carpeta = File(contexto.cacheDir, "compartir").apply { mkdirs() }
        val archivo = File(carpeta, nombre)
        // El CSV lleva marca de UTF-8 delante para que las hojas de cálculo lean bien las tildes.
        archivo.writeText((if (formato == FormatoTarjetas.CSV) "﻿" else "") + texto)
        val uri = FileProvider.getUriForFile(contexto, contexto.packageName + ".archivos", archivo)
        val enviar = Intent(Intent.ACTION_SEND)
            .setType(formato.tipo)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, nombre)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        enviar.clipData = ClipData.newRawUri(nombre, uri)
        contexto.startActivity(Intent.createChooser(enviar, contexto.getString(R.string.exportar_tarjetas_titulo)))
    }

    /** «Don Quijote de la Mancha - tarjetas.csv» (sin signos que no admiten los sistemas de archivos). */
    private fun nombreArchivo(contexto: Context, libro: Libro, formato: FormatoTarjetas): String {
        val titulo = libro.titulo.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "-").trim().take(80).ifEmpty { "libro" }
        val sufijo = contexto.getString(if (formato == FormatoTarjetas.ANKI) R.string.exportar_tarjetas_sufijo_anki else R.string.exportar_tarjetas_sufijo)
        return "$titulo - $sufijo.${formato.extension}"
    }

    private fun subrayados(tarjetas: List<Tarjeta>) =
        tarjetas.filter { it.tipo == TipoTarjeta.SUBRAYADO && !it.eliminada }.sortedWith(compareBy({ it.progreso }, { it.fechaCreacion }))

    /** Una fila por tarjeta: texto, frase, nota, etiquetas, color, capítulo, página (PDF) y fecha. */
    fun csv(contexto: Context, libro: Libro, tarjetas: List<Tarjeta>): String {
        val cabecera = listOf(
            R.string.csv_texto, R.string.csv_frase, R.string.csv_nota, R.string.csv_etiquetas,
            R.string.csv_color, R.string.csv_capitulo, R.string.csv_pagina, R.string.csv_creada,
        ).map { contexto.getString(it) }
        val fecha = DateFormat.getDateInstance(DateFormat.MEDIUM, org.libertalia.reader.ui.ajustes.AjustesApp.localeApp(contexto))
        val filas = subrayados(tarjetas).map { t ->
            listOf(
                t.texto,
                t.contexto.orEmpty(),
                t.nota.orEmpty(),
                Etiquetas.de(t).joinToString(" ") { "#$it" },
                t.color?.let { contexto.getString(it.nombre) }.orEmpty(),
                t.capitulo.orEmpty(),
                t.pagina?.takeIf { libro.formato == Formato.PDF }?.toString().orEmpty(),
                fecha.format(Date(t.fechaCreacion)),
            )
        }
        return (listOf(cabecera) + filas).joinToString("\r\n") { fila -> fila.joinToString(",") { celdaCsv(it) } } + "\r\n"
    }

    /** Entre comillas si hace falta (comas, comillas o saltos de línea), con las comillas dobladas. */
    private fun celdaCsv(texto: String): String =
        if (texto.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + texto.replace("\"", "\"\"") + "\"" else texto

    /**
     * Texto que Anki importa (Archivo → Importar): una nota por tarjeta, separada por
     * tabuladores. Anverso: la frase con el subrayado en negrita. Reverso: libro, autor,
     * capítulo y nota. Las etiquetas pasan a ser etiquetas de Anki.
     */
    fun anki(contexto: Context, libro: Libro, tarjetas: List<Tarjeta>): String {
        val cabecera = "#separator:tab\n#html:true\n#tags column:3\n"
        val cuerpo = subrayados(tarjetas).joinToString("\n") { t ->
            // Se comparan sin saltos de línea ni espacios dobles: la frase y el subrayado pueden diferir en eso.
            val frase = unaLinea(t.contexto ?: t.texto)
            val subrayado = unaLinea(t.texto)
            val inicio = frase.indexOf(subrayado)
            val anverso = if (inicio >= 0 && subrayado.isNotEmpty()) {
                html(frase.substring(0, inicio)) + "<b>" + html(subrayado) + "</b>" + html(frase.substring(inicio + subrayado.length))
            } else {
                "<b>" + html(t.texto) + "</b>"
            }
            val reverso = buildString {
                append("<i>").append(html(libro.titulo)).append("</i>")
                if (libro.autor.isNotBlank()) append(" — ").append(html(libro.autor))
                t.capitulo?.takeIf { it.isNotBlank() }?.let { append("<br>").append(html(it)) }
                t.pagina?.takeIf { libro.formato == Formato.PDF }?.let { append(" · ").append(html(contexto.getString(R.string.tarjetas_pagina, it))) }
                t.nota?.takeIf { it.isNotBlank() }?.let { append("<br><br>").append(html(it)) }
            }
            val etiquetas = Etiquetas.de(t).joinToString(" ") { it.replace(" ", "_") }
            listOf(anverso, reverso, etiquetas).joinToString("\t")
        }
        return cabecera + cuerpo + "\n"
    }

    private fun unaLinea(texto: String) = texto.replace(Regex("\\s+"), " ").trim()

    /** Texto seguro dentro de HTML, en una sola línea (sin tabuladores, que separan los campos). */
    private fun html(texto: String): String = texto
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\t", " ").replace("\r\n", "<br>").replace("\n", "<br>")
}
