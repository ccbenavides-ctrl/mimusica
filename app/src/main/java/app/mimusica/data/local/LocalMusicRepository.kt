package app.mimusica.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import app.mimusica.data.settings.SettingsRepository
import app.mimusica.domain.model.AlbumSummary
import app.mimusica.domain.model.ArtistSummary
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Estado del escaneo de la biblioteca local. */
data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val hasPermission: Boolean = false,
    val isScanning: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
)

/**
 * Lee toda la música del teléfono vía MediaStore (incluida la carpeta Music/MiMusica de las descargas).
 * Se refresca manualmente y automáticamente cuando MediaStore cambia (ContentObserver).
 */
class LocalMusicRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LibraryState(hasPermission = hasAudioPermission(context)))
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val scanMutex = Mutex()
    private var observerRegistered = false
    private var pendingRefresh: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduleRefresh()
        }
    }

    init {
        // Si cambia la duración mínima en Ajustes, se vuelve a escanear.
        scope.launch {
            settings.settings.map { it.minTrackDurationSec }.distinctUntilChanged().drop(1).collect {
                refresh()
            }
        }
    }

    /** Llamar cuando se concede (o se revisa) el permiso de audio. */
    fun onPermissionChanged() {
        val granted = hasAudioPermission(context)
        _state.value = _state.value.copy(hasPermission = granted)
        if (granted) {
            registerObserver()
            scope.launch { refresh() }
        }
    }

    private fun registerObserver() {
        if (observerRegistered) return
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        observerRegistered = true
    }

    fun scheduleRefresh() {
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            delay(1500)
            refresh()
        }
    }

    suspend fun refresh() {
        if (!hasAudioPermission(context)) {
            _state.value = _state.value.copy(hasPermission = false, isScanning = false)
            return
        }
        scanMutex.withLock {
            _state.value = _state.value.copy(hasPermission = true, isScanning = true, error = null)
            try {
                val minSec = settings.settings.first().minTrackDurationSec
                val tracks = withContext(Dispatchers.IO) { scan(minSec * 1000L) }
                _state.value = LibraryState(tracks = tracks, hasPermission = true, isScanning = false, loadedOnce = true)
            } catch (e: SecurityException) {
                _state.value = _state.value.copy(hasPermission = false, isScanning = false, loadedOnce = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isScanning = false,
                    loadedOnce = true,
                    error = "No se pudo leer la música del teléfono: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    private fun scan(minDurationMs: Long): List<Track> {
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(minDurationMs.toString())
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val result = mutableListOf<Track>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val displayName = c.getString(nameCol).orEmpty()
                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.').ifBlank { "Sin título" }
                val artist = c.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: Track.UNKNOWN_ARTIST
                val album = c.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: Track.UNKNOWN_ALBUM
                // TRACK puede venir como 1004 (disco 1, pista 4).
                val trackNumber = c.getInt(trackCol) % 1000
                result.add(
                    Track(
                        key = Track.localKey(id),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = c.getLong(durationCol),
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        artworkUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId).toString(),
                        source = TrackSource.LOCAL,
                        dateAddedSec = c.getLong(dateCol),
                        trackNumber = trackNumber,
                        albumId = albumId,
                    )
                )
            }
        }
        return result
    }

    companion object {
        private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")

        fun audioPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        fun hasAudioPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

        fun albumKey(track: Track): String = "${track.album}|${track.artist}"

        fun artists(tracks: List<Track>): List<ArtistSummary> =
            tracks.groupBy { it.artist }
                .map { (name, list) ->
                    ArtistSummary(
                        name = name,
                        trackCount = list.size,
                        albumCount = list.map { it.album }.distinct().size,
                        artworkUri = list.firstOrNull()?.artworkUri,
                    )
                }
                .sortedBy { it.name.lowercase() }

        fun albums(tracks: List<Track>): List<AlbumSummary> =
            tracks.groupBy { if (it.albumId != 0L) it.albumId.toString() else albumKey(it) }
                .map { (key, list) ->
                    val first = list.first()
                    val artists = list.map { it.artist }.distinct()
                    AlbumSummary(
                        key = key,
                        title = first.album,
                        artist = if (artists.size == 1) artists.first() else "Varios artistas",
                        trackCount = list.size,
                        artworkUri = first.artworkUri,
                    )
                }
                .sortedBy { it.title.lowercase() }

        fun tracksOfAlbum(tracks: List<Track>, albumKey: String): List<Track> =
            tracks.filter { (if (it.albumId != 0L) it.albumId.toString() else albumKey(it)) == albumKey }
                .sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
    }
}
