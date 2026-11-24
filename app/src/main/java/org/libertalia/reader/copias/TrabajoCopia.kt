package org.libertalia.reader.copias

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.libertalia.reader.LibertaliaApp
import org.libertalia.reader.MainActivity
import org.libertalia.reader.R
import org.libertalia.reader.racha.MedidorLectura

/**
 * El trabajo en segundo plano de la copia automática (lo lanza WorkManager).
 * Nunca se hace mientras se está leyendo: en ese caso se deja para un poco más tarde.
 */
class TrabajoCopia(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        if (MedidorLectura.leyendo) return Result.retry()
        val app = applicationContext as LibertaliaApp
        val forzar = inputData.getBoolean(FORZAR, false)
        val resultado = CopiaAutomatica(app).hacer(forzar) { p -> setProgressAsync(workDataOf(PROGRESO to p)) }
        return when (resultado) {
            ResultadoCopia.CORRECTO, ResultadoCopia.SIN_CAMBIOS, ResultadoCopia.SIN_DESTINO -> {
                AvisoCopia.quitar(app)
                Result.success()
            }
            // Un fallo pasajero (la nube no responde...): se reintenta un par de veces antes de avisar.
            ResultadoCopia.ERROR -> if (runAttemptCount < 2 && !forzar) {
                Result.retry()
            } else {
                AvisoCopia.mostrar(app, resultado)
                Result.success()
            }
            // Sin permiso o sin archivo: reintentar no sirve; hay que avisar.
            ResultadoCopia.SIN_PERMISO, ResultadoCopia.NO_EXISTE -> {
                AvisoCopia.mostrar(app, resultado)
                Result.success()
            }
        }
    }

    companion object {
        const val FORZAR = "forzar"
        const val PROGRESO = "progreso"
    }
}

/**
 * La notificación de la copia automática: solo cuando falla (decisión del usuario), nunca
 * cuando va bien. Al tocarla se abre la pantalla de la copia automática.
 */
object AvisoCopia {

    private const val CANAL = "copias"
    private const val ID = 10

    fun mostrar(contexto: Context, resultado: ResultadoCopia) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // Sin permiso: el aviso se verá dentro de la app.
        }
        val gestor = contexto.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && gestor.getNotificationChannel(CANAL) == null) {
            gestor.createNotificationChannel(
                NotificationChannel(CANAL, contexto.getString(R.string.copia_canal), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val abrir = PendingIntent.getActivity(
            contexto, 0,
            Intent(contexto, MainActivity::class.java).putExtra(MainActivity.EXTRA_ABRIR_COPIA_AUTOMATICA, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_nube)
            .setContentTitle(contexto.getString(R.string.copia_fallo_titulo))
            .setContentText(contexto.getString(textoDe(resultado)))
            .setStyle(NotificationCompat.BigTextStyle().bigText(contexto.getString(textoDe(resultado))))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(contexto).notify(ID, notificacion) }
    }

    fun quitar(contexto: Context) {
        NotificationManagerCompat.from(contexto).cancel(ID)
    }

    /** Por qué ha fallado, en palabras sencillas. */
    fun textoDe(resultado: ResultadoCopia): Int = when (resultado) {
        ResultadoCopia.SIN_PERMISO -> R.string.copia_fallo_permiso
        ResultadoCopia.NO_EXISTE -> R.string.copia_fallo_no_existe
        else -> R.string.copia_fallo_error
    }
}
