package org.libertalia.reader.copias

import org.json.JSONArray
import org.json.JSONObject
import org.libertalia.reader.datos.ColorSubrayado
import org.libertalia.reader.datos.DiaLectura
import org.libertalia.reader.datos.EstadoLectura
import org.libertalia.reader.datos.Estanteria
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.datos.TipoLibro
import org.libertalia.reader.datos.TipoTarjeta

/**
 * El formato de las copias .libertalia (un ZIP):
 *
 * ```
 * manifiesto.json                      versión, fecha, lista de libros, estanterías y días leídos
 * libros/<titulo-corto-id>/libro.epub  el archivo original (solo si se incluyen los archivos)
 * libros/<titulo-corto-id>/portada.jpg
 * libros/<titulo-corto-id>/datos.json  todos los datos del libro y todas sus tarjetas
 * legible/<Título>.md                  las tarjetas en Markdown, para leerlas fuera de la app
 * ```
 *
 * Aquí está cómo se pasa cada cosa a JSON y de vuelta. Si algún día cambia el formato,
 * se sube [VERSION] y al importar se tienen en cuenta las copias antiguas.
 */
object FormatoCopia {

    const val VERSION = 1
    const val EXTENSION = "libertalia"
    const val MANIFIESTO = "manifiesto.json"
    const val CARPETA_LIBROS = "libros/"
    const val CARPETA_LEGIBLE = "legible/"
    const val DATOS = "datos.json"
    const val PORTADA = "portada.jpg"

    /** Nombre del archivo del libro dentro de su carpeta: «libro.epub», «libro.cbz»... */
    fun nombreArchivo(formato: Formato) = "libro." + formato.name.lowercase()

    /** Carpeta de un libro dentro de la copia: título corto (sin tildes ni signos) e identificador. */
    fun carpetaDe(libro: Libro): String {
        val corto = java.text.Normalizer.normalize(libro.titulo, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trimEnd('-')
            .ifEmpty { "libro" }
        return "$CARPETA_LIBROS$corto-${libro.id.take(8)}/"
    }

    // ---- Libro ----

    fun libroAJson(libro: Libro): JSONObject = JSONObject()
        .put("id", libro.id)
        .put("tipo", libro.tipo.name)
        .put("formato", libro.formato.name)
        .put("huella", libro.huella)
        .put("titulo", libro.titulo)
        .put("autor", libro.autor)
        .putOpt("tomo", libro.tomo)
        .put("tituloOriginal", libro.tituloOriginal)
        .put("autorOriginal", libro.autorOriginal)
        .putOpt("nombreArchivo", libro.nombreArchivo)
        .put("tamanoBytes", libro.tamanoBytes)
        .put("tienePortada", libro.tienePortada)
        .put("estado", libro.estado.name)
        .putOpt("posicion", libro.posicion)
        .put("progreso", libro.progreso)
        .putOpt("capituloActual", libro.capituloActual)
        .put("incluirArchivoEnCopia", libro.incluirArchivoEnCopia)
        .putOpt("sentidoLectura", libro.sentidoLectura)
        .putOpt("idioma", libro.idioma)
        .put("fechaAnadido", libro.fechaAnadido)
        .putOpt("fechaUltimaLectura", libro.fechaUltimaLectura)
        .put("fechaModificacion", libro.fechaModificacion)
        .putOpt("fechaTerminado", libro.fechaTerminado)
        .putOpt("ajustesTexto", libro.ajustesTexto)
        .putOpt("marcapaginas", libro.marcapaginas)
        .putOpt("paginaLector", libro.paginaLector)
        .putOpt("paginasLector", libro.paginasLector)

    /**
     * El libro tal como viene en una copia. [uriArchivo] es dónde ha quedado su archivo en
     * este móvil (null si la copia no lo traía: el libro queda «sin archivo»).
     */
    fun libroDeJson(json: JSONObject, uriArchivo: String?): Libro = Libro(
        id = json.getString("id"),
        tipo = TipoLibro.valueOf(json.getString("tipo")),
        formato = Formato.valueOf(json.getString("formato")),
        huella = json.getString("huella"),
        titulo = json.getString("titulo"),
        autor = json.optString("autor"),
        tomo = json.optIntONull("tomo"),
        tituloOriginal = json.optString("tituloOriginal", json.getString("titulo")),
        autorOriginal = json.optString("autorOriginal"),
        uriArchivo = uriArchivo,
        nombreArchivo = json.optStringONull("nombreArchivo"),
        tamanoBytes = json.optLong("tamanoBytes"),
        tienePortada = json.optBoolean("tienePortada"),
        estado = EstadoLectura.valueOf(json.optString("estado", EstadoLectura.PENDIENTE.name)),
        posicion = json.optStringONull("posicion"),
        progreso = json.optDouble("progreso", 0.0),
        capituloActual = json.optStringONull("capituloActual"),
        incluirArchivoEnCopia = json.optBoolean("incluirArchivoEnCopia", true),
        sentidoLectura = json.optStringONull("sentidoLectura"),
        idioma = json.optStringONull("idioma"),
        fechaAnadido = json.optLong("fechaAnadido"),
        fechaUltimaLectura = json.optLongONull("fechaUltimaLectura"),
        fechaModificacion = json.optLong("fechaModificacion"),
        fechaTerminado = json.optLongONull("fechaTerminado"),
        ajustesTexto = json.optStringONull("ajustesTexto"),
        marcapaginas = json.optStringONull("marcapaginas"),
        paginaLector = json.optIntONull("paginaLector"),
        paginasLector = json.optIntONull("paginasLector"),
    )

    // ---- Tarjeta ----

    fun tarjetaAJson(t: Tarjeta): JSONObject = JSONObject()
        .put("id", t.id)
        .put("tipo", t.tipo.name)
        .putOpt("color", t.color?.name)
        .put("texto", t.texto)
        .putOpt("contexto", t.contexto)
        .putOpt("descripcion", t.descripcion)
        .putOpt("nota", t.nota)
        .putOpt("etiquetas", t.etiquetas)
        .putOpt("capitulo", t.capitulo)
        .putOpt("pagina", t.pagina)
        .put("progreso", t.progreso)
        .put("posicion", t.posicion)
        .put("fechaCreacion", t.fechaCreacion)
        .put("fechaModificacion", t.fechaModificacion)
        .put("eliminada", t.eliminada)

    fun tarjetaDeJson(json: JSONObject, idLibro: String): Tarjeta = Tarjeta(
        id = json.getString("id"),
        idLibro = idLibro,
        tipo = TipoTarjeta.valueOf(json.getString("tipo")),
        color = json.optStringONull("color")?.let { runCatching { ColorSubrayado.valueOf(it) }.getOrNull() },
        texto = json.optString("texto"),
        contexto = json.optStringONull("contexto"),
        descripcion = json.optStringONull("descripcion"),
        nota = json.optStringONull("nota"),
        etiquetas = json.optStringONull("etiquetas"),
        capitulo = json.optStringONull("capitulo"),
        pagina = json.optIntONull("pagina"),
        progreso = json.optDouble("progreso", 0.0),
        posicion = json.getString("posicion"),
        fechaCreacion = json.optLong("fechaCreacion"),
        fechaModificacion = json.optLong("fechaModificacion"),
        eliminada = json.optBoolean("eliminada"),
    )

    // ---- Estanterías y días leídos ----

    fun estanteriaAJson(e: Estanteria): JSONObject = JSONObject()
        .put("id", e.id)
        .put("nombre", e.nombre)
        .put("orden", e.orden)
        .put("fechaModificacion", e.fechaModificacion)

    fun estanteriaDeJson(json: JSONObject) = Estanteria(
        id = json.getString("id"),
        nombre = json.getString("nombre"),
        orden = json.optInt("orden"),
        fechaModificacion = json.optLong("fechaModificacion"),
    )

    fun diaAJson(d: DiaLectura): JSONObject = JSONObject().put("fecha", d.fecha).put("segundos", d.segundos)
        .put("segundosLibros", d.segundosLibros).put("segundosComics", d.segundosComics)
        .put("palabras", d.palabras).put("segundosPalabras", d.segundosPalabras)

    fun diaDeJson(json: JSONObject) = DiaLectura(
        fecha = json.getString("fecha"),
        segundos = json.optLong("segundos"),
        segundosLibros = json.optLong("segundosLibros"),
        segundosComics = json.optLong("segundosComics"),
        palabras = json.optLong("palabras"),
        segundosPalabras = json.optLong("segundosPalabras"),
    )

    // ---- Ayudas ----

    fun <T> lista(array: JSONArray?, convertir: (JSONObject) -> T): List<T> =
        if (array == null) emptyList() else (0 until array.length()).map { convertir(array.getJSONObject(it)) }

    private fun JSONObject.optStringONull(clave: String): String? =
        if (isNull(clave)) null else optString(clave)

    private fun JSONObject.optIntONull(clave: String): Int? =
        if (isNull(clave)) null else optInt(clave)

    private fun JSONObject.optLongONull(clave: String): Long? =
        if (isNull(clave)) null else optLong(clave)
}
