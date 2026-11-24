package org.libertalia.reader.voz

import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.PublicationServicesHolder
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.iterators.ResourceContentIteratorFactory
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.resource.Resource

/**
 * La lectura en voz alta recorre el texto del libro trozo a trozo (párrafos, títulos...).
 * Este filtro se salta los trozos que están dentro de las notas al pie del modo texto
 * (el recuadro `<div class="notas">` que crea el GeneradorEpub), para leer solo el texto
 * principal. En los demás libros no cambia nada.
 */
class FabricaSinNotas(private val base: ResourceContentIteratorFactory) : ResourceContentIteratorFactory {
    override suspend fun create(
        manifest: Manifest,
        servicesHolder: PublicationServicesHolder,
        readingOrderIndex: Int,
        resource: Resource,
        mediaType: MediaType,
        locator: Locator,
    ): Content.Iterator? =
        base.create(manifest, servicesHolder, readingOrderIndex, resource, mediaType, locator)?.let(::IteradorSinNotas)
}

private class IteradorSinNotas(private val base: Content.Iterator) : Content.Iterator {

    private var siguiente: Content.Element? = null
    private var anterior: Content.Element? = null

    override suspend fun hasNext(): Boolean {
        siguiente = null
        while (base.hasNext()) {
            val elemento = base.next()
            if (!esNota(elemento)) {
                siguiente = elemento
                return true
            }
        }
        return false
    }

    override fun next(): Content.Element =
        siguiente?.also { siguiente = null } ?: throw IllegalStateException("Antes hay que llamar a hasNext()")

    override suspend fun hasPrevious(): Boolean {
        anterior = null
        while (base.hasPrevious()) {
            val elemento = base.previous()
            if (!esNota(elemento)) {
                anterior = elemento
                return true
            }
        }
        return false
    }

    override fun previous(): Content.Element =
        anterior?.also { anterior = null } ?: throw IllegalStateException("Antes hay que llamar a hasPrevious()")

    /** Readium apunta dónde está cada trozo con un selector CSS («… > div.notas > p:nth-child(2)»). */
    private fun esNota(elemento: Content.Element): Boolean =
        (elemento.locator.locations.otherLocations["cssSelector"] as? String)?.contains("div.notas") == true
}
