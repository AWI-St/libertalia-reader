package org.libertalia.reader.comic

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.libertalia.reader.ui.tema.TemaLibertalia

/**
 * Lector de cómics y manga (CBZ, CBR y PDF de cómic), con fondo oscuro como en el boceto 13.
 * Las páginas se leen directamente del archivo original.
 */
class LectorComicActivity : AppCompatActivity() {

    private val estado: LectorComicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras del sistema oscuras, como el fondo del lector.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        estado.cargar(intent.getStringExtra(EXTRA_ID) ?: run { finish(); return })
        if (estado.ajustes.pantallaEncendida) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        estado.ajustes.brillo?.let { brillo ->
            window.attributes = window.attributes.apply { screenBrightness = brillo }
        }
        setContent {
            TemaLibertalia {
                // En horizontal, si no se ha elegido modo, se pasa a doble página.
                estado.horizontal = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                // Sin barras de la app, tampoco las del sistema: toda la pantalla para la página.
                LaunchedEffect(estado.barrasVisibles) { mostrarBarrasDelSistema(estado.barrasVisibles) }
                PantallaComic(estado = estado, alVolver = ::finish)
            }
        }
    }

    private fun mostrarBarrasDelSistema(visibles: Boolean) {
        val control = WindowCompat.getInsetsController(window, window.decorView)
        control.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visibles) control.show(WindowInsetsCompat.Type.systemBars()) else control.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Botones de volumen: bajar = página siguiente, subir = anterior (si está activado en Ajustes). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Solo en la versión de pruebas: teclas para simular un doble toque desde adb
        // (Z = centro, X = arriba, C = abajo).
        val depurable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (depurable && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_Z -> { estado.simularDobleToque(0.5f, 0.5f); return true }
                KeyEvent.KEYCODE_X -> { estado.simularDobleToque(0.5f, 0.1f); return true }
                KeyEvent.KEYCODE_C -> { estado.simularDobleToque(0.5f, 0.9f); return true }
            }
        }
        val esVolumen = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (esVolumen && estado.ajustes.pasarConVolumen && estado.total > 0) {
            if (event.action == KeyEvent.ACTION_DOWN) estado.pasar(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // Con «Solo leer» no se cuenta el tiempo (ni racha ni estadísticas).
        if (!intent.getStringExtra(EXTRA_ID).let(org.libertalia.reader.biblioteca.LibroDePaso::esDePaso)) estado.medidor.empezar()
    }

    override fun onPause() {
        estado.medidor.parar()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        estado.guardar()
    }

    override fun onDestroy() {
        super.onDestroy()
        // «Solo leer»: al cerrar no queda nada (ni su copia ni sus preferencias).
        val id = intent.getStringExtra(EXTRA_ID)
        if (isFinishing && org.libertalia.reader.biblioteca.LibroDePaso.esDePaso(id)) {
            org.libertalia.reader.biblioteca.LibroDePaso.limpiar(this, id!!)
        }
    }

    companion object {
        private const val EXTRA_ID = "id_libro"

        fun abrir(contexto: Context, idLibro: String) {
            contexto.startActivity(Intent(contexto, LectorComicActivity::class.java).putExtra(EXTRA_ID, idLibro))
        }
    }
}
