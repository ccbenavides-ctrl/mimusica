package app.mimusica.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource

/** Conversión entre el modelo de dominio [Track] y [MediaItem] de Media3. */
object MediaItemMapper {
    private const val EXTRA_SOURCE = "mimusica.source"
    private const val EXTRA_DURATION = "mimusica.duration"
    private const val EXTRA_LICENSE = "mimusica.license"
    private const val EXTRA_DOWNLOAD = "mimusica.download"
    private const val EXTRA_INFO = "mimusica.info"
    private const val EXTRA_URI = "mimusica.uri"
    private const val EXTRA_ORIGINAL_URI = "mimusica.originalUri"

    /**
     * [playbackUri] permite reproducir la copia descargada manteniendo la referencia online original.
     */
    fun toMediaItem(track: Track, playbackUri: String = track.uri): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_SOURCE, track.source.name)
            putLong(EXTRA_DURATION, track.durationMs)
            putString(EXTRA_LICENSE, track.license)
            putString(EXTRA_DOWNLOAD, track.downloadUrl)
            putString(EXTRA_INFO, track.infoUrl)
            putString(EXTRA_URI, playbackUri)
            putString(EXTRA_ORIGINAL_URI, track.uri)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUri?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()
        val uri = Uri.parse(playbackUri)
        return MediaItem.Builder()
            .setMediaId(track.key)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .build()
    }

    /** URI real con la que se reproduce el elemento (puede ser la copia descargada). */
    fun playbackUri(item: MediaItem): String =
        item.mediaMetadata.extras?.getString(EXTRA_URI)
            ?: item.localConfiguration?.uri?.toString()
            ?: item.requestMetadata.mediaUri?.toString()
            ?: ""

    fun toTrack(item: MediaItem): Track {
        val m = item.mediaMetadata
        val extras = m.extras
        val playbackUri = extras?.getString(EXTRA_URI)
            ?: item.localConfiguration?.uri?.toString()
            ?: item.requestMetadata.mediaUri?.toString()
            ?: ""
        return Track(
            key = item.mediaId,
            title = m.title?.toString() ?: "Sin título",
            artist = m.artist?.toString() ?: Track.UNKNOWN_ARTIST,
            album = m.albumTitle?.toString() ?: "",
            durationMs = extras?.getLong(EXTRA_DURATION) ?: 0L,
            uri = extras?.getString(EXTRA_ORIGINAL_URI) ?: playbackUri,
            artworkUri = m.artworkUri?.toString(),
            source = TrackSource.fromName(extras?.getString(EXTRA_SOURCE) ?: Track.sourceOfKey(item.mediaId).name),
            license = extras?.getString(EXTRA_LICENSE),
            downloadUrl = extras?.getString(EXTRA_DOWNLOAD),
            infoUrl = extras?.getString(EXTRA_INFO),
        )
    }
}
