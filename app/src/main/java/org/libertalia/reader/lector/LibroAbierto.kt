package org.libertalia.reader.lector

import org.libertalia.reader.datos.Libro
import org.libertalia.reader.lector.modotexto.MapaTexto
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Guarda el libro que está abierto en este momento, para pasarlo
 * de la pantalla que lo abre a la pantalla del lector.
 */
object LibroAbierto {

    var publicacion: Publication? = null
        private set

    /** Los datos del libro en la biblioteca. */
    var libro: Libro? = null
        private set

    /** Última posición conocida (para no perderla al girar el móvil). */
    var ultimaPosicion: Locator? = null

    /** Un PDF que se está leyendo en modo texto (la publicación es su EPUB interno). */
    var modoTexto: Boolean = false
        private set

    /** Correspondencia entre las páginas del PDF y su modo texto (si ya se ha creado). */
    var mapaTexto: MapaTexto? = null
        private set

    fun abrir(nueva: Publication, datos: Libro, posicion: Locator?, enModoTexto: Boolean = false, mapa: MapaTexto? = null) {
        cerrar()
        publicacion = nueva
        libro = datos
        ultimaPosicion = posicion
        modoTexto = enModoTexto
        mapaTexto = mapa
    }

    /**
     * Pasa del PDF a su modo texto o al revés. La publicación anterior no se cierra aquí:
     * la cierra el lector que la estaba mostrando, al desaparecer.
     */
    fun cambiarModo(nueva: Publication, posicion: Locator?, enModoTexto: Boolean, mapa: MapaTexto?) {
        publicacion = nueva
        ultimaPosicion = posicion
        modoTexto = enModoTexto
        mapaTexto = mapa
    }

    /** Cierra el libro y libera la memoria que usaba. */
    fun cerrar() {
        publicacion?.close()
        publicacion = null
        libro = null
        ultimaPosicion = null
        modoTexto = false
        mapaTexto = null
    }
}
