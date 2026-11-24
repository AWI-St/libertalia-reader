package org.libertalia.reader.depuracion

import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.libertalia.reader.R
import org.libertalia.reader.lector.modotexto.ExtractorPdf
import org.libertalia.reader.lector.modotexto.PaginaPdf
import org.libertalia.reader.lector.modotexto.VolcadoLineas
import java.io.File

/**
 * Solo en la versión de pruebas (no va en la app que se instala en el móvil).
 * Saca las líneas de todos los PDF de la carpeta de la app y las guarda en
 * files/volcados/, para probar la limpieza del modo texto en el ordenador.
 *
 * Se abre con: adb shell am start -n org.libertalia.reader/.depuracion.VolcarPdfActivity
 * Para volcar solo los PDF cuyo nombre contiene un texto: añadir --es filtro <texto>.
 */
class VolcarPdfActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val estado = TextView(this).apply { setPadding(48, 160, 48, 48) }
        setContentView(estado)
        estado.text = getString(R.string.depuracion_volcando)
        // En un hilo aparte que sigue aunque se cierre esta pantalla.
        val app = applicationContext
        val filtro = intent.getStringExtra("filtro")
        Thread {
            Log.i("VolcarPdf", "Empezando")
            val hechos = runCatching { volcarTodos(app, filtro) }.onFailure { Log.e("VolcarPdf", "Error", it) }.getOrDefault(-1)
            runOnUiThread { estado.text = getString(R.string.depuracion_volcado_listo, hechos) }
        }.start()
    }

    private fun volcarTodos(contexto: android.content.Context, filtro: String?): Int {
        PDFBoxResourceLoader.init(contexto)
        val contentResolver = contexto.contentResolver
        val salida = File(contexto.filesDir, "volcados").apply { deleteRecursively(); mkdirs() }
        val arbol = contentResolver.persistedUriPermissions
            .map { it.uri }
            .firstOrNull { DocumentsContract.isTreeUri(it) } ?: return 0
        val carpeta = DocumentFile.fromTreeUri(contexto, arbol) ?: return 0
        var hechos = 0
        carpeta.listFiles()
            .filter { it.name?.endsWith(".pdf", ignoreCase = true) == true }
            .filter { filtro == null || it.name!!.contains(filtro, ignoreCase = true) }
            .forEach { archivo ->
                runCatching {
                    contentResolver.openInputStream(archivo.uri)!!.use { entrada ->
                        PDDocument.load(entrada, MemoryUsageSetting.setupTempFileOnly()).use { documento ->
                            val extractor = ExtractorPdf()
                            val paginas = (1..documento.numberOfPages).map { n ->
                                runCatching { extractor.leerPagina(documento, n) }.getOrDefault(PaginaPdf(n, 0f, emptyList()))
                            }
                            File(salida, archivo.name!! + ".txt").bufferedWriter().use { VolcadoLineas.escribir(paginas, it) }
                        }
                    }
                    hechos++
                    Log.i("VolcarPdf", "Hecho: ${archivo.name}")
                }.onFailure { Log.w("VolcarPdf", "Error en ${archivo.name}", it) }
            }
        File(salida, "LISTO").writeText(hechos.toString())
        return hechos
    }
}
