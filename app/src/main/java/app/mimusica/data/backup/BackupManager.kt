package app.mimusica.data.backup

import android.content.Context
import android.net.Uri
import app.mimusica.data.db.AppDatabase
import app.mimusica.data.db.CachedTrackEntity
import app.mimusica.data.db.FavoriteEntity
import app.mimusica.data.db.HistoryEntity
import app.mimusica.data.db.PodcastEntity
import app.mimusica.data.db.TrackOverrideEntity
import app.mimusica.data.library.LibraryRepository
import app.mimusica.data.local.LocalMusicRepository
import app.mimusica.domain.matching.TrackMatcher
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Contenido de una copia de seguridad, ya leído. */
data class BackupData(
    val tracks: Map<String, Track>,
    val playlists: List<Pair<String, List<String>>>,
    val favorites: List<Pair<String, Long>>,
    val history: List<Pair<String, Long>>,
    val podcasts: List<PodcastEntity>,
    val overrides: List<TrackOverrideEntity>,
)

data class RestoreSummary(val playlists: Int, val favorites: Int, val history: Int, val podcasts: Int, val missingTracks: Int)

/** Formato JSON de la copia de seguridad. Sin dependencias de Android (se prueba en JVM). */
object BackupCodec {
    const val VERSION = 1

    fun encode(data: BackupData): String = buildJsonObject {
        put("app", "MiMusica")
        put("version", VERSION)
        put("tracks", JsonObject(data.tracks.mapValues { TrackJson.toJson(it.value) }))
        put("playlists", buildJsonArray {
            for ((name, keys) in data.playlists) add(buildJsonObject {
                put("name", name)
                put("tracks", JsonArray(keys.map { JsonPrimitive(it) }))
            })
        })
        put("favorites", buildJsonArray {
            for ((key, at) in data.favorites) add(buildJsonObject { put("key", key); put("at", at) })
        })
        put("history", buildJsonArray {
            for ((key, at) in data.history) add(buildJsonObject { put("key", key); put("at", at) })
        })
        put("podcasts", buildJsonArray {
            for (p in data.podcasts) add(buildJsonObject {
                put("feedUrl", p.feedUrl)
                put("title", p.title)
                put("author", p.author)
                p.artworkUrl?.let { put("artworkUrl", it) }
                put("subscribedAt", p.subscribedAt)
            })
        })
        put("overrides", buildJsonArray {
            for (o in data.overrides) add(buildJsonObject {
                put("key", o.trackKey)
                o.title?.let { put("title", it) }
                o.artist?.let { put("artist", it) }
                o.album?.let { put("album", it) }
                o.artworkUri?.let { put("artworkUri", it) }
            })
        })
    }.toString()

    fun decode(text: String): BackupData {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw IllegalArgumentException("El archivo no es una copia de seguridad de MiMúsica.")
        if (root.str("app") != "MiMusica") throw IllegalArgumentException("El archivo no es una copia de seguridad de MiMúsica.")
        val tracks = (root["tracks"] as? JsonObject).orEmpty()
            .mapNotNull { (key, el) -> (el as? JsonObject)?.let { TrackJson.fromJson(it) }?.let { key to it } }
            .toMap()
        fun pairs(name: String) = root.arr(name).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val key = o.str("key") ?: return@mapNotNull null
            key to o.num("at")
        }
        return BackupData(
            tracks = tracks,
            playlists = root.arr("playlists").mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o.str("name") ?: return@mapNotNull null
                name to o.arr("tracks").mapNotNull { (it as? JsonPrimitive)?.content }
            },
            favorites = pairs("favorites"),
            history = pairs("history"),
            podcasts = root.arr("podcasts").mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                PodcastEntity(
                    feedUrl = o.str("feedUrl") ?: return@mapNotNull null,
                    title = o.str("title") ?: "Podcast",
                    author = o.str("author").orEmpty(),
                    artworkUrl = o.str("artworkUrl"),
                    subscribedAt = o.num("subscribedAt"),
                )
            },
            overrides = root.arr("overrides").mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                TrackOverrideEntity(o.str("key") ?: return@mapNotNull null, o.str("title"), o.str("artist"), o.str("album"), o.str("artworkUri"))
            },
        )
    }

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(name: String): Long = (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: 0L
    private fun JsonObject.arr(name: String): List<JsonElement> = (this[name] as? JsonArray).orEmpty()
}

/** Copia de seguridad de listas, favoritos, historial, podcasts y datos corregidos en un archivo JSON. */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val library: LibraryRepository,
    private val localMusic: LocalMusicRepository,
) {
    suspend fun export(target: Uri) {
        val tracks = db.cachedTrackDao().getAll().associate { it.trackKey to it.toTrack() }
        val playlists = db.playlistDao().getAll().map { p ->
            p.name to db.playlistDao().getEntries(p.id).map { it.trackKey }
        }
        val data = BackupData(
            tracks = tracks,
            playlists = playlists,
            favorites = db.favoriteDao().getAll().map { it.trackKey to it.addedAt },
            history = db.historyDao().getAll().map { it.trackKey to it.playedAt },
            podcasts = db.podcastDao().getAll(),
            overrides = db.overrideDao().getAll(),
        )
        val text = withContext(Dispatchers.Default) { BackupCodec.encode(data) }
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: throw IllegalStateException("No se pudo abrir el archivo de destino.")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    /**
     * Restaura una copia. Las canciones locales se identifican por su número interno en el teléfono,
     * así que si la copia viene de otro teléfono (o la música se volvió a copiar) se buscan por título y artista.
     */
    suspend fun restore(source: Uri): RestoreSummary {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalStateException("No se pudo leer el archivo.")
        }
        val data = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
        val current = localMusic.state.value.tracks
        val byKey = current.associateBy { it.key }
        val matcher = withContext(Dispatchers.Default) { TrackMatcher(current) }

        // Clave de la copia → pista utilizable hoy (null si no se encontró).
        val resolved = HashMap<String, Track?>()
        var missing = 0
        withContext(Dispatchers.Default) {
            for ((key, track) in data.tracks) {
                resolved[key] = if (!key.startsWith("local:")) track else {
                    val same = byKey[key]
                    if (same != null && same.title == track.title) same
                    else matcher.findBest(track.title, track.artist)?.track
                }
                if (resolved[key] == null) missing++
            }
        }
        val usable = resolved.values.filterNotNull()
        db.cachedTrackDao().upsertAll(usable.distinctBy { it.key }.map { CachedTrackEntity.from(it) })

        val existingNames = db.playlistDao().getAll().map { it.name }.toSet()
        var playlists = 0
        for ((name, keys) in data.playlists) {
            if (name in existingNames) continue
            val tracks = keys.mapNotNull { resolved[it] }
            val id = library.createPlaylist(name)
            library.addToPlaylist(id, tracks)
            playlists++
        }

        val existingFavorites = db.favoriteDao().getAll().map { it.trackKey }.toSet()
        var favorites = 0
        for ((key, at) in data.favorites) {
            val track = resolved[key] ?: continue
            if (track.key in existingFavorites) continue
            db.favoriteDao().insert(FavoriteEntity(track.key, at))
            favorites++
        }

        val existingHistory = db.historyDao().getAll().map { it.trackKey to it.playedAt }.toSet()
        val newHistory = data.history.mapNotNull { (key, at) ->
            val track = resolved[key] ?: return@mapNotNull null
            if ((track.key to at) in existingHistory) null else HistoryEntity(trackKey = track.key, playedAt = at)
        }
        if (newHistory.isNotEmpty()) db.historyDao().insertAll(newHistory)

        val existingPodcasts = db.podcastDao().getAll().map { it.feedUrl }.toSet()
        var podcasts = 0
        for (p in data.podcasts) {
            if (p.feedUrl in existingPodcasts) continue
            db.podcastDao().upsert(p)
            podcasts++
        }

        for (o in data.overrides) {
            val key = resolved[o.trackKey]?.key ?: if (byKey.containsKey(o.trackKey)) o.trackKey else null
            if (key != null) db.overrideDao().upsert(o.copy(trackKey = key))
        }
        return RestoreSummary(playlists, favorites, newHistory.size, podcasts, missing)
    }
}
