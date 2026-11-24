package org.libertalia.reader.biblioteca

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.LibroDao
import org.libertalia.reader.datos.TipoLibro
import org.libertalia.reader.comic.FichaComic
import org.libertalia.reader.comic.LectorDatosComic
import org.libertalia.reader.comic.OrigenComic
import org.libertalia.reader.comic.SentidoLectura
import org.libertalia.reader.lector.ServicioLectura
import org.readium.r2.shared.publication.services.cover
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

/** Lo que ha pasado al intentar añadir un archivo. */
enum class ResultadoImportacion {
    /** Libro nuevo añadido. */
    ANADIDO,

    /** Ya estaba en la biblioteca (misma huella), no se duplica. */
    YA_EXISTIA,

    /** Estaba "sin archivo" (vino de una copia) y ahora lo tiene. */
    COMPLETADO,

    /** Se había eliminado de la biblioteca y vuelve con todos sus datos. */
    RECUPERADO,

    /** No es EPUB, PDF, CBZ ni CBR. */
    NO_COMPATIBLE,

    /** Es un CBR en formato RAR 5, que la app no sabe abrir. */
    RAR5,

    /** Algo falló (archivo dañado, sin espacio, sin permiso en la carpeta...). */
    ERROR,

    /** Es una copia .libertalia: no se añade como libro, se ofrece importarla. */
    COPIA,
}

/** Resultado de añadir un archivo, con el libro afectado (si lo hay). */
data class Importacion(
    val resultado: ResultadoImportacion,
    val idLibro: String? = null,
    /** Cómic que la app solo puede abrir ahora: hay que pedir al usuario la carpeta donde está. */
    val faltaCarpeta: Boolean = false,
    /** Cómic que está en una nube: se recomienda guardarlo en el móvil. */
    val enLaNube: Boolean = false,
    /**
     * El archivo que se añadió (p. ej. en Descargas), si se puede ofrecer borrarlo: el libro
     * ya está guardado en la carpeta de la app y así no queda dos veces.
     */
    val originalBorrable: Uri? = null,
)

/**
 * Un archivo recibido, ya copiado a un temporal y reconocido, a la espera de decidir qué
 * hacer con él (con «Abrir con» se pregunta antes si se añade a la biblioteca o solo se lee).
 * Hay que terminar siempre con [Importador.completar], [Importador.prepararDePaso] o
 * [Importador.descartar], que borran el temporal.
 */
class ArchivoExaminado internal constructor(
    val origen: Uri,
    internal val temporal: File,
    internal val huella: String,
    internal val formato: Formato?,
    internal val nombreOriginal: String,
    /** Lo que ya se sabe sin añadir nada (copia, RAR 5, no compatible, ya existía...); null: es nuevo. */
    val final: Importacion?,
    internal val existente: Libro?,
) {
    /** El nombre del archivo, para enseñarlo al preguntar. */
    val nombre: String get() = nombreOriginal
}

/** Lo que se ha podido leer de dentro del libro o cómic. */
private class DatosLeidos(
    val tipo: TipoLibro,
    val titulo: String,
    val autor: String,
    val tomo: Int?,
    val sentido: String?,
    val idioma: String?,
    val portada: android.graphics.Bitmap?,
)

/**
 * Añade libros a la biblioteca:
 * 1. copia el archivo a un temporal calculando su huella (SHA-256),
 * 2. comprueba si ya estaba (aunque se hubiera eliminado: entonces recupera sus datos),
 * 3. lo abre con Readium para sacar título, autor, idioma y portada,
 * 4. reutiliza el archivo si ya está en la carpeta de la app o, si no, lo copia allí,
 * 5. lo apunta en la base de datos.
 */
class Importador(
    private val contexto: Context,
    private val libros: LibroDao,
    private val carpeta: CarpetaLibertalia,
    private val servicioLectura: ServicioLectura,
) {

    suspend fun importar(origen: Uri): Importacion {
        val examinado = examinar(origen)
        examinado.final?.let {
            descartar(examinado)
            return it
        }
        return completar(examinado)
    }

    /**
     * Copia el archivo a un temporal, calcula su huella y mira qué es y si ya estaba en la
     * biblioteca, sin añadir nada todavía.
     */
    suspend fun examinar(origen: Uri): ArchivoExaminado = withContext(Dispatchers.IO) {
        val nombreOriginal = nombreDe(origen)
        val temporal = File(contexto.cacheDir, "importando/${UUID.randomUUID()}")
        temporal.parentFile?.mkdirs()
        fun examinado(final: Importacion?, huella: String = "", formato: Formato? = null, existente: Libro? = null) =
            ArchivoExaminado(origen, temporal, huella, formato, nombreOriginal, final, existente)
        val huella = runCatching { copiarCalculandoHuella(origen, temporal) }
            .getOrElse { return@withContext examinado(Importacion(ResultadoImportacion.ERROR)) }
        if (esCopiaLibertalia(temporal)) return@withContext examinado(Importacion(ResultadoImportacion.COPIA))
        if (esRar5(temporal)) return@withContext examinado(Importacion(ResultadoImportacion.RAR5))
        val formato = detectarFormato(temporal)
            ?: return@withContext examinado(Importacion(ResultadoImportacion.NO_COMPATIBLE))

        // ¿Ya conocemos este libro? (también si se eliminó: conserva sus datos)
        val existente = libros.buscarPorHuella(huella)
        if (existente != null && !existente.eliminado && archivoExiste(existente.uriArchivo)) {
            // Ya lo tenemos: el que se acaba de añadir sobra (si es un libro de la carpeta).
            val sobra = origen.takeIf { existente.tipo == TipoLibro.TEXTO && carpeta.contiene(existente.uriArchivo) }
            return@withContext examinado(
                Importacion(ResultadoImportacion.YA_EXISTIA, existente.id, originalBorrable = sobra?.let(::siSePuedeBorrar)),
                huella, formato, existente,
            )
        }
        examinado(null, huella, formato, existente)
    }

    /** No se hace nada con el archivo examinado: se borra el temporal. */
    fun descartar(examinado: ArchivoExaminado) {
        examinado.temporal.delete()
    }

    /** Añade a la biblioteca un archivo ya examinado (nuevo, o que estaba eliminado o sin archivo). */
    suspend fun completar(examinado: ArchivoExaminado): Importacion = withContext(Dispatchers.IO) {
        examinado.final?.let {
            descartar(examinado)
            return@withContext it
        }
        runCatching { completarSinCapturar(examinado) }.getOrDefault(Importacion(ResultadoImportacion.ERROR))
    }

    /**
     * «Solo leer»: prepara el archivo para abrirlo sin añadirlo a la biblioteca. Devuelve un
     * libro «de paso» (no se guarda en la base de datos) cuyo archivo es una copia en la caché,
     * que se borra al cerrar el lector ([LibroDePaso.limpiar]).
     */
    suspend fun prepararDePaso(examinado: ArchivoExaminado): Libro? = withContext(Dispatchers.IO) {
        val formato = examinado.formato
        if (examinado.final != null || formato == null) {
            descartar(examinado)
            return@withContext null
        }
        val datos = runCatching { leerDatos(examinado.temporal, formato, examinado.nombreOriginal) }.getOrNull()?.first
        if (datos == null) {
            descartar(examinado)
            return@withContext null
        }
        val id = LibroDePaso.nuevoId()
        val archivo = LibroDePaso.archivo(contexto, id, formato)
        archivo.parentFile?.mkdirs()
        if (!examinado.temporal.renameTo(archivo)) {
            examinado.temporal.copyTo(archivo, overwrite = true)
            descartar(examinado)
        }
        val ahora = System.currentTimeMillis()
        Libro(
            id = id,
            tipo = datos.tipo,
            formato = formato,
            huella = examinado.huella,
            titulo = datos.titulo,
            autor = datos.autor,
            tomo = datos.tomo,
            sentidoLectura = datos.sentido,
            tituloOriginal = datos.titulo,
            autorOriginal = datos.autor,
            uriArchivo = Uri.fromFile(archivo).toString(),
            nombreArchivo = examinado.nombreOriginal,
            tamanoBytes = archivo.length(),
            tienePortada = false,
            idioma = datos.idioma,
            fechaAnadido = ahora,
            fechaModificacion = ahora,
        )
    }

    /**
     * Lee título, autor, idioma, portada... del propio libro o cómic. Devuelve los datos o,
     * si no se puede, el resultado que hay que dar (no compatible o error).
     */
    private suspend fun leerDatos(
        temporal: File,
        formato: Formato,
        nombreOriginal: String,
    ): Pair<DatosLeidos?, ResultadoImportacion?> {
        val (tituloArchivo, autorArchivo) = datosDelNombre(nombreOriginal)
        val (tituloSinTomo, tomoArchivo) = FichaComic.tomoDelNombre(tituloArchivo)
        if (formato == Formato.CBZ || formato == Formato.CBR) {
            val comic = runCatching { LectorDatosComic.leer(temporal, esRar = formato == Formato.CBR) }
                .getOrElse { return null to ResultadoImportacion.ERROR }
                ?: return null to ResultadoImportacion.NO_COMPATIBLE
            val ficha = comic.ficha
            // Con ComicInfo.xml: la serie es el título (y el número, el tomo).
            return DatosLeidos(
                tipo = TipoLibro.COMIC,
                titulo = ficha.serie ?: ficha.titulo ?: tituloSinTomo,
                autor = ficha.autor ?: autorArchivo,
                tomo = ficha.numero ?: tomoArchivo,
                sentido = if (ficha.derechaAIzquierda == true) SentidoLectura.DERECHA_A_IZQUIERDA else SentidoLectura.IZQUIERDA_A_DERECHA,
                idioma = ficha.idioma,
                portada = comic.portada,
            ) to null
        }
        val publicacion = servicioLectura.abrir(temporal).getOrElse { return null to ResultadoImportacion.ERROR }
        val tituloLibro = publicacion.metadata.title?.trim().orEmpty()
        val autorLibro = publicacion.metadata.authors.joinToString(", ") { it.name.trim() }
        val idioma = publicacion.metadata.languages.firstOrNull()
        val portada = runCatching { publicacion.cover() }.getOrNull()
        publicacion.close()
        // Un PDF cuyas páginas son solo imágenes se trata como cómic.
        val esComic = formato == Formato.PDF && LectorDatosComic.esPdfDeImagenes(contexto, temporal)
        return DatosLeidos(
            tipo = if (esComic) TipoLibro.COMIC else TipoLibro.TEXTO,
            // Si el libro no trae título, se saca del nombre del archivo (limpio).
            titulo = tituloLibro.ifEmpty { if (esComic) tituloSinTomo else tituloArchivo },
            autor = autorLibro.ifEmpty { if (tituloLibro.isEmpty()) autorArchivo else "" },
            tomo = if (esComic) tomoArchivo else null,
            sentido = if (esComic) SentidoLectura.IZQUIERDA_A_DERECHA else null,
            idioma = idioma,
            portada = portada,
        ) to null
    }

    private suspend fun completarSinCapturar(examinado: ArchivoExaminado): Importacion {
        val origen = examinado.origen
        val temporal = examinado.temporal
        val huella = examinado.huella
        val nombreOriginal = examinado.nombreOriginal
        val existente = examinado.existente
        val formato = examinado.formato ?: return Importacion(ResultadoImportacion.NO_COMPATIBLE)
        try {
            // Leer los datos del propio libro o cómic.
            val (datos, fallo) = leerDatos(temporal, formato, nombreOriginal)
            if (datos == null) return Importacion(fallo ?: ResultadoImportacion.ERROR)
            val tipo = datos.tipo
            val titulo = datos.titulo
            val autor = datos.autor
            val tomo = datos.tomo
            val sentido = datos.sentido
            val idioma = datos.idioma
            val portada = datos.portada
            val nombreArchivo = asegurarExtension(nombreOriginal, formato)

            // El archivo:
            // - Los cómics (que pueden pesar mucho) no se copian: se abren siempre desde donde
            //   están y se pide a Android permiso permanente para volver a leerlos.
            // - Los libros se guardan en la carpeta de la app: se reutiliza el que ya esté (el del
            //   libro o cualquiera con el mismo contenido) y solo si no hay ninguno se copia.
            var faltaCarpeta = false
            val (uriGuardada, nombreGuardado) = if (tipo == TipoLibro.COMIC) {
                val permanente = OrigenComic.direccionPermanente(contexto, origen, nombreOriginal, temporal.length())
                // Sin dirección permanente se apunta la de ahora (sirve mientras dure el permiso)
                // y se pide al usuario la carpeta donde está.
                faltaCarpeta = permanente == null
                (permanente ?: origen).toString() to nombreOriginal
            } else {
                val guardado = existente?.uriArchivo
                    ?.let { DocumentFile.fromSingleUri(contexto, Uri.parse(it)) }
                    ?.takeIf { runCatching { it.exists() }.getOrDefault(false) }
                    ?: carpeta.buscarIgual(temporal.length()) { calcularHuella(it) == huella }
                    ?: temporal.inputStream().use { entrada ->
                        carpeta.guardarArchivo(nombreArchivo, tipoMime(formato), entrada)
                    }
                    ?: return Importacion(ResultadoImportacion.ERROR)
                guardado.uri.toString() to guardado.name
            }

            val ahora = System.currentTimeMillis()
            val id = existente?.id ?: UUID.randomUUID().toString()
            val portadaGuardada = existente?.tienePortada == true && Portadas.archivo(contexto, id).exists()
            val tienePortada = portadaGuardada || (portada?.let { Portadas.guardar(contexto, id, it) } ?: false)

            if (existente != null) {
                // Se conservan su página, estado, estanterías, notas...: solo cambia el archivo.
                libros.actualizar(
                    existente.copy(
                        uriArchivo = uriGuardada,
                        nombreArchivo = nombreGuardado,
                        tamanoBytes = temporal.length(),
                        tienePortada = tienePortada,
                        eliminado = false,
                        fechaModificacion = ahora,
                    )
                )
                val resultado = if (existente.eliminado) ResultadoImportacion.RECUPERADO else ResultadoImportacion.COMPLETADO
                return Importacion(
                    resultado, id, faltaCarpeta,
                    enLaNube = tipo == TipoLibro.COMIC && OrigenComic.estaEnLaNube(uriGuardada),
                    originalBorrable = if (tipo == TipoLibro.TEXTO) siSePuedeBorrar(origen) else null,
                )
            }

            libros.insertar(
                Libro(
                    id = id,
                    tipo = tipo,
                    formato = formato,
                    huella = huella,
                    titulo = titulo,
                    autor = autor,
                    tomo = tomo,
                    sentidoLectura = sentido,
                    tituloOriginal = titulo,
                    autorOriginal = autor,
                    uriArchivo = uriGuardada,
                    nombreArchivo = nombreGuardado,
                    tamanoBytes = temporal.length(),
                    tienePortada = tienePortada,
                    idioma = idioma,
                    fechaAnadido = ahora,
                    fechaModificacion = ahora,
                )
            )
            return Importacion(
                ResultadoImportacion.ANADIDO, id, faltaCarpeta,
                enLaNube = tipo == TipoLibro.COMIC && OrigenComic.estaEnLaNube(uriGuardada),
                // Los libros ya están copiados en la carpeta: se puede ofrecer borrar el original.
                // (Los cómics no: se abren desde donde están.)
                originalBorrable = if (tipo == TipoLibro.TEXTO) siSePuedeBorrar(origen) else null,
            )
        } finally {
            temporal.delete()
        }
    }

    /** [origen] si se puede ofrecer borrarlo (está en el móvil, fuera de la carpeta, y Android lo deja). */
    private fun siSePuedeBorrar(origen: Uri): Uri? =
        origen.takeIf { ArchivosExternos.sePuedeBorrar(contexto, it, carpeta) }

    /** Calcula la huella (SHA-256) de un archivo de la carpeta. */
    private fun calcularHuella(archivo: DocumentFile): String? = runCatching {
        val resumen = MessageDigest.getInstance("SHA-256")
        contexto.contentResolver.openInputStream(archivo.uri)?.use { entrada ->
            val bufer = ByteArray(64 * 1024)
            while (true) {
                val leidos = entrada.read(bufer)
                if (leidos < 0) break
                resumen.update(bufer, 0, leidos)
            }
        } ?: return null
        resumen.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Copia el archivo y, de paso, calcula su SHA-256 (su "huella"). */
    private fun copiarCalculandoHuella(origen: Uri, destino: File): String {
        val resumen = MessageDigest.getInstance("SHA-256")
        val entrada = contexto.contentResolver.openInputStream(origen)
            ?: error("No se puede leer el archivo")
        DigestInputStream(entrada, resumen).use { flujo ->
            destino.outputStream().use { salida -> flujo.copyTo(salida) }
        }
        return resumen.digest().joinToString("") { "%02x".format(it) }
    }

    /** Una copia de Libertalia es un ZIP con «manifiesto.json» (se mira el contenido, no el nombre). */
    private fun esCopiaLibertalia(archivo: File): Boolean = runCatching {
        java.util.zip.ZipFile(archivo).use { it.getEntry(org.libertalia.reader.copias.FormatoCopia.MANIFIESTO) != null }
    }.getOrDefault(false)

    /** RAR 5 empieza por «Rar!» seguido de 1A 07 01 00 (RAR 4: 1A 07 00). */
    private fun esRar5(archivo: File): Boolean {
        val cabecera = ByteArray(8)
        val leidos = archivo.inputStream().use { it.read(cabecera) }
        return leidos == 8 && cabecera.contentEquals(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00))
    }

    /** Mira los primeros bytes del archivo para saber qué es, sin fiarse del nombre. */
    private fun detectarFormato(archivo: File): Formato? {
        val cabecera = ByteArray(64)
        val leidos = archivo.inputStream().use { it.read(cabecera) }
        if (leidos < 4) return null
        val texto = String(cabecera, 0, leidos, Charsets.ISO_8859_1)
        return when {
            texto.startsWith("%PDF") -> Formato.PDF
            // Un EPUB es un ZIP cuyo primer archivo se llama "mimetype".
            texto.startsWith("PK") && texto.contains("application/epub+zip") -> Formato.EPUB
            // Cualquier otro ZIP puede ser un cómic (se comprueba después que tenga imágenes).
            texto.startsWith("PK") -> Formato.CBZ
            texto.startsWith("Rar!\u001A\u0007\u0000") -> Formato.CBR
            else -> null
        }
    }

    private fun archivoExiste(uri: String?): Boolean {
        if (uri == null) return false
        return runCatching {
            DocumentFile.fromSingleUri(contexto, Uri.parse(uri))?.exists() == true
        }.getOrDefault(false)
    }

    private fun nombreDe(uri: Uri): String {
        val nombre = runCatching {
            contexto.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return nombre ?: uri.lastPathSegment?.substringAfterLast('/') ?: "libro"
    }

    /**
     * Saca título y autor del nombre del archivo, para los libros que no los traen dentro.
     * «Nombre+Apellido+-+Titulo+del+libro.pdf» → autor «Nombre Apellido»,
     * título «Aprendiendo de las drogas». Si no hay « - », todo es el título.
     */
    private fun datosDelNombre(nombre: String): Pair<String, String> {
        val limpio = runCatching { java.net.URLDecoder.decode(nombre.substringBeforeLast('.'), "UTF-8") }
            .getOrDefault(nombre.substringBeforeLast('.'))
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val partes = limpio.split(" - ", limit = 2).map { it.trim() }
        return if (partes.size == 2 && partes.all { it.isNotEmpty() }) {
            partes[1] to partes[0]
        } else {
            limpio.ifEmpty { "libro" } to ""
        }
    }

    private fun asegurarExtension(nombre: String, formato: Formato): String {
        val extension = ".${formato.name.lowercase()}"
        return if (nombre.endsWith(extension, ignoreCase = true)) nombre else nombre + extension
    }

    private fun tipoMime(formato: Formato): String = tipoMimeDe(formato)
}

/** El tipo de archivo (MIME) de cada formato, para crear el archivo en la carpeta. */
fun tipoMimeDe(formato: Formato): String = when (formato) {
    Formato.EPUB -> "application/epub+zip"
    Formato.PDF -> "application/pdf"
    Formato.CBZ -> "application/vnd.comicbook+zip"
    Formato.CBR -> "application/vnd.comicbook-rar"
}

/** Las portadas se guardan como JPG en la memoria privada de la app, una por libro. */
object Portadas {

    fun archivo(contexto: Context, idLibro: String): File =
        File(contexto.filesDir, "portadas/$idLibro.jpg")

    /** Usa una imagen elegida por el usuario (galería) como portada. */
    fun guardarDesdeImagen(contexto: Context, idLibro: String, imagen: Uri): Boolean = runCatching {
        val resolver = contexto.contentResolver
        // Primero se mira el tamaño sin cargar la imagen, para reducirla al leerla.
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(imagen)?.use { BitmapFactory.decodeStream(it, null, medidas) }
        var reduccion = 1
        while (medidas.outHeight / (reduccion * 2) >= 900) reduccion *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = reduccion }
        val bitmap = resolver.openInputStream(imagen)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: return false
        guardar(contexto, idLibro, bitmap)
    }.getOrDefault(false)

    /** Guarda la portada reducida (como mucho 900 px de alto). */
    fun guardar(contexto: Context, idLibro: String, imagen: Bitmap): Boolean = runCatching {
        val alto = minOf(imagen.height, 900)
        val ancho = (imagen.width * alto.toFloat() / imagen.height).toInt().coerceAtLeast(1)
        val reducida = Bitmap.createScaledBitmap(imagen, ancho, alto, true)
        val destino = archivo(contexto, idLibro)
        destino.parentFile?.mkdirs()
        destino.outputStream().use { reducida.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    }.getOrDefault(false)
}
