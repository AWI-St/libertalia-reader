package org.libertalia.reader.copias

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libertalia.reader.LibertaliaApp
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Cómo ha ido el último intento de hacer la copia automática. */
enum class ResultadoCopia {
    CORRECTO,

    /** No había cambios desde la última copia correcta: no se ha rehecho. */
    SIN_CAMBIOS,

    /** Todavía no se ha elegido dónde guardarla. */
    SIN_DESTINO,

    /** Android ya no deja escribir en ese archivo (permiso retirado). */
    SIN_PERMISO,

    /** El archivo ya no existe (por ejemplo, se borró en Drive). */
    NO_EXISTE,

    /** Otro fallo (sin espacio, la app de la nube no responde...). */
    ERROR,
}

/** Lo que se enseña en Ajustes. */
data class InfoCopiaAutomatica(
    val uri: Uri?,
    val nombre: String?,
    /** Cada cuántos días (0 = nunca). */
    val dias: Int,
    val fechaUltimaCorrecta: Long?,
    val ultimoResultado: ResultadoCopia?,
) {
    val falla get() = ultimoResultado in FALLOS

    companion object {
        val FALLOS = setOf(ResultadoCopia.SIN_PERMISO, ResultadoCopia.NO_EXISTE, ResultadoCopia.ERROR)
    }
}

/**
 * La copia automática: un único archivo «copia.libertalia» en el sitio que elija el usuario
 * (el móvil, Google Drive u otra nube instalada) que se sobrescribe en cada copia. La subida a
 * la nube la hace la app de la nube; Libertalia no inicia sesión en nada.
 *
 * - Lleva todos los datos de todos los libros y los archivos de los que tienen activado
 *   «Incluir el archivo en la copia» ([org.libertalia.reader.datos.Libro.incluirArchivoEnCopia]).
 * - Si no ha cambiado nada desde la última copia correcta, no se rehace.
 * - Se prepara entera en el móvil y solo después se sustituye el archivo; luego se vuelve a
 *   leer y se compara su huella (con Drive no vale mirar el tamaño: lo actualiza más tarde).
 * - WorkManager la lanza cada [InfoCopiaAutomatica.dias] días ([TrabajoCopia]).
 */
class CopiaAutomatica(private val app: LibertaliaApp) {

    private val contexto: Context get() = app
    private val prefs = app.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    init {
        // El archivo elegido en la prueba de la fase 3 pasa a ser el de la copia automática.
        if (!prefs.contains(CLAVE_URI)) {
            app.getSharedPreferences("prueba_copia", Context.MODE_PRIVATE).getString("uri", null)?.let { antiguo ->
                prefs.edit { putString(CLAVE_URI, antiguo) }
            }
        }
    }

    fun info(): InfoCopiaAutomatica {
        val uri = prefs.getString(CLAVE_URI, null)?.let(Uri::parse)
        return InfoCopiaAutomatica(
            uri = uri,
            nombre = uri?.let(::nombreDe),
            dias = prefs.getInt(CLAVE_DIAS, DIAS_POR_DEFECTO),
            fechaUltimaCorrecta = prefs.getLong(CLAVE_FECHA, 0L).takeIf { it > 0 },
            ultimoResultado = prefs.getString(CLAVE_RESULTADO, null)?.let { runCatching { ResultadoCopia.valueOf(it) }.getOrNull() },
        )
    }

    /** El archivo elegido con el selector de Android: se pide permiso permanente y se programa. */
    fun elegirDestino(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contexto.contentResolver.takePersistableUriPermission(uri, flags) }
        prefs.edit {
            putString(CLAVE_URI, uri.toString())
            remove(CLAVE_HUELLA)
            remove(CLAVE_RESULTADO)
        }
        programar(reemplazar = true)
        // La primera copia se hace ya; después, cada pocos días.
        hacerAhoraEnSegundoPlano()
    }

    fun cambiarFrecuencia(dias: Int) {
        prefs.edit { putInt(CLAVE_DIAS, dias) }
        programar(reemplazar = true)
    }

    /**
     * Programa (o quita) la copia periódica. Con [reemplazar] = false (al abrir la app) no se
     * toca una ya programada, para no retrasarla.
     */
    fun programar(reemplazar: Boolean = false) {
        val trabajos = WorkManager.getInstance(contexto)
        val info = info()
        if (info.uri == null || info.dias <= 0) {
            trabajos.cancelUniqueWork(TRABAJO_PERIODICO)
            return
        }
        val peticion = PeriodicWorkRequestBuilder<TrabajoCopia>(info.dias.toLong(), TimeUnit.DAYS)
            // Con batería suficiente (WorkManager espera si está baja).
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setInitialDelay(info.dias.toLong(), TimeUnit.DAYS)
            .build()
        trabajos.enqueueUniquePeriodicWork(
            TRABAJO_PERIODICO,
            if (reemplazar) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            peticion,
        )
    }

    /** «Hacer copia ahora»: en segundo plano, aunque se salga de la pantalla. */
    fun hacerAhoraEnSegundoPlano() {
        val peticion = OneTimeWorkRequestBuilder<TrabajoCopia>()
            .setInputData(androidx.work.workDataOf(TrabajoCopia.FORZAR to true))
            .build()
        WorkManager.getInstance(contexto).enqueueUniqueWork(TRABAJO_AHORA, ExistingWorkPolicy.KEEP, peticion)
    }

    /**
     * Hace la copia. Con [forzar] = false, si no ha cambiado nada desde la última copia
     * correcta, no se rehace ([ResultadoCopia.SIN_CAMBIOS]).
     */
    suspend fun hacer(forzar: Boolean, alAvanzar: (Float) -> Unit = {}): ResultadoCopia = withContext(Dispatchers.IO) {
        val uri = prefs.getString(CLAVE_URI, null)?.let(Uri::parse) ?: return@withContext ResultadoCopia.SIN_DESTINO
        val huellaDatos = huellaDeLosDatos()
        if (!forzar && huellaDatos == prefs.getString(CLAVE_HUELLA, null) &&
            prefs.getString(CLAVE_RESULTADO, null) == ResultadoCopia.CORRECTO.name
        ) {
            return@withContext ResultadoCopia.SIN_CAMBIOS
        }

        // 1. Preparar la copia completa en el móvil.
        val temporal = File(contexto.cacheDir, "copia-automatica/copia.libertalia")
        val resultado = try {
            val libros = app.baseDatos.libros().todos()
            Exportador(app).exportar(libros, { it.incluirArchivoEnCopia }, temporal, alAvanzar)
            // 2. Sustituir el archivo elegido por la copia ya preparada.
            Exportador.escribirEn(contexto, uri, temporal)
            // 3. Comprobar que ha quedado igual: se vuelve a leer y se compara su huella.
            if (huella(contexto.contentResolver.openInputStream(uri)) == huella(temporal.inputStream())) {
                ResultadoCopia.CORRECTO
            } else {
                ResultadoCopia.ERROR
            }
        } catch (e: SecurityException) {
            ResultadoCopia.SIN_PERMISO
        } catch (e: FileNotFoundException) {
            ResultadoCopia.NO_EXISTE
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ResultadoCopia.ERROR
        } finally {
            temporal.delete()
        }

        prefs.edit {
            putString(CLAVE_RESULTADO, resultado.name)
            if (resultado == ResultadoCopia.CORRECTO) {
                putLong(CLAVE_FECHA, System.currentTimeMillis())
                putString(CLAVE_HUELLA, huellaDatos)
            }
        }
        resultado
    }

    /** Lo que pesará más o menos la copia (datos y los archivos incluidos). */
    suspend fun pesoAproximado(): Long = withContext(Dispatchers.IO) {
        val exportador = Exportador(app)
        app.baseDatos.libros().todos().sumOf { exportador.pesoEstimado(it, it.incluirArchivoEnCopia) }
    }

    /**
     * Una «huella» de todo lo que va en la copia: si no cambia, la copia sería igual.
     * (Libros con sus fechas, tarjetas, estanterías, días leídos y ajustes por libro.)
     */
    private suspend fun huellaDeLosDatos(): String {
        val bd = app.baseDatos
        val resumen = MessageDigest.getInstance("SHA-256")
        fun sumar(texto: String) = resumen.update(texto.toByteArray())
        for (l in bd.libros().todos()) {
            sumar(FormatoCopia.libroAJson(l).toString())
            sumar(l.uriArchivo.orEmpty())
            for (t in bd.tarjetas().todasDeLibro(l.id)) sumar("${t.id}:${t.fechaModificacion}:${t.eliminada}")
        }
        bd.estanterias().todas().forEach { sumar("${it.id}:${it.nombre}:${it.orden}") }
        bd.estanterias().relaciones().forEach { sumar("${it.libroId}>${it.estanteriaId}") }
        bd.diasLectura().desde("").forEach { sumar("${it.fecha}=${it.segundos}/${it.palabras}") }
        for (archivo in Exportador.PREFERENCIAS_POR_LIBRO) {
            contexto.getSharedPreferences(archivo, Context.MODE_PRIVATE).all.toSortedMap().forEach { (k, v) -> sumar("$k=$v") }
        }
        return resumen.digest().joinToString("") { "%02x".format(it) }
    }

    private fun huella(entrada: InputStream?): String? = entrada?.use {
        val resumen = MessageDigest.getInstance("SHA-256")
        val bufer = ByteArray(64 * 1024)
        while (true) {
            val leidos = it.read(bufer)
            if (leidos < 0) break
            resumen.update(bufer, 0, leidos)
        }
        resumen.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun nombreDe(uri: Uri): String? = runCatching {
        contexto.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    companion object {
        const val NOMBRE_SUGERIDO = "copia.libertalia"
        const val DIAS_POR_DEFECTO = 3

        /** Frecuencias que se pueden elegir (en días; 0 = nunca). */
        val FRECUENCIAS = listOf(1, 2, 3, 7, 15, 30, 0)

        private const val ARCHIVO = "copia_automatica"
        private const val CLAVE_URI = "uri"
        private const val CLAVE_DIAS = "dias"
        private const val CLAVE_FECHA = "fecha_ultima"
        private const val CLAVE_RESULTADO = "resultado"
        private const val CLAVE_HUELLA = "huella_datos"
        private const val TRABAJO_PERIODICO = "copia-automatica"
        const val TRABAJO_AHORA = "copia-automatica-ahora"
    }
}
