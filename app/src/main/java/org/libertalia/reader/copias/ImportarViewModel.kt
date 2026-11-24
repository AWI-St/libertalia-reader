package org.libertalia.reader.copias

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libertalia.reader.biblioteca.ArchivosExternos
import org.libertalia.reader.LibertaliaApp

/** En qué punto está la importación de una copia. */
sealed interface EstadoImportar {
    /** Copiando y leyendo el archivo (de 0 a 1). */
    data class Leyendo(val progreso: Float) : EstadoImportar

    /** No se puede importar. */
    data class Fallo(val motivo: ErrorCopia.Motivo) : EstadoImportar

    /** Leída: se enseña el resumen y se espera a que el usuario confirme. */
    data class Resumen(val analisis: AnalisisCopia) : EstadoImportar

    /** Uniendo la copia con la biblioteca (de 0 a 1). */
    data class Aplicando(val progreso: Float) : EstadoImportar

    /**
     * Terminada. [copiaBorrable]: el archivo .libertalia está en el móvil (no en una nube ni es
     * el de la copia automática) y se puede ofrecer borrarlo.
     */
    data class Hecho(val analisis: AnalisisCopia, val copiaBorrable: Boolean, val nombreCopia: String) : EstadoImportar
}

/** Pantalla «Importar una copia»: leer, resumir, y unir con la biblioteca al confirmar. */
class ImportarViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as LibertaliaApp
    private val importador = ImportadorCopias(app)

    var estado by mutableStateOf<EstadoImportar>(EstadoImportar.Leyendo(0f))
        private set

    private var copia: CopiaLeida? = null
    private var uriLeida: Uri? = null

    fun iniciar(uri: Uri) {
        if (uriLeida == uri) return
        uriLeida = uri
        viewModelScope.launch {
            try {
                var ultimo = 0f
                val leida = importador.leer(uri) { p ->
                    if (p - ultimo >= 0.01f) {
                        ultimo = p
                        estado = EstadoImportar.Leyendo(p)
                    }
                }
                copia = leida
                estado = EstadoImportar.Resumen(importador.analizar(leida))
            } catch (e: ErrorCopia) {
                estado = EstadoImportar.Fallo(e.motivo)
            }
        }
    }

    /** «Importar»: se une con la biblioteca. Sigue aunque se salga de la pantalla. */
    fun importar() {
        val leida = copia ?: return
        if (estado !is EstadoImportar.Resumen) return
        copia = null
        estado = EstadoImportar.Aplicando(0f)
        app.alcanceApp.launch {
            try {
                var ultimo = 0f
                val hecho = importador.aplicar(leida) { p ->
                    if (p - ultimo >= 0.01f || p >= 1f) {
                        ultimo = p
                        estado = EstadoImportar.Aplicando(p)
                    }
                }
                val uri = uriLeida
                val borrable = uri != null && uri.toString() != uriCopiaAutomatica() &&
                    ArchivosExternos.sePuedeBorrar(app, uri, app.carpeta)
                estado = EstadoImportar.Hecho(hecho, borrable, uri?.let { ArchivosExternos.nombre(app, it) }.orEmpty())
            } catch (e: Exception) {
                estado = EstadoImportar.Fallo(ErrorCopia.Motivo.ILEGIBLE)
            } finally {
                importador.descartar(leida)
            }
        }
    }

    /** null mientras no se ha intentado; true/false: si se ha podido borrar el archivo de copia. */
    var copiaBorrada by mutableStateOf<Boolean?>(null)
        private set

    /** Borra el archivo .libertalia que se ha importado (solo se ofrece si está en el móvil). */
    fun borrarCopia() {
        val uri = uriLeida ?: return
        viewModelScope.launch {
            copiaBorrada = withContext(Dispatchers.IO) { ArchivosExternos.borrar(app, uri) }
        }
    }

    /** El archivo de la copia automática nunca se ofrece borrar. */
    private fun uriCopiaAutomatica(): String? = app.copiaAutomatica.info().uri?.toString()

    override fun onCleared() {
        // Si se sale sin importar, se borra el archivo temporal.
        importador.descartar(copia)
        copia = null
    }
}
