package org.libertalia.reader.ui.biblioteca

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libertalia.reader.biblioteca.Portadas
import org.libertalia.reader.datos.Formato
import org.libertalia.reader.datos.Libro
import org.libertalia.reader.datos.TipoLibro
import kotlin.math.absoluteValue

/** Colores de tapa para los libros sin portada, como los del boceto de la biblioteca. */
private val coloresTapa = listOf(
    Color(0xFF7A2E26), // granate
    Color(0xFF2F4A3C), // verde
    Color(0xFF2E3D5C), // azul
    Color(0xFF8B5A2B), // ocre
    Color(0xFF4E3B5C), // morado
)

/** Siempre el mismo color para el mismo libro. */
fun colorTapa(libro: Libro): Color = coloresTapa[libro.id.hashCode().absoluteValue % coloresTapa.size]

/**
 * Portada del libro. Si no tiene, dibuja una tapa de color con el título,
 * como un libro de verdad en la estantería.
 */
@Composable
fun PortadaLibro(
    libro: Libro,
    modifier: Modifier = Modifier,
    mostrarTitulo: Boolean = true,
    forma: androidx.compose.ui.graphics.Shape =
        RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp, topStart = 2.dp, bottomStart = 2.dp),
) {
    val contexto = LocalContext.current
    // La imagen se lee del disco fuera de la pantalla para no frenarla.
    val imagen: ImageBitmap? by produceState<ImageBitmap?>(null, libro.id, libro.fechaModificacion) {
        value = if (!libro.tienePortada) null else withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(Portadas.archivo(contexto, libro.id).path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.clip(forma).background(colorTapa(libro))) {
        val portada = imagen
        if (portada != null) {
            Image(
                bitmap = portada,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // En los PDF la portada es la primera página: se le pone el mismo filtro
                // cálido que en el lector para que no se vea blanco brillante.
                colorFilter = if (libro.formato == Formato.PDF && libro.tipo != TipoLibro.COMIC) {
                    ColorFilter.tint(Color(0xFFF8F1E3), BlendMode.Multiply)
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (mostrarTitulo) {
            TapaDeColor(libro)
        }
        // Lomo: una franja más oscura a la izquierda (los cómics, de frente, no lo tienen).
        if (libro.tipo != TipoLibro.COMIC) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(Color.Black.copy(alpha = 0.18f))
            )
        }
    }
}

/**
 * Tapa dibujada: título arriba y autor abajo, en crema sobre el color del libro.
 * La letra se ajusta al tamaño de la tapa (más pequeña en la lista, más grande en el estante).
 */
@Composable
private fun TapaDeColor(libro: Libro) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 7.dp, end = 5.dp, top = 8.dp, bottom = 8.dp)) {
        val grande = maxWidth >= 70.dp
        val crema = Color(0xFFF3EADB)
        Text(
            text = libro.titulo,
            color = crema,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (grande) 13.sp else 9.sp,
            lineHeight = if (grande) 16.sp else 11.sp,
            textAlign = TextAlign.Center,
            maxLines = if (grande) 4 else 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(if (grande) Alignment.TopCenter else Alignment.Center).fillMaxWidth(),
        )
        if (grande && libro.autor.isNotBlank()) {
            Text(
                text = libro.autor.substringAfterLast(' ').ifBlank { libro.autor },
                color = crema.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}
