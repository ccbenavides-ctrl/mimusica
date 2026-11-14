package app.mimusica.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTrackDao {
    @Upsert
    suspend fun upsert(track: CachedTrackEntity)

    @Upsert
    suspend fun upsertAll(tracks: List<CachedTrackEntity>)

    @Query("SELECT * FROM cached_tracks WHERE trackKey = :key")
    suspend fun get(key: String): CachedTrackEntity?

    @Query("SELECT * FROM cached_tracks")
    suspend fun getAll(): List<CachedTrackEntity>
}

@Dao
abstract class PlaylistDao {
    @Query(
        """SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
           (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount
           FROM playlists p ORDER BY p.name COLLATE NOCASE"""
    )
    abstract fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun observe(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    abstract suspend fun getAll(): List<PlaylistEntity>

    @Query(
        """SELECT pt.position AS position, c.* FROM playlist_tracks pt
           INNER JOIN cached_tracks c ON c.trackKey = pt.trackKey
           WHERE pt.playlistId = :playlistId ORDER BY pt.position"""
    )
    abstract suspend fun getTracks(playlistId: Long): List<PlaylistTrackRow>

    @Insert
    abstract suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query(
        """SELECT pt.position AS position, c.* FROM playlist_tracks pt
           INNER JOIN cached_tracks c ON c.trackKey = pt.trackKey
           WHERE pt.playlistId = :playlistId ORDER BY pt.position"""
    )
    abstract fun observeTracks(playlistId: Long): Flow<List<PlaylistTrackRow>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    abstract suspend fun getEntries(playlistId: Long): List<PlaylistTrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEntries(entries: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun clearEntries(playlistId: Long)

    @Transaction
    open suspend fun replaceEntries(playlistId: Long, keys: List<String>) {
        clearEntries(playlistId)
        insertEntries(keys.mapIndexed { i, k -> PlaylistTrackEntity(playlistId, k, i) })
    }

    @Transaction
    open suspend fun appendEntries(playlistId: Long, keys: List<String>) {
        val start = maxPosition(playlistId) + 1
        insertEntries(keys.mapIndexed { i, k -> PlaylistTrackEntity(playlistId, k, start + i) })
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT trackKey FROM favorites")
    fun observeKeys(): Flow<List<String>>

    @Query(
        """SELECT c.* FROM favorites f INNER JOIN cached_tracks c ON c.trackKey = f.trackKey
           ORDER BY f.addedAt DESC"""
    )
    fun observeTracks(): Flow<List<CachedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>

    @Query(
        """SELECT c.* FROM favorites f INNER JOIN cached_tracks c ON c.trackKey = f.trackKey
           ORDER BY f.addedAt DESC"""
    )
    suspend fun getTracks(): List<CachedTrackEntity>

    @Query("DELETE FROM favorites WHERE trackKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE trackKey = :key")
    suspend fun count(key: String): Int
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query(
        """SELECT c.* FROM cached_tracks c INNER JOIN
           (SELECT trackKey, MAX(playedAt) AS lastPlayed FROM history GROUP BY trackKey) h
           ON h.trackKey = c.trackKey ORDER BY h.lastPlayed DESC LIMIT :limit"""
    )
    fun observeRecent(limit: Int): Flow<List<CachedTrackEntity>>

    @Query(
        """SELECT c.* FROM cached_tracks c INNER JOIN
           (SELECT trackKey, COUNT(*) AS plays FROM history GROUP BY trackKey) h
           ON h.trackKey = c.trackKey ORDER BY h.plays DESC LIMIT :limit"""
    )
    fun observeMostPlayed(limit: Int): Flow<List<CachedTrackEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Insert
    suspend fun insertAll(entries: List<HistoryEntity>)

    @Query("SELECT * FROM history ORDER BY playedAt")
    suspend fun getAll(): List<HistoryEntity>

    @Query(
        """SELECT c.* FROM cached_tracks c INNER JOIN
           (SELECT trackKey, MAX(playedAt) AS lastPlayed FROM history GROUP BY trackKey) h
           ON h.trackKey = c.trackKey ORDER BY h.lastPlayed DESC LIMIT :limit"""
    )
    suspend fun getRecent(limit: Int): List<CachedTrackEntity>

    @Query(
        """SELECT h.playedAt AS playedAt, c.* FROM history h
           INNER JOIN cached_tracks c ON c.trackKey = h.trackKey
           WHERE h.playedAt >= :since ORDER BY h.playedAt"""
    )
    suspend fun eventsSince(since: Long): List<HistoryRow>

    @Query("SELECT trackKey AS trackKey, COUNT(*) AS plays FROM history GROUP BY trackKey")
    fun observePlayCounts(): Flow<List<PlayCount>>
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackKey = :key")
    suspend fun get(key: String): LyricsEntity?

    @Upsert
    suspend fun upsert(entity: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE trackKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts")
    suspend fun getAll(): List<PodcastEntity>

    @Query("SELECT COUNT(*) FROM podcasts WHERE feedUrl = :feedUrl")
    fun observeSubscribed(feedUrl: String): Flow<Int>

    @Upsert
    suspend fun upsert(entity: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun delete(feedUrl: String)

    @Query("SELECT * FROM episode_progress WHERE trackKey = :key")
    suspend fun progress(key: String): EpisodeProgressEntity?

    @Query("SELECT * FROM episode_progress")
    fun observeProgress(): Flow<List<EpisodeProgressEntity>>

    @Upsert
    suspend fun saveProgress(entity: EpisodeProgressEntity)
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM track_overrides")
    fun observeAll(): Flow<List<TrackOverrideEntity>>

    @Query("SELECT * FROM track_overrides")
    suspend fun getAll(): List<TrackOverrideEntity>

    @Query("SELECT * FROM track_overrides WHERE trackKey = :key")
    suspend fun get(key: String): TrackOverrideEntity?

    @Upsert
    suspend fun upsert(entity: TrackOverrideEntity)

    @Query("DELETE FROM track_overrides WHERE trackKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface AlbumArtDao {
    @Query("SELECT * FROM album_art WHERE albumKey = :key")
    suspend fun get(key: String): AlbumArtEntity?

    @Upsert
    suspend fun upsert(entity: AlbumArtEntity)

    @Query("DELETE FROM album_art")
    suspend fun clear()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackKey = :key")
    suspend fun get(key: String): DownloadEntity?

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, progress = :progress, error = :error WHERE trackKey = :key")
    suspend fun updateStatus(key: String, status: String, progress: Int, error: String?)

    @Query("UPDATE downloads SET status = :status, progress = 100, localUri = :localUri, error = NULL WHERE trackKey = :key")
    suspend fun markCompleted(key: String, status: String, localUri: String)

    @Query("DELETE FROM downloads WHERE trackKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface ImportDao {
    @Query(
        """SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
           (SELECT COUNT(*) FROM imported_tracks t WHERE t.playlistId = p.id) AS total,
           (SELECT COUNT(*) FROM imported_tracks t WHERE t.playlistId = p.id AND t.matchedTrackKey IS NOT NULL) AS matched
           FROM imported_playlists p ORDER BY p.createdAt DESC"""
    )
    fun observeSummaries(): Flow<List<ImportedPlaylistSummary>>

    @Query("SELECT * FROM imported_playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<ImportedPlaylistEntity?>

    @Query("SELECT * FROM imported_tracks WHERE playlistId = :id ORDER BY position")
    fun observeTracks(id: Long): Flow<List<ImportedTrackEntity>>

    @Query("SELECT * FROM imported_tracks WHERE playlistId = :id ORDER BY position")
    suspend fun getTracks(id: Long): List<ImportedTrackEntity>

    @Insert
    suspend fun insertPlaylist(p: ImportedPlaylistEntity): Long

    @Insert
    suspend fun insertTracks(tracks: List<ImportedTrackEntity>)

    @Query("UPDATE imported_tracks SET matchedTrackKey = :key WHERE id = :id")
    suspend fun setMatch(id: Long, key: String?)

    @Query("DELETE FROM imported_playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)
}
