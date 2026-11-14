package app.mimusica.domain.model

/** Origen de una pista. */
enum class TrackSource(val label: String) {
    LOCAL("En mi teléfono"),
    INTERNET_ARCHIVE("Internet Archive"),
    JAMENDO("Jamendo"),
    RADIO("Radio en vivo"),
    PODCAST("Podcast");

    companion object {
        fun fromName(name: String?): TrackSource =
            entries.firstOrNull { it.name == name } ?: LOCAL
    }
}

/**
 * Modelo de dominio único para cualquier pista (local u online).
 *
 * [key] sigue el formato:
 *  - `local:<mediaStoreId>`
 *  - `ia:<identifier>/<file>`
 *  - `jam:<id>`
 *  - `radio:<stationuuid>`
 *  - `pod:<hash del enlace del episodio>`
 */
data class Track(
    val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String?,
    val source: TrackSource,
    val license: String? = null,
    val downloadUrl: String? = null,
    val infoUrl: String? = null,
    val dateAddedSec: Long = 0L,
    val trackNumber: Int = 0,
    val albumId: Long = 0L,
    val genre: String = "",
) {
    val isOnline: Boolean get() = source != TrackSource.LOCAL
    /** Emisora en vivo: sin duración, sin barra de progreso y sin descarga. */
    val isLive: Boolean get() = source == TrackSource.RADIO
    val isPodcast: Boolean get() = source == TrackSource.PODCAST
    val canDownload: Boolean get() = isOnline && !isLive && !downloadUrl.isNullOrBlank()

    companion object {
        const val UNKNOWN_ARTIST = "Artista desconocido"
        const val UNKNOWN_ALBUM = "Álbum desconocido"

        fun localKey(mediaStoreId: Long) = "local:$mediaStoreId"
        fun internetArchiveKey(identifier: String, file: String) = "ia:$identifier/$file"
        fun jamendoKey(id: String) = "jam:$id"
        fun radioKey(uuid: String) = "radio:$uuid"
        fun podcastKey(hash: String) = "pod:$hash"

        fun sourceOfKey(key: String): TrackSource = when {
            key.startsWith("ia:") -> TrackSource.INTERNET_ARCHIVE
            key.startsWith("jam:") -> TrackSource.JAMENDO
            key.startsWith("radio:") -> TrackSource.RADIO
            key.startsWith("pod:") -> TrackSource.PODCAST
            else -> TrackSource.LOCAL
        }

        fun localIdOfKey(key: String): Long? =
            if (key.startsWith("local:")) key.removePrefix("local:").toLongOrNull() else null
    }
}

/** Artista agrupado a partir de la biblioteca local. */
data class ArtistSummary(val name: String, val trackCount: Int, val albumCount: Int, val artworkUri: String?)

/** Álbum agrupado a partir de la biblioteca local. */
data class AlbumSummary(
    val key: String,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val artworkUri: String?,
)
