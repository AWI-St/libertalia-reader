package org.libertalia.reader.copias

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.biblioteca.Portadas
import org.libertalia.reader.biblioteca.tipoMimeDe
import org.libertalia.reader.datos.DiaLectura
import org.libertalia.reader.datos.Estanteria
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.LibroEnEstanteria
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.datos.TipoTarjeta
import org.libertalia.reader.widgets.Widgets
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/** Un libro o cómic tal como viene en una copia. */
data class LibroEnCopia(
    /** Sus datos (con el identificador de la copia y sin dirección de archivo). */
    val libro: Libro,
    val tarjetas: List<Tarjeta>,
    /** Identificadores (de la copia) de sus estanterías. */
    val estanterias: List<String>,
    val preferencias: JSONArray,
    /** Dónde está su archivo dentro del ZIP (null si la copia no lo trae). */
    val entradaArchivo: String?,
    val entradaPortada: String?,
)

/** Una copia ya leída (todavía sin aplicar). */
data class CopiaLeida(
    val archivo: File,
    val fecha: Long,
    val libros: List<LibroEnCopia>,
    val estanterias: List<Estanteria>,
    val dias: List<DiaLectura>,
)

/** Qué pasará con cada libro de la copia. */
enum class EstadoEnCopia {
    /** No está en esta biblioteca: se añade. */
    NUEVO,

    /** Ya está (misma huella): se unen sus datos y tarjetas. */
    YA_ESTA,

    /** Se había eliminado de la biblioteca: vuelve. */
    VUELVE,
}

data class AnalisisLibro(
    val libro: Libro,
    val estado: EstadoEnCopia,
    val tarjetasNuevas: Int,
    val destacadasNuevas: Int,
    val actualizadas: Int,
    /** Quedará «sin archivo»: la copia no lo trae y aquí tampoco está. */
    val sinArchivo: Boolean,
)

/** Resumen de lo que hará (o ha hecho) la importación. */
data class AnalisisCopia(val fecha: Long, val libros: List<AnalisisLibro>) {
    val nuevos get() = libros.count { it.estado != EstadoEnCopia.YA_ESTA }
    val yaEstaban get() = libros.count { it.estado == EstadoEnCopia.YA_ESTA }
    val tarjetasNuevas get() = libros.sumOf { it.tarjetasNuevas }
    val destacadasNuevas get() = libros.sumOf { it.destacadasNuevas }
    val actualizadas get() = libros.sumOf { it.actualizadas }
    val sinArchivo get() = libros.count { it.sinArchivo }
}

/** Por qué no se puede importar un archivo. */
class ErrorCopia(val motivo: Motivo) : Exception(motivo.name) {
    enum class Motivo {
        /** No es una copia de Libertalia (o está dañada). */
        NO_ES_COPIA,

        /** Es de una versión más nueva de la app, que esta no sabe leer. */
        VERSION_NUEVA,

        /** No se ha podido leer (sin permiso, sin espacio...). */
        ILEGIBLE,
    }
}

/**
 * Importa copias .libertalia (ver [FormatoCopia]):
 * 1. [leer]: se copia el archivo al móvil y se leen sus datos;
 * 2. [analizar]: se compara con la biblioteca, para enseñar un resumen antes de tocar nada;
 * 3. [aplicar]: se une con la biblioteca.
 *
 * Reglas al unir:
 * - Los libros se reconocen por su huella, aunque tengan otro nombre o identificador.
 * - Si un dato existe en los dos sitios, gana el cambio más reciente (los datos del libro por
 *   su fecha de modificación; la posición por la fecha de la última lectura; cada tarjeta por
 *   su fecha de modificación, también si se borró).
 * - Las estanterías se unen por identificador o por nombre; los días leídos se quedan con el
 *   tiempo mayor (así importar dos veces la misma copia no cambia nada).
 * - Si la copia no trae el archivo de un libro nuevo, queda «sin archivo» y se completa solo
 *   cuando el usuario añada ese archivo (el importador de libros lo reconoce por la huella).
 */
class ImportadorCopias(private val app: LibertaliaApp) {

    private val contexto: Context get() = app

    /** Copia el archivo [uri] al móvil y lee su contenido. [alAvanzar]: de 0 a 1. */
    suspend fun leer(uri: Uri, alAvanzar: (Float) -> Unit = {}): CopiaLeida = withContext(Dispatchers.IO) {
        val destino = File(contexto.cacheDir, "importar/copia.libertalia")
        destino.parentFile?.mkdirs()
        val total = tamanoDe(uri)
        try {
            val entrada = contexto.contentResolver.openInputStream(uri) ?: throw ErrorCopia(ErrorCopia.Motivo.ILEGIBLE)
            var leidos = 0L
            FlujoConAvance(entrada) { n ->
                leidos += n
                if (total > 0) alAvanzar((leidos.toFloat() / total).coerceIn(0f, 1f))
            }.use { flujo -> destino.outputStream().use { flujo.copyTo(it, 64 * 1024) } }
        } catch (e: ErrorCopia) {
            throw e
        } catch (e: Exception) {
            throw ErrorCopia(ErrorCopia.Motivo.ILEGIBLE)
        }
        try {
            ZipFile(destino).use { zip -> leerZip(destino, zip) }
        } catch (e: ErrorCopia) {
            destino.delete()
            throw e
        } catch (e: Exception) {
            destino.delete()
            throw ErrorCopia(ErrorCopia.Motivo.NO_ES_COPIA)
        }
    }

    private fun leerZip(archivo: File, zip: ZipFile): CopiaLeida {
        val manifiesto = zip.getEntry(FormatoCopia.MANIFIESTO)
            ?.let { e -> zip.getInputStream(e).use { JSONObject(it.readBytes().decodeToString()) } }
            ?: throw ErrorCopia(ErrorCopia.Motivo.NO_ES_COPIA)
        if (manifiesto.optInt("formato", 0) > FormatoCopia.VERSION) throw ErrorCopia(ErrorCopia.Motivo.VERSION_NUEVA)

        val libros = FormatoCopia.lista(manifiesto.optJSONArray("libros")) { it }.mapNotNull { entrada ->
            val carpeta = entrada.optString("carpeta").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val datos = zip.getEntry(carpeta + FormatoCopia.DATOS)
                ?.let { e -> zip.getInputStream(e).use { JSONObject(it.readBytes().decodeToString()) } }
                ?: return@mapNotNull null
            val libro = FormatoCopia.libroDeJson(datos.getJSONObject("libro"), uriArchivo = null)
            val estanterias = datos.optJSONArray("estanterias")
                ?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            LibroEnCopia(
                libro = libro,
                tarjetas = FormatoCopia.lista(datos.optJSONArray("tarjetas")) { FormatoCopia.tarjetaDeJson(it, libro.id) },
                estanterias = estanterias,
                preferencias = datos.optJSONArray("preferencias") ?: JSONArray(),
                entradaArchivo = (carpeta + FormatoCopia.nombreArchivo(libro.formato)).takeIf { zip.getEntry(it) != null },
                entradaPortada = (carpeta + FormatoCopia.PORTADA).takeIf { zip.getEntry(it) != null },
            )
        }
        return CopiaLeida(
            archivo = archivo,
            fecha = manifiesto.optLong("fecha"),
            libros = libros,
            estanterias = FormatoCopia.lista(manifiesto.optJSONArray("estanterias"), FormatoCopia::estanteriaDeJson),
            dias = FormatoCopia.lista(manifiesto.optJSONArray("diasLectura"), FormatoCopia::diaDeJson),
        )
    }

    /** Lo que pasará con cada libro, sin cambiar nada todavía. */
    suspend fun analizar(copia: CopiaLeida): AnalisisCopia = withContext(Dispatchers.IO) {
        val bd = app.baseDatos
        AnalisisCopia(
            fecha = copia.fecha,
            libros = copia.libros.map { c ->
                val local = bd.libros().buscarPorHuella(c.libro.huella)
                var nuevasT = 0
                var nuevasD = 0
                var actualizadas = 0
                for (t in c.tarjetas) {
                    val existente = bd.tarjetas().buscarPorId(t.id)
                    when {
                        existente == null && !t.eliminada -> if (t.tipo == TipoTarjeta.PAGINA) nuevasD++ else nuevasT++
                        existente != null && t.fechaModificacion > existente.fechaModificacion -> actualizadas++
                    }
                }
                AnalisisLibro(
                    libro = local?.copy(titulo = c.libro.titulo) ?: c.libro,
                    estado = when {
                        local == null -> EstadoEnCopia.NUEVO
                        local.eliminado -> EstadoEnCopia.VUELVE
                        else -> EstadoEnCopia.YA_ESTA
                    },
                    tarjetasNuevas = nuevasT,
                    destacadasNuevas = nuevasD,
                    actualizadas = actualizadas,
                    sinArchivo = c.entradaArchivo == null && local?.uriArchivo == null,
                )
            },
        )
    }

    /** Une la copia con la biblioteca. Devuelve lo que se ha hecho. [alAvanzar]: de 0 a 1. */
    suspend fun aplicar(copia: CopiaLeida, alAvanzar: (Float) -> Unit = {}): AnalisisCopia = withContext(Dispatchers.IO) {
        val analisis = analizar(copia)
        val bd = app.baseDatos
        ZipFile(copia.archivo).use { zip ->
            val total = copia.libros.sumOf { c ->
                (c.entradaArchivo?.let { zip.getEntry(it)?.size } ?: 0L) + POR_LIBRO
            }.coerceAtLeast(1)
            var hecho = 0L
            val avanzar = { n: Long ->
                hecho += n
                alAvanzar((hecho.toFloat() / total).coerceIn(0f, 1f))
            }

            // 1. Estanterías: se unen por identificador o, si no, por nombre.
            val mapaEstanterias = unirEstanterias(copia.estanterias)
            // 2. Cada libro, con su archivo, portada, tarjetas y estanterías.
            for (c in copia.libros) {
                aplicarLibro(zip, c, mapaEstanterias, avanzar)
                avanzar(POR_LIBRO)
            }
        }
        // 3. Los días leídos (la racha): de cada día, el tiempo mayor.
        copia.dias.forEach {
            bd.diasLectura().guardarMaximo(it.fecha, it.segundos, it.segundosLibros, it.segundosComics, it.palabras, it.segundosPalabras)
        }
        Widgets.actualizar(app)
        alAvanzar(1f)
        analisis
    }

    /** Borra el archivo temporal de la copia. */
    fun descartar(copia: CopiaLeida?) {
        copia?.archivo?.delete()
    }

    private suspend fun unirEstanterias(deCopia: List<Estanteria>): Map<String, String> {
        val dao = app.baseDatos.estanterias()
        val locales = dao.todas().toMutableList()
        val mapa = mutableMapOf<String, String>()
        for (e in deCopia) {
            val porId = locales.firstOrNull { it.id == e.id }
            val porNombre = locales.firstOrNull { it.nombre.trim().equals(e.nombre.trim(), ignoreCase = true) }
            when {
                porId != null -> {
                    // La misma estantería: si en la copia se renombró después, gana ese nombre.
                    if (e.fechaModificacion > porId.fechaModificacion && e.nombre != porId.nombre) {
                        dao.renombrar(porId.id, e.nombre, e.fechaModificacion)
                    }
                    mapa[e.id] = porId.id
                }
                porNombre != null -> mapa[e.id] = porNombre.id
                else -> {
                    val nueva = e.copy(orden = dao.ordenMaximo() + 1)
                    dao.insertar(nueva)
                    locales += nueva
                    mapa[e.id] = nueva.id
                }
            }
        }
        return mapa
    }

    private suspend fun aplicarLibro(
        zip: ZipFile,
        c: LibroEnCopia,
        mapaEstanterias: Map<String, String>,
        avanzar: (Long) -> Unit,
    ) {
        val bd = app.baseDatos
        val deCopia = c.libro
        val local = bd.libros().buscarPorHuella(deCopia.huella)
        // El identificador: el de aquí si ya estaba; si no, el de la copia (si está libre).
        val id = local?.id ?: if (bd.libros().buscarPorId(deCopia.id) == null) deCopia.id else UUID.randomUUID().toString()

        // 1. El archivo: solo si aquí no está (los cómics también van a la carpeta de la app,
        //    porque en un móvil nuevo no hay otro sitio desde donde abrirlos).
        var uri = local?.uriArchivo?.takeIf(::archivoExiste)
        var nombre = local?.nombreArchivo
        var tamano = local?.tamanoBytes ?: deCopia.tamanoBytes
        if (uri == null && c.entradaArchivo != null) {
            val entrada = zip.getEntry(c.entradaArchivo)
            val nombreDeseado = deCopia.nombreArchivo?.takeIf { it.isNotBlank() }
                ?: (deCopia.titulo + "." + deCopia.formato.name.lowercase())
            val guardado = app.carpeta.buscarIgual(entrada.size) { huellaDe(it.uri) == deCopia.huella }
                ?.also { avanzar(entrada.size) }
                ?: zip.getInputStream(entrada).use { flujo ->
                    app.carpeta.guardarArchivo(nombreDeseado, tipoMimeDe(deCopia.formato), FlujoConAvance(flujo, avanzar))
                }
            if (guardado != null) {
                uri = guardado.uri.toString()
                nombre = guardado.name
                tamano = entrada.size
            }
        }

        // 2. La portada (si aquí no tiene).
        val portada = Portadas.archivo(contexto, id)
        if (!portada.exists() && c.entradaPortada != null) {
            portada.parentFile?.mkdirs()
            runCatching {
                zip.getInputStream(zip.getEntry(c.entradaPortada)).use { e -> portada.outputStream().use { e.copyTo(it) } }
            }
        }

        // 3. Sus datos.
        if (local == null) {
            bd.libros().insertar(
                deCopia.copy(
                    id = id,
                    uriArchivo = uri,
                    nombreArchivo = nombre,
                    tamanoBytes = tamano,
                    tienePortada = portada.exists(),
                    eliminado = false,
                ),
            )
        } else {
            var unido = local.copy(
                uriArchivo = uri ?: local.uriArchivo,
                nombreArchivo = nombre,
                tamanoBytes = tamano,
                tienePortada = portada.exists(),
                eliminado = false,
            )
            // Título, estado, marcapáginas...: gana el cambio más reciente.
            if (deCopia.fechaModificacion > local.fechaModificacion) {
                unido = unido.copy(
                    titulo = deCopia.titulo,
                    autor = deCopia.autor,
                    tomo = deCopia.tomo,
                    tipo = deCopia.tipo,
                    estado = deCopia.estado,
                    sentidoLectura = deCopia.sentidoLectura,
                    ajustesTexto = deCopia.ajustesTexto ?: local.ajustesTexto,
                    marcapaginas = deCopia.marcapaginas,
                    incluirArchivoEnCopia = deCopia.incluirArchivoEnCopia,
                    fechaModificacion = deCopia.fechaModificacion,
                )
            }
            // La posición: gana la lectura más reciente.
            if ((deCopia.fechaUltimaLectura ?: 0L) > (local.fechaUltimaLectura ?: 0L)) {
                unido = unido.copy(
                    posicion = deCopia.posicion,
                    progreso = deCopia.progreso,
                    capituloActual = deCopia.capituloActual,
                    paginaLector = deCopia.paginaLector,
                    paginasLector = deCopia.paginasLector,
                    fechaUltimaLectura = deCopia.fechaUltimaLectura,
                )
            }
            bd.libros().actualizar(unido)
        }

        // 4. Las tarjetas: se unen por su identificador; gana la editada más tarde.
        for (t in c.tarjetas) {
            val tarjeta = t.copy(idLibro = id)
            val existente = bd.tarjetas().buscarPorId(t.id)
            when {
                existente == null -> bd.tarjetas().insertar(tarjeta)
                t.fechaModificacion > existente.fechaModificacion -> bd.tarjetas().actualizar(tarjeta)
            }
        }

        // 5. Sus estanterías.
        c.estanterias.mapNotNull { mapaEstanterias[it] }.forEach {
            bd.estanterias().anadirLibro(LibroEnEstanteria(libroId = id, estanteriaId = it))
        }

        // 6. Sus ajustes guardados fuera de la base de datos (modo texto, modo del cómic...),
        //    solo si aquí no tiene ya los suyos.
        for (i in 0 until c.preferencias.length()) {
            val p = c.preferencias.optJSONObject(i) ?: continue
            val archivo = p.optString("archivo")
            if (archivo !in Exportador.PREFERENCIAS_POR_LIBRO) continue
            val clave = p.optString("clave") + "_" + id
            val prefs = contexto.getSharedPreferences(archivo, Context.MODE_PRIVATE)
            if (prefs.contains(clave)) continue
            prefs.edit {
                when (p.optString("tipo")) {
                    "boolean" -> putBoolean(clave, p.optBoolean("valor"))
                    "string" -> putString(clave, p.optString("valor"))
                    "int" -> putInt(clave, p.optInt("valor"))
                    "long" -> putLong(clave, p.optLong("valor"))
                    "float" -> putFloat(clave, p.optDouble("valor").toFloat())
                }
            }
        }
    }

    private fun archivoExiste(uri: String): Boolean = runCatching {
        DocumentFile.fromSingleUri(contexto, Uri.parse(uri))?.exists() == true
    }.getOrDefault(false)

    private fun huellaDe(uri: Uri): String? = runCatching {
        val resumen = MessageDigest.getInstance("SHA-256")
        contexto.contentResolver.openInputStream(uri)?.use { entrada ->
            val bufer = ByteArray(64 * 1024)
            while (true) {
                val leidos = entrada.read(bufer)
                if (leidos < 0) break
                resumen.update(bufer, 0, leidos)
            }
        } ?: return null
        resumen.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun tamanoDe(uri: Uri): Long = runCatching {
        contexto.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
        }
    }.getOrNull() ?: -1L

    private companion object {
        /** Lo que «pesa» en la barra de progreso cada libro, además de su archivo. */
        const val POR_LIBRO = 32 * 1024L
    }
}

/** Un flujo que avisa de cuánto se va leyendo (para las barras de progreso). */
class FlujoConAvance(entrada: InputStream, private val alLeer: (Long) -> Unit) : FilterInputStream(entrada) {
    override fun read(): Int = super.read().also { if (it >= 0) alLeer(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) alLeer(it.toLong()) }
}
