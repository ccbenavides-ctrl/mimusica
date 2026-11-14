package app.mimusica.data.remote

import app.mimusica.data.db.AppDatabase
import app.mimusica.data.db.LyricsEntity
import app.mimusica.domain.lyrics.LrcParser
import app.mimusica.domain.lyrics.LyricLine
import app.mimusica.domain.matching.TextNormalizer
import app.mimusica.domain.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.math.abs

/** Letra de una canción: sincronizada (con tiempos) y/o en texto plano. */
data class Lyrics(val synced: List<LyricLine>, val plain: String?) {
    val isEmpty: Boolean get() = synced.isEmpty() && plain.isNullOrBlank()
}

/** Elige la mejor coincidencia entre las respuestas de LRCLIB. Sin dependencias de Android. */
object LyricsParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Candidate(val synced: String?, val plain: String?, val durationSec: Double, val instrumental: Boolean)

    fun parseOne(body: String): Candidate? = (json.parseToJsonElement(body) as? JsonObject)?.let { candidate(it) }

    fun parseMany(body: String): List<Candidate> =
        (json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::candidate) }

    private fun candidate(o: JsonObject): Candidate? {
        val synced = o.text("syncedLyrics")
        val plain = o.text("plainLyrics")
        val instrumental = o.bool("instrumental")
        if (synced == null && plain == null && !instrumental) return null
        return Candidate(synced, plain, o.text("duration")?.toDoubleOrNull() ?: 0.0, instrumental)
    }

    /** Prefiere letra sincronizada y la duración más parecida a la de la canción. */
    fun pickBest(candidates: List<Candidate>, durationSec: Long): Candidate? {
        val usable = candidates.filter { !it.instrumental && (it.synced != null || it.plain != null) }
        if (usable.isEmpty()) return null
        return usable.minByOrNull { c ->
            val durationPenalty = if (durationSec > 0 && c.durationSec > 0) abs(c.durationSec - durationSec) else 5.0
            durationPenalty + if (c.synced == null) 8.0 else 0.0
        }
    }
}

/** Letras desde LRCLIB (base de datos abierta y gratuita). Se guardan para no volver a pedirlas. */
class LyricsRepository(private val client: OkHttpClient, db: AppDatabase) {
    private val dao = db.lyricsDao()

    /** Devuelve null si no hay letra para la canción. */
    suspend fun lyricsFor(track: Track, refresh: Boolean = false): Lyrics? {
        if (track.isLive || track.isPodcast) return null
        if (!refresh) {
            val saved = dao.get(track.key)
            if (saved != null) {
                val stale = !saved.found && System.currentTimeMillis() - saved.fetchedAt > RETRY_AFTER_MS
                if (!stale) return if (saved.found) toLyrics(saved.synced, saved.plain) else null
            }
        }
        val best = remoteCall { fetch(track) }
        dao.upsert(
            LyricsEntity(
                trackKey = track.key,
                synced = best?.synced,
                plain = best?.plain,
                found = best != null,
                fetchedAt = System.currentTimeMillis(),
            )
        )
        return best?.let { toLyrics(it.synced, it.plain) }
    }

    private suspend fun fetch(track: Track): LyricsParser.Candidate? {
        val title = track.title.trim()
        val artist = track.artist.takeIf { it != Track.UNKNOWN_ARTIST }?.trim().orEmpty()
        val durationSec = track.durationMs / 1000
        if (title.isBlank()) return null

        // 1) Coincidencia exacta (título + artista + álbum + duración).
        if (artist.isNotBlank() && durationSec > 0) {
            val url = "$BASE/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("album_name", track.album.takeIf { it != Track.UNKNOWN_ALBUM }.orEmpty())
                .addQueryParameter("duration", durationSec.toString())
                .build()
            val result = client.getText(url)
            if (result.isSuccessful) {
                val c = LyricsParser.parseOne(result.body)
                if (c != null && !c.instrumental && (c.synced != null || c.plain != null)) return c
            }
        }
        // 2) Búsqueda por título limpio (sin "Remastered", "feat.", etc.) y artista.
        val search = "$BASE/api/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("track_name", TextNormalizer.title(title).ifBlank { title })
            if (artist.isNotBlank()) addQueryParameter("artist_name", TextNormalizer.artist(artist).ifBlank { artist })
        }.build()
        val result = client.getText(search)
        if (!result.isSuccessful) {
            if (result.code == 404) return null
            throw RemoteApiException("El servicio de letras respondió con un error (código ${result.code}).")
        }
        return LyricsParser.pickBest(LyricsParser.parseMany(result.body), durationSec)
    }

    private fun toLyrics(synced: String?, plain: String?): Lyrics? {
        val lines = synced?.let { LrcParser.parse(it) }.orEmpty()
        val lyrics = Lyrics(lines, plain ?: lines.joinToString("\n") { it.text }.takeIf { it.isNotBlank() })
        return lyrics.takeIf { !it.isEmpty }
    }

    companion object {
        private const val BASE = "https://lrclib.net"
        private const val RETRY_AFTER_MS = 7L * 24 * 3600 * 1000
    }
}
