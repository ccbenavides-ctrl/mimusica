package app.mimusica.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import app.mimusica.AppContainer
import app.mimusica.MainActivity
import app.mimusica.MiMusicaApp
import app.mimusica.data.db.EpisodeProgressEntity
import app.mimusica.data.settings.AppSettings
import app.mimusica.domain.model.Track
import app.mimusica.widget.PlayerWidget
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow

/**
 * Servicio de reproducción en segundo plano: ExoPlayer + MediaLibrarySession.
 *
 * Además de la notificación, la pantalla de bloqueo y los botones Bluetooth (que gestiona Media3), aquí viven:
 * la caché de streaming, la cola que se recuerda entre sesiones, el temporizador de apagado, el fundido
 * entre canciones, la normalización de volumen, el ecualizador, el historial, el progreso de los podcasts
 * y el árbol de contenidos para Android Auto.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private lateinit var exo: ExoPlayer
    private lateinit var sessionPlayer: Player
    private lateinit var container: AppContainer
    private lateinit var queueStore: QueueStore
    private lateinit var browser: AutoBrowser

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var settings = AppSettings()

    // Historial: una reproducción cuenta cuando suena más de 10 s.
    private var listenedMs = 0L
    private var recordedCurrent = false

    // Volumen efectivo = normalización × fundido × temporizador.
    private var normFactor = 1f
    private var lastVolume = -1f

    private var saveJob: Job? = null
    private var ticks = 0

    override fun onCreate() {
        super.onCreate()
        container = (application as MiMusicaApp).container
        queueStore = QueueStore(this)
        browser = AutoBrowser(container)
        // Al arrancar solo se usan los ajustes de la copia rápida (sin bloquear el hilo principal);
        // el resto llega enseguida desde DataStore en applySettings().
        settings = container.settings.startupSettings()

        exo = buildExoPlayer()
        sessionPlayer = CastSupport.wrapIfEnabled(this, exo, settings.castEnabled).also {
            container.extras.castActive.value = it !== exo
        }
        container.extras.effects.attach(exo.audioSessionId)
        sessionPlayer.addListener(playerListener)
        exo.addListener(exoListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, sessionPlayer, LibraryCallback())
            .setSessionActivity(openApp)
            .build()

        restoreQueue()
        scope.launch { container.settings.settings.collect { applySettings(it) } }
        startTicker()
    }

    private fun buildExoPlayer(): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(MiMusicaApp.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val plain = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory))
        // La caché solo se usa para audio por internet que no sea en vivo.
        val cached: DefaultMediaSourceFactory? = if (settings.streamCacheMb > 0) {
            runCatching {
                val cache = PlaybackCache.get(this, settings.streamCacheMb * 1024L * 1024L)
                val cacheFactory: DataSource.Factory = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(httpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                DefaultMediaSourceFactory(cacheFactory)
            }.getOrNull()
        } else null

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(RoutingMediaSourceFactory(plain, cached))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private fun applySettings(s: AppSettings) {
        container.settings.mirrorStartupSettings(s)
        val speedChanged = s.playbackSpeed != settings.playbackSpeed || lastVolume < 0f
        settings = s
        container.extras.effects.configure(s.eqEnabled, s.eqPreset, s.bassBoost)
        if (!s.normalizeVolume) {
            normFactor = 1f
            container.extras.effects.setExtraGainMb(0)
        }
        if (speedChanged && abs(sessionPlayer.playbackParameters.speed - s.playbackSpeed) > 0.001f) {
            sessionPlayer.setPlaybackSpeed(s.playbackSpeed)
        }
    }

    // ---- Eventos del reproductor ----

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            listenedMs = 0L
            recordedCurrent = false
            container.extras.liveTitle.value = null
            if (mediaItem != null) resumePodcastIfNeeded(mediaItem)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                scheduleSave()
                updateWidget()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // "Pausar al terminar la canción" ya se cumplió.
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                exo.pauseAtEndOfMediaItems = false
                container.extras.sleepTimer.cancel()
            }
        }
    }

    private val exoListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            container.extras.effects.attach(audioSessionId)
        }

        override fun onTracksChanged(tracks: Tracks) {
            applyReplayGain(tracks)
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    val title = entry.title?.trim()
                    if (!title.isNullOrBlank()) container.extras.liveTitle.value = title
                }
            }
        }
    }

    /** Normalización de volumen con las etiquetas ReplayGain del archivo (si las trae). */
    private fun applyReplayGain(tracks: Tracks) {
        if (!settings.normalizeVolume) return
        var gainDb: Float? = null
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    ReplayGain.parseTrackGain(metadata.get(j).toString())?.let { gainDb = it }
                }
            }
        }
        val gain = gainDb
        if (gain == null) {
            normFactor = 1f
            container.extras.effects.setExtraGainMb(0)
        } else if (gain <= 0f) {
            normFactor = 10f.pow(gain / 20f).coerceIn(0.1f, 1f)
            container.extras.effects.setExtraGainMb(0)
        } else {
            normFactor = 1f
            container.extras.effects.setExtraGainMb((gain.coerceAtMost(6f) * 100).toInt())
        }
    }

    // ---- Tareas periódicas: historial, volumen, temporizador, guardado ----

    private fun startTicker() {
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                ticks++
                updateVolumeAndTimer()
                if (ticks % (1000 / TICK_MS).toInt() == 0) everySecond()
            }
        }
    }

    private fun updateVolumeAndTimer() {
        var factor = 1f
        val fadeMs = settings.fadeSec * 1000L
        if (fadeMs > 0 && exo.isPlaying && !exo.isCurrentMediaItemLive) {
            val duration = exo.duration
            val position = exo.currentPosition
            if (duration != C.TIME_UNSET && duration > fadeMs * 3) {
                val remaining = duration - position
                val fadeIn = fadeMs.coerceAtMost(1500L)
                if (remaining < fadeMs) factor = (remaining.toFloat() / fadeMs).coerceIn(0f, 1f)
                else if (position < fadeIn) factor = (position.toFloat() / fadeIn).coerceIn(0.2f, 1f)
            }
        }
        val sleep = container.extras.sleepTimer.state.value
        val endAt = sleep.endAtElapsedMs
        if (endAt != null) {
            val remaining = sleep.remainingMs()
            if (remaining <= 0L) {
                sessionPlayer.pause()
                container.extras.sleepTimer.cancel()
            } else if (remaining < SLEEP_FADE_MS) {
                factor *= (remaining.toFloat() / SLEEP_FADE_MS).coerceIn(0.05f, 1f)
            }
        }
        if (exo.pauseAtEndOfMediaItems != sleep.endOfTrack) exo.pauseAtEndOfMediaItems = sleep.endOfTrack

        val volume = (normFactor * factor).coerceIn(0f, 1f)
        if (abs(volume - lastVolume) > 0.01f) {
            exo.volume = volume
            lastVolume = volume
        }
    }

    private fun everySecond() {
        val player = sessionPlayer
        if (!player.isPlaying) return
        listenedMs += 1_000
        val item = player.currentMediaItem ?: return
        if (!recordedCurrent && listenedMs > 10_000) {
            recordedCurrent = true
            val track = MediaItemMapper.toTrack(item)
            container.appScope.launch { container.library.recordPlay(track) }
        }
        val seconds = ticks / (1000 / TICK_MS).toInt()
        if (seconds % 5 == 0 && item.mediaId.startsWith("pod:")) savePodcastProgress(item.mediaId)
        if (seconds % 15 == 0) scheduleSave()
    }

    // ---- Podcasts: retomar donde quedó ----

    private fun savePodcastProgress(key: String) {
        val position = sessionPlayer.currentPosition
        val duration = sessionPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        container.appScope.launch {
            container.database.podcastDao().saveProgress(EpisodeProgressEntity(key, position, duration, System.currentTimeMillis()))
        }
    }

    private fun resumePodcastIfNeeded(item: MediaItem) {
        val key = item.mediaId
        if (!key.startsWith("pod:")) return
        scope.launch {
            val saved = container.database.podcastDao().progress(key) ?: return@launch
            val nearEnd = saved.durationMs > 0 && saved.positionMs > saved.durationMs - 30_000
            val player = sessionPlayer
            if (saved.positionMs > 15_000 && !nearEnd && player.currentMediaItem?.mediaId == key && player.currentPosition < 5_000) {
                player.seekTo(saved.positionMs)
            }
        }
    }

    // ---- Cola guardada ----

    private fun snapshotQueue(): SavedQueue {
        val player = sessionPlayer
        val items = (0 until player.mediaItemCount).map { i ->
            val item = player.getMediaItemAt(i)
            MediaItemMapper.toTrack(item) to MediaItemMapper.playbackUri(item)
        }
        return SavedQueue(
            items = items,
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = if (player.isCurrentMediaItemLive) 0L else player.currentPosition.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(800)
            val snapshot = snapshotQueue()
            withContext(Dispatchers.IO) { queueStore.save(snapshot) }
        }
    }

    private fun restoreQueue() {
        val saved = queueStore.load() ?: return
        val items = saved.items.map { (track, playUri) -> MediaItemMapper.toMediaItem(track, playUri) }
        sessionPlayer.shuffleModeEnabled = saved.shuffle
        sessionPlayer.repeatMode = saved.repeatMode
        // No se llama a prepare(): la música se carga cuando el usuario pulsa reproducir.
        sessionPlayer.setMediaItems(items, saved.index, saved.positionMs)
        updateWidget()
    }

    private fun updateWidget() {
        val item = sessionPlayer.currentMediaItem
        PlayerWidget.update(
            context = this,
            title = item?.mediaMetadata?.title?.toString(),
            artist = item?.mediaMetadata?.artist?.toString(),
            isPlaying = sessionPlayer.isPlaying,
        )
    }

    // ---- Ciclo de vida ----

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Último guardado, síncrono: el proceso puede morir enseguida.
        runCatching { queueStore.save(snapshotQueue()) }
        runCatching { PlayerWidget.update(this, sessionPlayer.currentMediaItem?.mediaMetadata?.title?.toString(), sessionPlayer.currentMediaItem?.mediaMetadata?.artist?.toString(), false) }
        scope.cancel()
        container.extras.effects.release()
        container.extras.liveTitle.value = null
        session?.release()
        session = null
        runCatching { sessionPlayer.release() }
        if (sessionPlayer !== exo) runCatching { exo.release() }
        super.onDestroy()
    }

    // ---- Sesión: controladores, Android Auto y reanudación ----

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(AutoBrowser.rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val all = this@PlaybackService.browser.children(parentId)
            val from = (page * pageSize).coerceIn(0, all.size)
            val to = (from + pageSize).coerceIn(from, all.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            val item = this@PlaybackService.browser.item(mediaId)
            if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        /** Los MediaItem que llegan sin URI (p. ej. desde Android Auto) se resuelven por su clave. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = future {
            mediaItems.map { item -> resolve(item) }.toMutableList()
        }

        /** Al elegir una canción en Android Auto se pone en cola toda la lista de la que salió. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val single = mediaItems.singleOrNull()
            if (single != null && single.localConfiguration == null && single.requestMetadata.mediaUri == null) {
                val list = browser.lastTrackList
                val index = list.indexOfFirst { it.key == single.mediaId }
                if (index >= 0) {
                    val items = list.map { toPlayable(it) }
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, index, C.TIME_UNSET))
                }
            }
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }

        /** Reanudar desde Bluetooth o el botón de reproducir del sistema cuando la app estaba cerrada. */
        @Deprecated("Media3 mantiene esta variante; se sobrescribe para la reanudación")
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val saved = withContext(Dispatchers.IO) { queueStore.load() }
                ?: throw UnsupportedOperationException("No hay nada que reanudar")
            MediaSession.MediaItemsWithStartPosition(
                saved.items.map { (track, playUri) -> MediaItemMapper.toMediaItem(track, playUri) },
                saved.index,
                saved.positionMs,
            )
        }

        private suspend fun resolve(item: MediaItem): MediaItem {
            if (item.localConfiguration != null) return item
            val uri = item.requestMetadata.mediaUri
            if (uri != null) return item.buildUpon().setUri(uri).build()
            val track = browser.findTrack(item.mediaId) ?: return item
            return toPlayable(track)
        }

        private fun toPlayable(track: Track): MediaItem =
            MediaItemMapper.toMediaItem(track, container.downloads.resolveForPlayback(track).uri)
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Throwable) {
                result.setException(e)
            }
        }
        return result
    }

    companion object {
        private const val TICK_MS = 200L
        private const val SLEEP_FADE_MS = 10_000L
    }
}

/** Elige la fuente con caché para audio por internet (no en vivo) y la normal para lo demás. */
@OptIn(UnstableApi::class)
private class RoutingMediaSourceFactory(
    private val plain: DefaultMediaSourceFactory,
    private val cached: DefaultMediaSourceFactory?,
) : MediaSource.Factory {

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        plain.setDrmSessionManagerProvider(drmSessionManagerProvider)
        cached?.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        plain.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        cached?.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = plain.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri: Uri? = mediaItem.localConfiguration?.uri
        val scheme = uri?.scheme?.lowercase()
        val isHttp = scheme == "http" || scheme == "https"
        val isLive = mediaItem.mediaId.startsWith("radio:")
        val isHls = uri?.path?.endsWith(".m3u8", ignoreCase = true) == true
        val factory = if (cached != null && isHttp && !isLive && !isHls) cached else plain
        return factory.createMediaSource(mediaItem)
    }
}

/** Lee la ganancia ReplayGain de pista a partir del texto de una entrada de metadatos (ID3 TXXX o Vorbis). */
object ReplayGain {
    private val regex = Regex("""replaygain_track_gain\D{0,24}?([+-]?\d+(?:[.,]\d+)?)\s*dB""", RegexOption.IGNORE_CASE)

    fun parseTrackGain(entryText: String): Float? =
        regex.find(entryText)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()?.takeIf { it in -30f..30f }
}
