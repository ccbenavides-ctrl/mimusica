package app.mimusica

import android.content.Context
import app.mimusica.data.db.AppDatabase
import app.mimusica.data.download.DownloadRepository
import app.mimusica.data.library.LibraryRepository
import app.mimusica.data.local.LocalMusicRepository
import app.mimusica.data.remote.OnlineRepository
import app.mimusica.data.settings.SettingsRepository
import app.mimusica.domain.model.Track
import app.mimusica.data.backup.BackupManager
import app.mimusica.data.remote.ArtworkRepository
import app.mimusica.data.remote.LyricsRepository
import app.mimusica.data.remote.PodcastRepository
import app.mimusica.data.remote.RadioRepository
import app.mimusica.domain.mix.MixBuilder
import app.mimusica.playback.PlaybackExtras
import app.mimusica.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** Inyección de dependencias manual (sin Hilt). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", MiMusicaApp.USER_AGENT).build())
        }
        .build()

    val database: AppDatabase = AppDatabase.create(appContext)
    val settings = SettingsRepository(appContext)
    val localMusic = LocalMusicRepository(appContext, settings, appScope, database.overrideDao())
    val library = LibraryRepository(appContext, database, appScope)
    val downloads = DownloadRepository(appContext, database, library, settings, appScope)

    val online = OnlineRepository(
        retrofitIa = Retrofit.Builder().baseUrl("https://archive.org/").client(okHttpClient).build(),
        retrofitJamendo = Retrofit.Builder().baseUrl("https://api.jamendo.com/").client(okHttpClient).build(),
        settings = settings,
    )

    /** Estado compartido con el servicio de reproducción (temporizador, ecualizador, título en vivo). */
    val extras = PlaybackExtras()

    val player = PlayerConnection(appContext, extras) { track -> downloads.resolveForPlayback(track).uri }

    val lyrics = LyricsRepository(okHttpClient, database)
    val radio = RadioRepository(okHttpClient)
    val podcasts = PodcastRepository(okHttpClient, database)
    val artwork = ArtworkRepository(okHttpClient, database)
    val backup = BackupManager(appContext, database, library, localMusic)

    /** Acciones comunes sobre una pista (menú ⋮), compartidas por todas las pantallas. */
    val trackActions = TrackActions(this)
}

/** Acciones de pista reutilizables desde cualquier pantalla. */
class TrackActions(private val c: AppContainer) {
    fun play(tracks: List<Track>, index: Int = 0) = c.player.playTracks(tracks, index)
    fun shuffle(tracks: List<Track>) = c.player.playTracks(tracks, shuffle = true)
    fun playNext(track: Track) = c.player.playNext(track)
    fun addToQueue(track: Track) = c.player.addToQueue(listOf(track))

    /** "Radio de esta canción": cola con canciones parecidas de la biblioteca. */
    fun songRadio(seed: Track) {
        val queue = MixBuilder.songRadio(seed, c.localMusic.state.value.tracks, c.library.favoriteKeys.value)
        c.player.playTracks(queue, 0)
    }

    fun toggleFavorite(track: Track) {
        c.appScope.launch { c.library.toggleFavorite(track) }
    }

    fun addToPlaylist(playlistId: Long, tracks: List<Track>) {
        c.appScope.launch { c.library.addToPlaylist(playlistId, tracks) }
    }

    fun createPlaylistWith(name: String, tracks: List<Track>) {
        c.appScope.launch {
            val id = c.library.createPlaylist(name)
            c.library.addToPlaylist(id, tracks)
        }
    }

    fun download(track: Track, onResult: (Boolean) -> Unit = {}) {
        c.appScope.launch { onResult(c.downloads.enqueue(track)) }
    }
}
