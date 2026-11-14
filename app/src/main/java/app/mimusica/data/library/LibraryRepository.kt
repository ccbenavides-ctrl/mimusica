package app.mimusica.data.library

import android.content.Context
import android.net.Uri
import app.mimusica.data.db.AppDatabase
import app.mimusica.data.db.CachedTrackEntity
import app.mimusica.data.db.FavoriteEntity
import app.mimusica.data.db.HistoryEntity
import app.mimusica.data.db.ImportedPlaylistEntity
import app.mimusica.data.db.ImportedTrackEntity
import app.mimusica.data.db.PlaylistEntity
import app.mimusica.domain.importer.ImportedRow
import app.mimusica.domain.m3u.M3uCodec
import app.mimusica.domain.m3u.M3uEntry
import app.mimusica.domain.matching.TextNormalizer
import app.mimusica.domain.matching.TrackMatcher
import app.mimusica.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Listas automáticas. */
enum class AutoPlaylist(val id: String, val title: String) {
    FAVORITES("favoritos", "Favoritos"),
    RECENT("recientes", "Escuchado recientemente"),
    MOST_PLAYED("mas-escuchadas", "Más escuchadas"),
    RECENTLY_ADDED("agregadas", "Agregado recientemente");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

data class M3uImportResult(val playlistId: Long, val name: String, val added: Int, val missing: Int)

/** Listas, favoritos, historial, playlists importadas y export/import .m3u. */
class LibraryRepository(
    private val context: Context,
    private val db: AppDatabase,
    scope: CoroutineScope,
) {
    private val playlistDao = db.playlistDao()
    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()
    private val cachedDao = db.cachedTrackDao()
    private val importDao = db.importDao()

    val playlists = playlistDao.observeSummaries()

    val favoriteKeys: StateFlow<Set<String>> = favoriteDao.observeKeys()
        .map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val favorites: Flow<List<Track>> = favoriteDao.observeTracks().map { list -> list.map { it.toTrack() } }
    fun recent(limit: Int = 100): Flow<List<Track>> = historyDao.observeRecent(limit).map { l -> l.map { it.toTrack() } }
    fun mostPlayed(limit: Int = 100): Flow<List<Track>> = historyDao.observeMostPlayed(limit).map { l -> l.map { it.toTrack() } }

    fun playlist(id: Long) = playlistDao.observe(id)
    fun playlistTracks(id: Long): Flow<List<Track>> =
        playlistDao.observeTracks(id).map { rows -> rows.map { it.track.toTrack() } }

    suspend fun cache(track: Track) = cachedDao.upsert(CachedTrackEntity.from(track))
    suspend fun cacheAll(tracks: List<Track>) = cachedDao.upsertAll(tracks.map { CachedTrackEntity.from(it) })

    // ---- Favoritos ----
    suspend fun toggleFavorite(track: Track): Boolean {
        return if (favoriteDao.count(track.key) > 0) {
            favoriteDao.delete(track.key)
            false
        } else {
            cache(track)
            favoriteDao.insert(FavoriteEntity(track.key, System.currentTimeMillis()))
            true
        }
    }

    // ---- Historial ----
    suspend fun recordPlay(track: Track) {
        cache(track)
        historyDao.insert(HistoryEntity(trackKey = track.key, playedAt = System.currentTimeMillis()))
    }

    suspend fun clearHistory() = historyDao.clear()

    // ---- Listas ----
    suspend fun createPlaylist(name: String): Long =
        playlistDao.insert(PlaylistEntity(name = name.trim().ifBlank { "Nueva lista" }, createdAt = System.currentTimeMillis()))

    suspend fun renamePlaylist(id: Long, name: String) = playlistDao.rename(id, name.trim().ifBlank { "Lista" })
    suspend fun deletePlaylist(id: Long) = playlistDao.delete(id)

    suspend fun addToPlaylist(id: Long, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        cacheAll(tracks)
        playlistDao.appendEntries(id, tracks.map { it.key })
    }

    suspend fun removeFromPlaylist(id: Long, index: Int) {
        val keys = playlistDao.getEntries(id).map { it.trackKey }.toMutableList()
        if (index !in keys.indices) return
        keys.removeAt(index)
        playlistDao.replaceEntries(id, keys)
    }

    suspend fun movePlaylistItem(id: Long, from: Int, to: Int) {
        val keys = playlistDao.getEntries(id).map { it.trackKey }.toMutableList()
        if (from !in keys.indices || to !in keys.indices || from == to) return
        val item = keys.removeAt(from)
        keys.add(to, item)
        playlistDao.replaceEntries(id, keys)
    }

    // ---- M3U ----
    suspend fun exportM3u(name: String, tracks: List<Track>, target: Uri) = withContext(Dispatchers.IO) {
        val text = M3uCodec.encode(name, tracks.map {
            M3uEntry(location = it.uri, title = it.title, artist = it.artist, durationSec = it.durationMs / 1000)
        })
        val stream = context.contentResolver.openOutputStream(target, "wt")
            ?: throw IllegalStateException("No se pudo abrir el archivo de destino.")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Importa un .m3u: reconoce pistas locales (content://), de Internet Archive y Jamendo;
     * las demás se emparejan por nombre con la biblioteca local.
     */
    suspend fun importM3u(source: Uri, fileName: String?, library: List<Track>): M3uImportResult {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalStateException("No se pudo leer el archivo.")
        }
        val parsed = M3uCodec.decode(text)
        val byKey = library.associateBy { it.key }
        val matcher = withContext(Dispatchers.Default) { TrackMatcher(library) }
        val byBaseName = library.groupBy { TextNormalizer.basic(it.title) }
        val resolved = mutableListOf<Track>()
        var missing = 0
        withContext(Dispatchers.Default) {
            for (e in parsed.entries) {
                val key = M3uCodec.keyFromLocation(e.location)
                val track: Track? = when {
                    key != null && byKey.containsKey(key) -> byKey[key]
                    key != null && !key.startsWith("local:") -> cachedDao.get(key)?.toTrack()
                        ?: remoteTrackFromEntry(key, e)
                    else -> {
                        val byTitle = e.title?.let { t -> matcher.findBest(t, e.artist.orEmpty())?.track }
                        byTitle ?: byBaseName[TextNormalizer.basic(M3uCodec.baseName(e.location))]?.firstOrNull()
                    }
                }
                if (track != null) resolved.add(track) else missing++
            }
        }
        val name = parsed.name ?: fileName?.substringBeforeLast('.') ?: "Lista importada"
        val id = createPlaylist(name)
        addToPlaylist(id, resolved)
        return M3uImportResult(id, name, resolved.size, missing)
    }

    private fun remoteTrackFromEntry(key: String, e: M3uEntry): Track? {
        if (!e.location.startsWith("https://")) return null
        return Track(
            key = key,
            title = e.title ?: M3uCodec.baseName(e.location),
            artist = e.artist ?: Track.UNKNOWN_ARTIST,
            album = "",
            durationMs = if (e.durationSec > 0) e.durationSec * 1000 else 0,
            uri = e.location,
            artworkUri = null,
            source = Track.sourceOfKey(key),
            downloadUrl = if (key.startsWith("ia:")) e.location else null,
        )
    }

    // ---- Playlists importadas (solo nombres) ----
    val importedPlaylists = importDao.observeSummaries()
    fun importedPlaylist(id: Long) = importDao.observePlaylist(id)
    fun importedTracks(id: Long) = importDao.observeTracks(id)

    suspend fun saveImport(name: String, rows: List<ImportedRow>, library: List<Track>): Long {
        val id = importDao.insertPlaylist(ImportedPlaylistEntity(name = name.trim().ifBlank { "Playlist importada" }, createdAt = System.currentTimeMillis()))
        val matcher = withContext(Dispatchers.Default) { TrackMatcher(library) }
        val entities = withContext(Dispatchers.Default) {
            rows.mapIndexed { i, r ->
                ImportedTrackEntity(
                    playlistId = id,
                    position = i,
                    title = r.title,
                    artist = r.artist,
                    album = r.album,
                    matchedTrackKey = matcher.findBest(r.title, r.artist)?.track?.key,
                )
            }
        }
        importDao.insertTracks(entities)
        return id
    }

    /** Vuelve a emparejar (por ejemplo, después de descargar canciones nuevas). */
    suspend fun rematchImport(id: Long, library: List<Track>): Int {
        val matcher = withContext(Dispatchers.Default) { TrackMatcher(library) }
        var matched = 0
        for (t in importDao.getTracks(id)) {
            val key = withContext(Dispatchers.Default) { matcher.findBest(t.title, t.artist)?.track?.key }
            if (key != null) matched++
            if (key != t.matchedTrackKey) importDao.setMatch(t.id, key)
        }
        return matched
    }

    suspend fun deleteImport(id: Long) = importDao.deletePlaylist(id)

    suspend fun createPlaylistFromImport(name: String, tracks: List<Track>): Long {
        val id = createPlaylist(name)
        addToPlaylist(id, tracks)
        return id
    }
}
