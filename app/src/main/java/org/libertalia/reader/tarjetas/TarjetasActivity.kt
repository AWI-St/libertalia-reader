package org.libertalia.reader.tarjetas

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.libertalia.reader.R
import org.libertalia.reader.datos.Tarjeta
import org.libertalia.reader.ui.tema.TemaLibertalia

/**
 * Las tarjetas de un libro y la tarjeta abierta. Se entra desde el lector, desde el aviso
 * que sale al subrayar o desde el menú del libro en la biblioteca; volver regresa allí.
 * «Ir al libro» devuelve la tarjeta elegida a quien abrió esta pantalla, que lleva al
 * lector justo a ese punto.
 */
class TarjetasActivity : AppCompatActivity() {

    private val estado: TarjetasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val fondo = ContextCompat.getColor(this, R.color.fondo_app)
        // Iconos de las barras del sistema claros u oscuros según el aspecto de la app.
        val estiloBarras = if (org.libertalia.reader.ui.ajustes.AjustesApp.esOscuro(this)) SystemBarStyle.dark(fondo)
        else SystemBarStyle.light(fondo, fondo)
        enableEdgeToEdge(statusBarStyle = estiloBarras, navigationBarStyle = estiloBarras)
        super.onCreate(savedInstanceState)
        val idLibro = intent.getStringExtra(EXTRA_LIBRO) ?: run { finish(); return }
        val idTarjeta = intent.getStringExtra(EXTRA_TARJETA)
        estado.cargar(idLibro)

        setContent {
            TemaLibertalia {
                val navegacion = rememberNavController()
                NavHost(navController = navegacion, startDestination = RUTA_LISTA) {
                    composable(RUTA_LISTA) {
                        PantallaTarjetas(
                            estado = estado,
                            alVolver = ::finish,
                            alAbrir = { navegacion.navigate("$RUTA_TARJETA/${it.id}") },
                            alEtiqueta = { navegacion.navigate("$RUTA_ETIQUETA/${Uri.encode(it)}") },
                        )
                    }
                    // Todas las tarjetas (de todos los libros) con una etiqueta.
                    composable("$RUTA_ETIQUETA/{nombre}") { entrada ->
                        val etiqueta = Uri.decode(entrada.arguments?.getString("nombre").orEmpty())
                        PantallaEtiqueta(
                            etiqueta = etiqueta,
                            libros = estado.libros,
                            tarjetas = estado.todasLasTarjetas,
                            alVolver = { if (!navegacion.popBackStack()) finish() },
                            alAbrir = { tarjeta ->
                                // Una tarjeta de este libro se abre aquí; la de otro libro, en su propia pantalla.
                                if (tarjeta.idLibro == idLibro) {
                                    navegacion.navigate("$RUTA_TARJETA/${tarjeta.id}")
                                } else {
                                    abrirOtroLibro.launch(Abrir.Entrada(tarjeta.idLibro, tarjeta.id))
                                }
                            },
                            alEtiqueta = { navegacion.navigate("$RUTA_ETIQUETA/${Uri.encode(it)}") },
                        )
                    }
                    composable("$RUTA_TARJETA/{id}") { entrada ->
                        PantallaTarjeta(
                            estado = estado,
                            idInicial = entrada.arguments?.getString("id").orEmpty(),
                            alVolver = { if (!navegacion.popBackStack()) finish() },
                            alIrAlLibro = ::irAlLibro,
                            alCompartir = ::compartir,
                            alEtiqueta = { navegacion.navigate("$RUTA_ETIQUETA/${Uri.encode(it)}") },
                        )
                    }
                }
                // Abierta para ver una tarjeta concreta (más adelante: la tarjeta del día).
                if (idTarjeta != null && savedInstanceState == null) {
                    androidx.compose.runtime.LaunchedEffect(Unit) { navegacion.navigate("$RUTA_TARJETA/$idTarjeta") }
                }
            }
        }
    }

    /**
     * Las tarjetas de otro libro (desde la pantalla de una etiqueta). Si allí se pulsa
     * «Ir al libro», se pasa el aviso hacia atrás, a quien abrió esta pantalla.
     */
    private val abrirOtroLibro = registerForActivityResult(Abrir()) { destino ->
        destino ?: return@registerForActivityResult
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_TARJETA, destino.idTarjeta).putExtra(EXTRA_LIBRO, destino.idLibro),
        )
        finish()
    }

    private fun irAlLibro(tarjeta: Tarjeta) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TARJETA, tarjeta.id).putExtra(EXTRA_LIBRO, tarjeta.idLibro))
        finish()
    }

    /** Comparte la cita con el título y el autor del libro (la nota nunca se comparte). */
    private fun compartir(tarjeta: Tarjeta) {
        val libro = estado.libro
        val autoria = listOfNotNull(libro?.titulo, libro?.autor?.takeIf { it.isNotBlank() }).joinToString(", ")
        val texto = if (autoria.isEmpty()) "«${tarjeta.texto}»" else getString(R.string.tarjeta_compartir_texto, tarjeta.texto, autoria)
        val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
        runCatching { startActivity(Intent.createChooser(envio, getString(R.string.cd_compartir_tarjeta))) }
    }

    /** Para abrir esta pantalla y recibir la tarjeta a la que hay que ir (o null si solo se ha vuelto). */
    class Abrir : ActivityResultContract<Abrir.Entrada, Abrir.Destino?>() {
        data class Entrada(val idLibro: String, val idTarjeta: String? = null)
        data class Destino(val idLibro: String, val idTarjeta: String)

        override fun createIntent(context: Context, input: Entrada): Intent =
            Intent(context, TarjetasActivity::class.java)
                .putExtra(EXTRA_LIBRO, input.idLibro)
                .putExtra(EXTRA_TARJETA, input.idTarjeta)

        override fun parseResult(resultCode: Int, intent: Intent?): Destino? {
            if (resultCode != Activity.RESULT_OK || intent == null) return null
            val libro = intent.getStringExtra(EXTRA_LIBRO) ?: return null
            val tarjeta = intent.getStringExtra(EXTRA_TARJETA) ?: return null
            return Destino(libro, tarjeta)
        }
    }

    private companion object {
        const val EXTRA_LIBRO = "id_libro"
        const val EXTRA_TARJETA = "id_tarjeta"
        const val RUTA_LISTA = "lista"
        const val RUTA_TARJETA = "tarjeta"
        const val RUTA_ETIQUETA = "etiqueta"
    }
}
