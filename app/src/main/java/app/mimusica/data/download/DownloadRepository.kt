package app.mimusica.data.download

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.mimusica.data.db.AppDatabase
import app.mimusica.data.db.DownloadEntity
import app.mimusica.data.db.DownloadStatus
import app.mimusica.data.library.LibraryRepository
import app.mimusica.data.settings.SettingsRepository
import app.mimusica.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Gestiona la cola de descargas (WorkManager) y su estado (Room). */
class DownloadRepository(
    private val context: Context,
    db: AppDatabase,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    private val dao = db.downloadDao()
    private val workManager get() = WorkManager.getInstance(context)

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    /** Estado de descarga por trackKey. */
    val byKey: StateFlow<Map<String, DownloadEntity>> = dao.observeAll()
        .map { list -> list.associateBy { it.trackKey } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Si la pista online ya está descargada, devuelve la copia local para reproducirla. */
    fun resolveForPlayback(track: Track): Track {
        if (!track.isOnline) return track
        val d = byKey.value[track.key] ?: return track
        val local = d.localUri
        return if (d.status == DownloadStatus.COMPLETED && !local.isNullOrBlank()) track.copy(uri = local) else track
    }

    suspend fun enqueue(track: Track): Boolean {
        val url = track.downloadUrl
        if (!track.isOnline || url.isNullOrBlank()) return false
        val existing = dao.get(track.key)
        if (existing != null && existing.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.COMPLETED)) {
            return false
        }
        library.cache(track)
        dao.upsert(
            DownloadEntity(
                trackKey = track.key,
                status = DownloadStatus.QUEUED,
                progress = 0,
                localUri = null,
                sourceUrl = url,
                title = track.title,
                artist = track.artist,
                album = track.album,
                createdAt = System.currentTimeMillis(),
            )
        )
        schedule(track.key)
        return true
    }

    private suspend fun schedule(key: String) {
        val wifiOnly = settings.settings.first().wifiOnlyDownloads
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_TRACK to key))
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(key), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun retry(key: String) {
        val d = dao.get(key) ?: return
        dao.updateStatus(key, DownloadStatus.QUEUED, 0, null)
        if (d.sourceUrl.isNotBlank()) schedule(key)
    }

    suspend fun cancel(key: String) {
        workManager.cancelUniqueWork(workName(key))
        dao.updateStatus(key, DownloadStatus.CANCELLED, 0, "Cancelada")
    }

    /** Quita la descarga de la lista; si [deleteFile] es true intenta borrar también el archivo. */
    suspend fun remove(key: String, deleteFile: Boolean): Boolean {
        val d = dao.get(key)
        workManager.cancelUniqueWork(workName(key))
        var deleted = true
        if (deleteFile && d?.localUri != null) {
            deleted = try {
                context.contentResolver.delete(Uri.parse(d.localUri), null, null) > 0
            } catch (_: Exception) {
                false
            }
        }
        dao.delete(key)
        return deleted
    }

    companion object {
        const val TAG = "descarga"
        fun workName(key: String) = "dl:$key"
    }
}
