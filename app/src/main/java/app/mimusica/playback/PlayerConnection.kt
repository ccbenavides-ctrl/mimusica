package app.mimusica.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.mimusica.domain.model.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatSetting(val label: String) { OFF("Repetir: no"), ALL("Repetir: todas"), ONE("Repetir: una") }

data class PlayerState(
    val connected: Boolean = false,
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatSetting = RepeatSetting.OFF,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val durationMs: Long = 0L,
    val error: String? = null,
) {
    val hasMedia: Boolean get() = current != null
}

/**
 * Lado "cliente" del reproductor: un [MediaController] conectado a [PlaybackService].
 * Todas las llamadas deben hacerse en el hilo principal (como las hace la UI).
 */
class PlayerConnection(
    private val context: Context,
    private val resolveUri: (Track) -> String,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            update(rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = describe(error))
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try {
                future.get()
            } catch (e: Exception) {
                controllerFuture = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            _state.value = _state.value.copy(connected = true)
            update(rebuildQueue = true)
            val actions = pending.toList()
            pending.clear()
            actions.forEach { it(c) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else {
            pending.add(action)
            connect()
        }
    }

    // ---- Comandos ----

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val items = tracks.map { MediaItemMapper.toMediaItem(it, resolveUri(it)) }
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle) tracks.indices.random() else startIndex.coerceIn(0, tracks.lastIndex)
            c.setMediaItems(items, start, 0L)
            c.prepare()
            c.play()
            _state.value = _state.value.copy(error = null)
        }
    }

    fun playNext(track: Track) = withController { c ->
        val item = MediaItemMapper.toMediaItem(track, resolveUri(track))
        if (c.mediaItemCount == 0) {
            c.setMediaItem(item); c.prepare(); c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, item)
        }
    }

    fun addToQueue(tracks: List<Track>) = withController { c ->
        val items = tracks.map { MediaItemMapper.toMediaItem(it, resolveUri(it)) }
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItems(items)
        if (wasEmpty) { c.prepare(); c.play() }
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun removeQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun clearQueue() = withController { c -> c.stop(); c.clearMediaItems() }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Posición actual (para la barra de progreso). */
    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    fun bufferedPositionMs(): Long = controller?.bufferedPosition ?: 0L

    // ---- Estado ----

    private fun update(rebuildQueue: Boolean) {
        val c = controller ?: return
        val queue = if (rebuildQueue) {
            (0 until c.mediaItemCount).map { MediaItemMapper.toTrack(c.getMediaItemAt(it)) }
        } else _state.value.queue
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        _state.value = _state.value.copy(
            connected = true,
            current = c.currentMediaItem?.let { MediaItemMapper.toTrack(it) },
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            shuffle = c.shuffleModeEnabled,
            repeat = when (c.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatSetting.ALL
                Player.REPEAT_MODE_ONE -> RepeatSetting.ONE
                else -> RepeatSetting.OFF
            },
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
            durationMs = duration,
            error = if (c.playerError == null) null else _state.value.error,
        )
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Sin conexión: no se pudo cargar la canción."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "El servidor no entregó la canción (puede que ya no esté disponible)."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "No se encontró el archivo (¿se borró del teléfono?)."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "Sin permiso para leer el archivo."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "Formato de audio no compatible."
        else -> "No se pudo reproducir: ${error.errorCodeName}"
    }
}
