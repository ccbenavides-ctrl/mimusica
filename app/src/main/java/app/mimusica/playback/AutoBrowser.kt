package app.mimusica.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.mimusica.AppContainer
import app.mimusica.data.local.LocalMusicRepository
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Árbol de contenidos para Android Auto (y otros navegadores de medios):
 * recientes, favoritos, listas, álbumes, artistas, canciones y emisoras favoritas.
 */
class AutoBrowser(private val container: AppContainer) {

    /** Última lista de canciones servida: al elegir una, se pone en cola la lista completa. */
    @Volatile
    var lastTrackList: List<Track> = emptyList()
        private set

    private suspend fun library(): List<Track> {
        // Si la app acaba de arrancar, espera (poco) a que termine el primer escaneo.
        withTimeoutOrNull(4_000) { container.localMusic.state.first { it.loadedOnce || !it.hasPermission } }
        return container.localMusic.state.value.tracks
    }

    suspend fun children(parentId: String): List<MediaItem> {
        val db = container.database
        return when {
            parentId == ROOT -> listOf(
                folder(RECENT, "Escuchado recientemente"),
                folder(FAVORITES, "Favoritos"),
                folder(PLAYLISTS, "Listas"),
                folder(ALBUMS, "Álbumes"),
                folder(ARTISTS, "Artistas"),
                folder(SONGS, "Canciones"),
                folder(RADIO, "Mis emisoras"),
            )
            parentId == RECENT -> tracks(db.historyDao().getRecent(MAX_ITEMS).map { it.toTrack() })
            parentId == FAVORITES -> tracks(
                db.favoriteDao().getTracks().map { it.toTrack() }.filter { it.source != TrackSource.RADIO }.take(MAX_ITEMS)
            )
            parentId == RADIO -> tracks(db.favoriteDao().getTracks().map { it.toTrack() }.filter { it.source == TrackSource.RADIO })
            parentId == PLAYLISTS -> db.playlistDao().getAll().map { folder(PLAYLIST_PREFIX + it.id, it.name) }
            parentId.startsWith(PLAYLIST_PREFIX) -> {
                val id = parentId.removePrefix(PLAYLIST_PREFIX).toLongOrNull() ?: return emptyList()
                tracks(db.playlistDao().getTracks(id).map { it.track.toTrack() }.take(MAX_ITEMS))
            }
            parentId == ALBUMS -> LocalMusicRepository.albums(library()).take(MAX_ITEMS).map {
                folder(ALBUM_PREFIX + it.key, it.title, it.artist, it.artworkUri, MediaMetadata.MEDIA_TYPE_ALBUM)
            }
            parentId.startsWith(ALBUM_PREFIX) ->
                tracks(LocalMusicRepository.tracksOfAlbum(library(), parentId.removePrefix(ALBUM_PREFIX)))
            parentId == ARTISTS -> LocalMusicRepository.artists(library()).take(MAX_ITEMS).map {
                folder(ARTIST_PREFIX + it.name, it.name, "${it.trackCount} canciones", it.artworkUri, MediaMetadata.MEDIA_TYPE_ARTIST)
            }
            parentId.startsWith(ARTIST_PREFIX) -> {
                val name = parentId.removePrefix(ARTIST_PREFIX)
                tracks(library().filter { it.artist == name }.sortedBy { it.title.lowercase() })
            }
            parentId == SONGS -> tracks(library().take(MAX_ITEMS))
            else -> emptyList()
        }
    }

    suspend fun item(mediaId: String): MediaItem? = when {
        mediaId == ROOT -> rootItem()
        mediaId.startsWith("nodo:") -> folder(mediaId, "S")
        else -> findTrack(mediaId)?.let { MediaItemMapper.toMediaItem(it) }
    }

    suspend fun findTrack(key: String): Track? =
        lastTrackList.firstOrNull { it.key == key }
            ?: library().firstOrNull { it.key == key }
            ?: container.database.cachedTrackDao().get(key)?.toTrack()

    private fun tracks(list: List<Track>): List<MediaItem> {
        lastTrackList = list
        return list.map { MediaItemMapper.toMediaItem(it) }
    }

    companion object {
        const val ROOT = "nodo:raiz"
        private const val RECENT = "nodo:recientes"
        private const val FAVORITES = "nodo:favoritos"
        private const val PLAYLISTS = "nodo:listas"
        private const val ALBUMS = "nodo:albumes"
        private const val ARTISTS = "nodo:artistas"
        private const val SONGS = "nodo:canciones"
        private const val RADIO = "nodo:emisoras"
        private const val PLAYLIST_PREFIX = "nodo:lista:"
        private const val ALBUM_PREFIX = "nodo:album:"
        private const val ARTIST_PREFIX = "nodo:artista:"
        private const val MAX_ITEMS = 300

        fun rootItem(): MediaItem = folder(ROOT, "S")

        private fun folder(
            id: String,
            title: String,
            subtitle: String? = null,
            artworkUri: String? = null,
            mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        ): MediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setArtworkUri(artworkUri?.let { Uri.parse(it) })
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }
}
