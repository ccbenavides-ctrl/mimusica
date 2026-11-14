package app.mimusica.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.mimusica.MainActivity
import app.mimusica.MiMusicaApp
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio de reproducción en segundo plano: ExoPlayer + MediaSession.
 * Media3 se encarga de la notificación multimedia, la pantalla de bloqueo y los botones Bluetooth.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var historyJob: Job? = null

    // Registro de historial: una reproducción cuenta cuando suena más de 10 s.
    private var listenedMs = 0L
    private var recordedCurrent = false

    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(MiMusicaApp.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                listenedMs = 0L
                recordedCurrent = false
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        startHistoryTracking(player)
    }

    private fun startHistoryTracking(player: Player) {
        historyJob = scope.launch {
            while (isActive) {
                delay(1_000)
                if (player.isPlaying) {
                    listenedMs += 1_000
                    if (!recordedCurrent && listenedMs > 10_000) {
                        recordedCurrent = true
                        val item = player.currentMediaItem ?: continue
                        val track = MediaItemMapper.toTrack(item)
                        val container = (application as MiMusicaApp).container
                        container.appScope.launch { container.library.recordPlay(track) }
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        historyJob?.cancel()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Los MediaItem que llegan desde el MediaController se reconstruyen con su URI. */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri
                if (item.localConfiguration == null && uri != null) item.buildUpon().setUri(uri).build() else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
