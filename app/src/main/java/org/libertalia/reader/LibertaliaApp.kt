package org.libertalia.reader

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    }
}
