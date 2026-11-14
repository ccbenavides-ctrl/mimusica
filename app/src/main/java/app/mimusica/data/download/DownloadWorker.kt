package app.mimusica.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.mimusica.MiMusicaApp
import app.mimusica.data.db.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/**
 * Descarga una pista con licencia libre (Internet Archive / Jamendo) y la guarda en Music/MiMusica.
 */
class DownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val container get() = (applicationContext as MiMusicaApp).container
    private val notificationId: Int get() = (inputData.getString(KEY_TRACK) ?: id.toString()).hashCode()

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_TRACK) ?: return Result.failure()
        val dao = container.database.downloadDao()
        val entity = dao.get(key) ?: return Result.failure()
        ensureChannel(applicationContext)
        trySetForeground(entity.title, 0, indeterminate = true)
        dao.updateStatus(key, DownloadStatus.RUNNING, 0, null)

        var target: DownloadTarget? = null
        return try {
            val localUri = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(entity.sourceUrl)
                    .header("User-Agent", MiMusicaApp.USER_AGENT)
                    .build()
                container.okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("El servidor respondió ${response.code}")
                    val body = response.body
                    val total = body.contentLength()
                    val ext = guessExtension(entity.sourceUrl, response.header("Content-Type"))
                    target = MediaStoreWriter.create(applicationContext, entity.title, entity.artist, entity.album, ext, podcast = key.startsWith("pod:"))
                    MediaStoreWriter.openOutput(applicationContext, target!!).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var done = 0L
                            var lastPercent = -1
                            while (input.read(buffer).also { read = it } != -1) {
                                if (isStopped) throw CancellationException("Cancelada")
                                out.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                                    if (percent >= lastPercent + 2) {
                                        lastPercent = percent
                                        setProgress(workDataOf(KEY_PROGRESS to percent))
                                        dao.updateStatus(key, DownloadStatus.RUNNING, percent, null)
                                        notifyProgress(entity.title, percent)
                                    }
                                }
                            }
                        }
                    }
                }
                MediaStoreWriter.finish(applicationContext, target!!)
            }
            dao.markCompleted(key, DownloadStatus.COMPLETED, localUri)
            notifyDone(entity.title, success = true)
            container.localMusic.scheduleRefresh()
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                MediaStoreWriter.discard(applicationContext, target)
                dao.updateStatus(key, DownloadStatus.CANCELLED, 0, "Cancelada")
            }
            NotificationManagerCompat.from(applicationContext).cancel(notificationId)
            throw e
        } catch (e: Exception) {
            MediaStoreWriter.discard(applicationContext, target)
            val message = when (e) {
                is SecurityException -> "Sin permiso para guardar en la carpeta Música."
                is IOException -> "Error de red: ${e.message ?: "sin conexión"}"
                else -> e.message ?: e.javaClass.simpleName
            }
            dao.updateStatus(key, DownloadStatus.FAILED, 0, message)
            notifyDone(entity.title, success = false)
            Result.failure()
        }
    }

    private fun guessExtension(url: String, contentType: String?): String {
        val path = url.substringBefore('?').lowercase()
        listOf("mp3", "ogg", "flac", "m4a", "wav").firstOrNull { path.endsWith(".$it") }?.let { return it }
        val ct = contentType.orEmpty().lowercase()
        return when {
            ct.contains("ogg") -> "ogg"
            ct.contains("flac") -> "flac"
            ct.contains("mp4") || ct.contains("aac") -> "m4a"
            ct.contains("wav") -> "wav"
            else -> "mp3"
        }
    }

    private fun buildNotification(title: String, percent: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Descargando")
            .setContentText(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, percent, indeterminate)
            .build()

    private suspend fun trySetForeground(title: String, percent: Int, indeterminate: Boolean) {
        val notification = buildNotification(title, percent, indeterminate)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
        try {
            setForeground(info)
        } catch (_: Exception) {
            // Android puede impedir servicios en primer plano si la app está en segundo plano;
            // la descarga continúa igualmente como trabajo normal.
        }
    }

    private fun notifyProgress(title: String, percent: Int) {
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(notificationId, buildNotification(title, percent, indeterminate = false))
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: se ignora.
        }
    }

    private fun notifyDone(title: String, success: Boolean) {
        try {
            val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (success) "Descarga completada" else "La descarga falló")
                .setContentText(title)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(notificationId + 1, n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val KEY_TRACK = "trackKey"
        const val KEY_PROGRESS = "progress"
        const val CHANNEL_ID = "descargas"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "Descargas", NotificationManager.IMPORTANCE_LOW).apply {
                            description = "Progreso de las descargas de música"
                        }
                    )
                }
            }
        }
    }
}
