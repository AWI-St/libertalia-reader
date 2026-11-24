package org.libertalia.reader.copias

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import org.libertalia.reader.LibertaliaApp

/** Pantalla «Copia automática» (en Ajustes). */
class CopiaAutomaticaViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val app = aplicacion as LibertaliaApp
    private val copia = app.copiaAutomatica

    var info by mutableStateOf(copia.info())
        private set

    /** Peso aproximado de la copia (null mientras se calcula). */
    var peso by mutableStateOf<Long?>(null)
        private set

    /** De 0 a 1 mientras se hace «Hacer copia ahora»; null si no se está haciendo. */
    var progreso by mutableStateOf<Float?>(null)
        private set

    init {
        actualizar()
        // Se sigue el trabajo de «Hacer copia ahora» (aunque se haya lanzado antes de abrir la pantalla).
        viewModelScope.launch {
            WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(CopiaAutomatica.TRABAJO_AHORA).collect { lista ->
                val trabajo = lista.firstOrNull()
                val enMarcha = trabajo?.state == WorkInfo.State.RUNNING || trabajo?.state == WorkInfo.State.ENQUEUED
                val antes = progreso
                progreso = if (enMarcha) trabajo?.progress?.getFloat(TrabajoCopia.PROGRESO, 0f) ?: 0f else null
                // Al terminar, se vuelve a leer cómo ha ido.
                if (antes != null && progreso == null) actualizar()
            }
        }
    }

    fun actualizar() {
        info = copia.info()
        viewModelScope.launch { peso = copia.pesoAproximado() }
    }

    fun elegirDestino(uri: Uri) {
        copia.elegirDestino(uri)
        info = copia.info()
    }

    fun cambiarFrecuencia(dias: Int) {
        copia.cambiarFrecuencia(dias)
        info = copia.info()
    }

    fun hacerAhora() {
        progreso = 0f
        copia.hacerAhoraEnSegundoPlano()
    }
}
