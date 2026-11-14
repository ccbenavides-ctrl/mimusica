package app.mimusica.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("playlistId"), Index("trackKey")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackKey: String,
    val position: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackKey: String,
    val addedAt: Long,
)

@Entity(tableName = "history", indices = [Index("trackKey")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackKey: String,
    val playedAt: Long,
)

/** Metadatos de pistas referenciadas (locales u online) para listas, favoritos e historial. */
@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val trackKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String?,
    val source: String,
    val license: String?,
    val downloadUrl: String?,
    val infoUrl: String?,
) {
    fun toTrack() = Track(
        key = trackKey,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = uri,
        artworkUri = artworkUri,
        source = TrackSource.fromName(source),
        license = license,
        downloadUrl = downloadUrl,
        infoUrl = infoUrl,
    )

    companion object {
        fun from(t: Track) = CachedTrackEntity(
            trackKey = t.key,
            title = t.title,
            artist = t.artist,
            album = t.album,
            durationMs = t.durationMs,
            uri = t.uri,
            artworkUri = t.artworkUri,
            source = t.source.name,
            license = t.license,
            downloadUrl = t.downloadUrl,
            infoUrl = t.infoUrl,
        )
    }
}

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackKey: String,
    val status: String,
    val progress: Int,
    val localUri: String?,
    val sourceUrl: String,
    val title: String,
    val artist: String,
    val album: String,
    val createdAt: Long,
    val error: String? = null,
)

@Entity(tableName = "imported_playlists")
data class ImportedPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "imported_tracks",
    foreignKeys = [
        ForeignKey(
            entity = ImportedPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("playlistId")],
)
data class ImportedTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val position: Int,
    val title: String,
    val artist: String,
    val album: String,
    val matchedTrackKey: String?,
)

/** Resumen de una lista con su número de pistas. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
)

/** Pista de una lista con su posición. */
data class PlaylistTrackRow(
    val position: Int,
    @Embedded val track: CachedTrackEntity,
)

data class ImportedPlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val total: Int,
    val matched: Int,
)
