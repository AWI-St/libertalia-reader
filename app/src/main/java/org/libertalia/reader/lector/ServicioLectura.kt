package org.libertalia.reader.lector

import android.content.Context
import android.net.Uri
import org.libertalia.reader.voz.FabricaSinNotas
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.ContentService
import org.readium.r2.shared.publication.services.content.DefaultContentService
import org.readium.r2.shared.publication.services.content.iterators.HtmlResourceContentIterator
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/**
 * Abre archivos de libros (EPUB y PDF) con Readium y los convierte en una "publicación",
 * que es lo que después muestra el lector.
 */
class ServicioLectura(contexto: Context) {

    private val clienteHttp = DefaultHttpClient()
    private val buscadorArchivos = AssetRetriever(contexto.contentResolver, clienteHttp)
    private val abridor = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = contexto,
            httpClient = clienteHttp,
            assetRetriever = buscadorArchivos,
            pdfFactory = PdfiumDocumentFactory(contexto),
        ),
        contentProtections = emptyList(),
        // La lectura en voz alta recorre el texto con el «servicio de contenido» del libro:
        // se cambia por uno que se salta las notas al pie del modo texto.
        onCreatePublication = {
            if (servicesBuilder.get(ContentService::class) != null) {
                servicesBuilder.set(
                    ContentService::class,
                    DefaultContentService.createFactory(listOf(FabricaSinNotas(HtmlResourceContentIterator.Factory()))),
                )
            }
        },
    )

    /** Abre un archivo de la memoria de la app. */
    suspend fun abrir(archivo: File): Result<Publication> =
        abrir(archivo.toUrl(isDirectory = false))

    /** Abre un archivo de la carpeta de la app (dirección "content://..."). */
    suspend fun abrir(uri: Uri): Result<Publication> {
        val url = uri.toAbsoluteUrl()
            ?: return Result.failure(Exception("Dirección no válida"))
        return abrir(url)
    }

    private suspend fun abrir(url: AbsoluteUrl): Result<Publication> {
        // Si la app ya no tiene permiso sobre el archivo, Readium lanza una excepción en lugar
        // de devolver un error: se captura para que la app no se cierre.
        val recurso = runCatching { buscadorArchivos.retrieve(url) }
            .getOrElse { return Result.failure(it) }
            .getOrElse { return Result.failure(Exception(it.message)) }
        val publicacion = abridor.open(recurso, allowUserInteraction = false)
            .getOrElse {
                recurso.close()
                return Result.failure(Exception(it.message))
            }
        return Result.success(publicacion)
    }
}
