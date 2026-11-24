package org.libertalia.reader.voz

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.libertalia.reader.lector.LectorActivity

/**
 * Mantiene viva la lectura en voz alta con la pantalla apagada o en otra app. Android lo
 * exige con una notificación (con pausa, frase anterior y siguiente) mientras suena, también
 * en la pantalla de bloqueo. Al tocar la notificación se vuelve al lector.
 */
class ServicioVoz : MediaSessionService() {

    private var sesion: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val jugador = LecturaEnVoz.jugador()
        if (jugador == null) {
            stopSelf()
            return
        }
        val volverAlLector = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LectorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        sesion = MediaSession.Builder(this, jugador).setSessionActivity(volverAlLector).build()
        LecturaEnVoz.servicio = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sesion

    /** Si se quita la app de las recientes, se deja de leer. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        LecturaEnVoz.parar()
        stopSelf()
    }

    /** Al parar la lectura: fuera la notificación y el servicio. */
    fun cerrar() {
        sesion?.let {
            removeSession(it)
            it.release()
        }
        sesion = null
        LecturaEnVoz.servicio = null
        stopSelf()
    }

    override fun onDestroy() {
        sesion?.release()
        sesion = null
        if (LecturaEnVoz.servicio === this) LecturaEnVoz.servicio = null
        super.onDestroy()
    }
}
