package org.libertalia.reader

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.libertalia.reader.biblioteca.CarpetaLibertalia
import org.libertalia.reader.biblioteca.Importador
import org.libertalia.reader.copias.CopiaAutomatica
import org.libertalia.reader.datos.BaseDatos
import org.libertalia.reader.datos.Preferencias
import org.libertalia.reader.lector.ServicioLectura

/**
 * La "aplicación" en sí: se crea una sola vez al arrancar
 * y guarda las piezas que comparten todas las pantallas.
 */
class LibertaliaApp : Application() {

    lateinit var baseDatos: BaseDatos
        private set
    lateinit var preferencias: Preferencias
        private set
    lateinit var carpeta: CarpetaLibertalia
        private set
    lateinit var servicioLectura: ServicioLectura
        private set
    lateinit var importador: Importador
        private set

    /** La copia automática (un único archivo que se sobrescribe cada pocos días). */
    val copiaAutomatica by lazy { CopiaAutomatica(this) }

    /** El vínculo con la bóveda de Obsidian (si el usuario la ha elegido en Ajustes). */
    val obsidian by lazy { org.libertalia.reader.obsidian.Obsidian(this) }

    /**
     * Obsidian al momento: 3 segundos después del último cambio en las tarjetas (subrayar,
     * nota, etiqueta, color, unir, borrar) se pone al día la bóveda, aunque se siga leyendo.
     * Solo se reescriben los archivos que han cambiado.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun vigilarTarjetasParaObsidian() {
        alcanceApp.launch {
            baseDatos.tarjetas().observarTodosLosSubrayados()
                .drop(1) // la primera lista es la de al arrancar: no es un cambio
                .debounce(3_000)
                .collect { if (obsidian.boveda != null) obsidian.sincronizar() }
        }
    }

    /** Pide a Obsidian que lea lo cambiado allí y escriba lo cambiado aquí (sin esperar). */
    fun sincronizarObsidian() {
        if (obsidian.boveda != null) alcanceApp.launch { obsidian.sincronizar() }
    }

    /**
     * Para trabajos que deben terminar aunque se cierre la pantalla que los empezó
     * (por ejemplo, guardar la posición al salir del lector).
     */
    val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        baseDatos = BaseDatos.crear(this)
        preferencias = Preferencias(this)
        org.libertalia.reader.ui.ajustes.AjustesApp.cargar(this)
        carpeta = CarpetaLibertalia(this, preferencias)
        servicioLectura = ServicioLectura(this)
        importador = Importador(this, baseDatos.libros(), carpeta, servicioLectura)
        // Restos de un libro leído con «Solo leer» si la app se cerró sin poder borrarlos.
        org.libertalia.reader.biblioteca.LibroDePaso.limpiarRestos(this)
        // Que la copia automática esté programada (si ya lo estaba, no se toca).
        copiaAutomatica.programar()
        vigilarTarjetasParaObsidian()
        // Obsidian: al volver a la app (por si se cambió algo allí) y al dejarla (para escribir
        // lo cambiado aquí).
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var visibles = 0
            override fun onActivityStarted(activity: android.app.Activity) {
                if (visibles++ == 0) sincronizarObsidian()
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                if (--visibles == 0) sincronizarObsidian()
            }
            override fun onActivityCreated(activity: android.app.Activity, estado: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, estado: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }
}
