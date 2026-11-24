package org.libertalia.reader.comic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.github.junrar.Archive
import com.github.junrar.io.SeekableReadOnlyByteChannel
import com.github.junrar.rarfile.FileHeader
import com.github.junrar.volume.Volume
import com.github.junrar.volume.VolumeManager
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater
import kotlin.math.max
import kotlin.math.min

/**
 * Las páginas de un cómic, leídas directamente del archivo original (sin copiarlo):
 * se salta a cada página cuando hace falta, en vez de descomprimirlo todo.
 */
interface FuentePaginas : Closeable {
    val total: Int

    /**
     * La página [indice] (desde 0), reducida para que quepa en [ancho] x [alto] píxeles
     * y sin pasar de [maximoLado] por ningún lado.
     */
    fun pagina(indice: Int, ancho: Int, alto: Int, maximoLado: Int = MAXIMO_LADO): Bitmap?

    companion object {
        /**
         * Abre el cómic. Si el archivo no permite saltar de un sitio a otro (algunas nubes
         * lo dan como un «tubo» que solo se lee de principio a fin), se copia a la caché de
         * la app mientras se lee y se borra al cerrar.
         */
        fun abrir(contexto: Context, uri: Uri, formato: String, idLibro: String): FuentePaginas {
            val acceso = AccesoArchivo.abrir(contexto, uri, idLibro)
            return try {
                when (formato) {
                    "CBZ" -> FuenteZip(acceso)
                    "CBR" -> FuenteRar(acceso, File(contexto.cacheDir, "lectura/$idLibro-paginas"))
                    else -> FuentePdf(acceso)
                }
            } catch (e: Exception) {
                acceso.close()
                throw e
            }
        }
    }
}

/** El archivo abierto para leer en cualquier posición (con su copia temporal, si hizo falta). */
class AccesoArchivo private constructor(
    val descriptor: ParcelFileDescriptor,
    private val temporal: File?,
) : Closeable {
    val canal: FileChannel = FileInputStream(descriptor.fileDescriptor).channel

    override fun close() {
        runCatching { canal.close() }
        runCatching { descriptor.close() }
        temporal?.delete()
    }

    companion object {
        fun abrir(contexto: Context, uri: Uri, idLibro: String): AccesoArchivo {
            val resolver = contexto.contentResolver
            val directo = resolver.openFileDescriptor(uri, "r")
            if (directo != null && sePuedeSaltar(directo)) return AccesoArchivo(directo, null)
            runCatching { directo?.close() }
            // Copia temporal mientras se lee (se borra al cerrar el cómic).
            val temporal = File(contexto.cacheDir, "lectura/$idLibro.tmp")
            temporal.parentFile?.mkdirs()
            resolver.openInputStream(uri)!!.use { entrada -> temporal.outputStream().use { entrada.copyTo(it) } }
            return AccesoArchivo(ParcelFileDescriptor.open(temporal, ParcelFileDescriptor.MODE_READ_ONLY), temporal)
        }

        private fun sePuedeSaltar(descriptor: ParcelFileDescriptor): Boolean = runCatching {
            val canal = FileInputStream(descriptor.fileDescriptor).channel
            canal.size() > 0 && run {
                canal.position(canal.size() - 1)
                canal.position(0)
                true
            }
        }.getOrDefault(false)
    }
}

/** Reduce una imagen al leerla para no gastar más memoria de la que se ve en pantalla. */
private fun decodificar(bytes: ByteArray, ancho: Int, alto: Int, maximoLado: Int): Bitmap? {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
    if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
    var reduccion = 1
    while (medidas.outWidth / (reduccion * 2) >= ancho && medidas.outHeight / (reduccion * 2) >= alto) reduccion *= 2
    val opciones = BitmapFactory.Options().apply {
        inSampleSize = reduccion
        // Sin transparencia: la mitad de memoria por página.
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val imagen = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones) ?: return null
    // Lo anterior solo reduce a la mitad, a la cuarta parte...: puede quedar hasta el doble de
    // lo pedido. Las páginas muy grandes (más de ~4000 píxeles) la tarjeta gráfica no las
    // dibuja y se ven en negro, sobre todo al ampliar. Se ajustan al tamaño pedido.
    val escala = min(min(ancho.toFloat() / imagen.width, alto.toFloat() / imagen.height), maximoLado.toFloat() / max(imagen.width, imagen.height))
    if (escala >= 1f) return imagen
    val ajustada = Bitmap.createScaledBitmap(
        imagen,
        max(1, (imagen.width * escala).toInt()),
        max(1, (imagen.height * escala).toInt()),
        true,
    )
    if (ajustada !== imagen) imagen.recycle()
    return ajustada
}

/** Lado máximo de una página dibujada (las tarjetas gráficas no dibujan imágenes mucho mayores). */
const val MAXIMO_LADO = 4096

/**
 * CBZ: un ZIP. Se lee su índice (al final del archivo) y, para cada página, se salta
 * directamente a ella. Las imágenes suelen ir sin comprimir o comprimidas con «deflate».
 */
private class FuenteZip(private val acceso: AccesoArchivo) : FuentePaginas {
    private class Entrada(val nombre: String, val metodo: Int, val comprimido: Long, val tamano: Long, val desplazamiento: Long)

    private val canal = acceso.canal
    private val entradas: List<Entrada>

    init {
        val todas = leerIndice()
        entradas = todas.filter { ArchivoComic.esImagen(it.nombre) }
            .sortedWith(compareBy(ArchivoComic.ORDEN_NATURAL) { it.nombre })
    }

    override val total get() = entradas.size

    private fun leer(posicion: Long, cuantos: Int): ByteBuffer {
        val bufer = ByteBuffer.allocate(cuantos).order(ByteOrder.LITTLE_ENDIAN)
        var leidos = 0
        while (leidos < cuantos) {
            val n = canal.read(bufer, posicion + leidos)
            if (n < 0) break
            leidos += n
        }
        bufer.flip()
        return bufer
    }

    private fun leerIndice(): List<Entrada> {
        val tamano = canal.size()
        // El final del índice («End of central directory») está en los últimos 64 KB.
        val cola = min(tamano, 66_000L).toInt()
        val final = leer(tamano - cola, cola)
        var pos = cola - 22
        while (pos >= 0 && final.getInt(pos) != 0x06054b50) pos--
        require(pos >= 0) { "No es un ZIP" }
        var numero = final.getShort(pos + 10).toInt() and 0xFFFF
        var tamanoIndice = final.getInt(pos + 12).toLong() and 0xFFFFFFFFL
        var inicioIndice = final.getInt(pos + 16).toLong() and 0xFFFFFFFFL
        // ZIP64 (archivos de más de 4 GB o con muchísimas entradas).
        if (inicioIndice == 0xFFFFFFFFL || numero == 0xFFFF) {
            val localizador = pos - 20
            if (localizador >= 0 && final.getInt(localizador) == 0x07064b50) {
                val registro = final.getLong(localizador + 8)
                val r = leer(registro, 56)
                numero = r.getLong(32).toInt()
                tamanoIndice = r.getLong(40)
                inicioIndice = r.getLong(48)
            }
        }
        val indice = leer(inicioIndice, tamanoIndice.toInt())
        val lista = ArrayList<Entrada>(numero)
        var p = 0
        repeat(numero) {
            if (p + 46 > indice.limit() || indice.getInt(p) != 0x02014b50) return lista
            val banderas = indice.getShort(p + 8).toInt()
            val metodo = indice.getShort(p + 10).toInt() and 0xFFFF
            var comprimido = indice.getInt(p + 20).toLong() and 0xFFFFFFFFL
            var real = indice.getInt(p + 24).toLong() and 0xFFFFFFFFL
            val largoNombre = indice.getShort(p + 28).toInt() and 0xFFFF
            val largoExtra = indice.getShort(p + 30).toInt() and 0xFFFF
            val largoComentario = indice.getShort(p + 32).toInt() and 0xFFFF
            var desplazamiento = indice.getInt(p + 42).toLong() and 0xFFFFFFFFL
            val nombreBytes = ByteArray(largoNombre).also { indice.position(p + 46); indice.get(it) }
            // Bit 11: nombre en UTF-8; si no, el antiguo juego de caracteres de MS-DOS.
            val nombre = if (banderas and 0x800 != 0) String(nombreBytes, Charsets.UTF_8) else String(nombreBytes, charset("IBM437"))
            // Campo extra ZIP64 con los tamaños y la posición de verdad.
            var e = p + 46 + largoNombre
            val finExtra = e + largoExtra
            while (e + 4 <= finExtra) {
                val id = indice.getShort(e).toInt() and 0xFFFF
                val largo = indice.getShort(e + 2).toInt() and 0xFFFF
                if (id == 0x0001) {
                    var q = e + 4
                    if (real == 0xFFFFFFFFL) { real = indice.getLong(q); q += 8 }
                    if (comprimido == 0xFFFFFFFFL) { comprimido = indice.getLong(q); q += 8 }
                    if (desplazamiento == 0xFFFFFFFFL) desplazamiento = indice.getLong(q)
                }
                e += 4 + largo
            }
            if (!nombre.endsWith("/")) lista += Entrada(nombre, metodo, comprimido, real, desplazamiento)
            p += 46 + largoNombre + largoExtra + largoComentario
        }
        return lista
    }

    private fun bytesDe(entrada: Entrada): ByteArray {
        // Cabecera local: 30 bytes + nombre + extra; después, los datos.
        val cabecera = leer(entrada.desplazamiento, 30)
        val inicio = entrada.desplazamiento + 30 +
            (cabecera.getShort(26).toInt() and 0xFFFF) + (cabecera.getShort(28).toInt() and 0xFFFF)
        val datos = leer(inicio, entrada.comprimido.toInt())
        val crudos = ByteArray(datos.remaining()).also { datos.get(it) }
        return when (entrada.metodo) {
            0 -> crudos
            8 -> {
                val inflador = Inflater(true)
                inflador.setInput(crudos)
                val salida = ByteArray(entrada.tamano.toInt())
                var hechos = 0
                while (!inflador.finished() && hechos < salida.size) {
                    val n = inflador.inflate(salida, hechos, salida.size - hechos)
                    if (n == 0 && (inflador.needsInput() || inflador.needsDictionary())) break
                    hechos += n
                }
                inflador.end()
                salida
            }
            else -> error("Compresión no admitida: ${entrada.metodo}")
        }
    }

    @Synchronized
    override fun pagina(indice: Int, ancho: Int, alto: Int, maximoLado: Int): Bitmap? =
        entradas.getOrNull(indice)?.let { decodificar(bytesDe(it), ancho, alto, maximoLado) }

    override fun close() = acceso.close()
}

/**
 * CBR: RAR 4 leído con junrar a través del archivo abierto (sin copiarlo).
 *
 * Algunos RAR son «sólidos»: todas las páginas van comprimidas en un solo bloque y, para
 * sacar la página 8, hay que descomprimir antes de la 1 a la 7. En esos se van sacando las
 * páginas en orden según se avanza, y se guardan en [carpetaTemporal] mientras se lee
 * (se borra al cerrar el cómic).
 */
private class FuenteRar(private val acceso: AccesoArchivo, private val carpetaTemporal: File) : FuentePaginas {

    /** Para junrar: el archivo como «volumen» en el que se puede saltar de un sitio a otro. */
    private class Canal(private val canal: FileChannel) : SeekableReadOnlyByteChannel {
        override fun getPosition(): Long = canal.position()
        override fun setPosition(pos: Long) { canal.position(pos) }
        override fun read(): Int {
            val uno = ByteBuffer.allocate(1)
            return if (canal.read(uno) <= 0) -1 else uno.get(0).toInt() and 0xFF
        }
        override fun read(destino: ByteArray, desde: Int, cuantos: Int): Int =
            canal.read(ByteBuffer.wrap(destino, desde, cuantos))
        override fun readFully(destino: ByteArray, cuantos: Int): Int {
            var leidos = 0
            while (leidos < cuantos) {
                val n = canal.read(ByteBuffer.wrap(destino, leidos, cuantos - leidos))
                if (n < 0) break
                leidos += n
            }
            return leidos
        }
        override fun close() = Unit // lo cierra AccesoArchivo
    }

    private fun abrirRar(): Archive = Archive(
        object : VolumeManager {
            override fun nextVolume(archivo: Archive, ultimo: Volume?): Volume? =
                if (ultimo != null) null else object : Volume {
                    override fun getChannel(): SeekableReadOnlyByteChannel = Canal(acceso.canal)
                    override fun getLength(): Long = acceso.canal.size()
                    override fun getArchive(): Archive = archivo
                }
        },
        null,
        null,
    )

    private var rar: Archive = abrirRar()
    private val solido = rar.mainHeader.isSolid

    /** Todas las entradas en el orden en que están guardadas (el que manda en los RAR sólidos). */
    private var enOrden: List<FileHeader> = rar.fileHeaders

    /** En un RAR sólido: la siguiente entrada que toca descomprimir. */
    private var siguiente = 0

    private val paginas: List<FileHeader> = rar.fileHeaders
        .filter { !it.isDirectory && ArchivoComic.esImagen(it.fileName.replace('\\', '/')) }
        .sortedWith(compareBy(ArchivoComic.ORDEN_NATURAL) { it.fileName.replace('\\', '/') })

    override val total get() = paginas.size

    @Synchronized
    override fun pagina(indice: Int, ancho: Int, alto: Int, maximoLado: Int): Bitmap? {
        val cabecera = paginas.getOrNull(indice) ?: return null
        if (!solido) {
            val bytes = ByteArrayOutputStream().also { rar.extractFile(cabecera, it) }.toByteArray()
            return decodificar(bytes, ancho, alto, maximoLado)
        }
        val posicion = enOrden.indexOfFirst { it.fileName == cabecera.fileName }
        val guardada = File(carpetaTemporal, posicion.toString())
        if (!guardada.exists()) {
            carpetaTemporal.mkdirs()
            // Si ya se pasó de largo (y no se guardó), hay que empezar de nuevo desde el principio.
            if (posicion < siguiente) {
                runCatching { rar.close() }
                acceso.canal.position(0)
                rar = abrirRar()
                enOrden = rar.fileHeaders
                siguiente = 0
            }
            while (siguiente <= posicion) {
                val entrada = enOrden[siguiente]
                val destino = File(carpetaTemporal, siguiente.toString())
                if (!entrada.isDirectory) {
                    val temporal = File(carpetaTemporal, "$siguiente.parcial")
                    temporal.outputStream().use { rar.extractFile(entrada, it) }
                    temporal.renameTo(destino)
                }
                siguiente++
            }
        }
        return decodificar(guardada.readBytes(), ancho, alto, maximoLado)
    }

    override fun close() {
        runCatching { rar.close() }
        carpetaTemporal.deleteRecursively()
        acceso.close()
    }
}

/** PDF de cómic: cada página se dibuja con el lector de PDF de Android. */
private class FuentePdf(private val acceso: AccesoArchivo) : FuentePaginas {
    private val pdf = PdfRenderer(acceso.descriptor)

    override val total get() = pdf.pageCount

    @Synchronized
    override fun pagina(indice: Int, ancho: Int, alto: Int, maximoLado: Int): Bitmap? {
        if (indice !in 0 until pdf.pageCount) return null
        pdf.openPage(indice).use { hoja ->
            val escala = minOf(ancho.toFloat() / hoja.width, alto.toFloat() / hoja.height, maximoLado.toFloat() / max(hoja.width, hoja.height))
            val w = max(1, (hoja.width * escala).toInt())
            val h = max(1, (hoja.height * escala).toInt())
            val imagen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            imagen.eraseColor(Color.WHITE)
            hoja.render(imagen, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return imagen
        }
    }

    override fun close() {
        runCatching { pdf.close() }
        acceso.close()
    }
}
