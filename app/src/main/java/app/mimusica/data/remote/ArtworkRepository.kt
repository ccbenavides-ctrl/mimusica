package app.mimusica.data.remote

import app.mimusica.data.db.AlbumArtEntity
import app.mimusica.data.db.AppDatabase
import app.mimusica.domain.matching.TextNormalizer
import app.mimusica.domain.matching.TrackMatcher
import app.mimusica.domain.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** Parser de la búsqueda de álbumes de MusicBrainz (release-group). */
object MusicBrainzParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Devuelve el id del álbum que mejor coincide, o null. */
    fun bestReleaseGroupId(body: String, artist: String, album: String): String? {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        val wantedAlbum = TextNormalizer.title(album)
        val wantedArtist = TextNormalizer.artist(artist)
        for (el in root.array("release-groups").orEmpty()) {
            val o = el as? JsonObject ?: continue
            val id = o.text("id") ?: continue
            val title = TextNormalizer.title(o.text("title").orEmpty())
            if (TrackMatcher.similarity(title, wantedAlbum) < 0.85) continue
            val credits = o.array("artist-credit").orEmpty().mapNotNull { (it as? JsonObject)?.text("name") }
            val artistOk = wantedArtist.isBlank() || credits.any {
                TrackMatcher.similarity(TextNormalizer.artist(it), wantedArtist) >= 0.8
            }
            if (artistOk) return id
        }
        return null
    }
}

/**
 * Busca en internet la carátula de los álbumes que no la traen (MusicBrainz + Cover Art Archive,
 * ambos abiertos). Guarda el resultado, también cuando no encuentra nada, para no repetir la consulta.
 */
class ArtworkRepository(private val client: OkHttpClient, db: AppDatabase) {
    private val dao = db.albumArtDao()
    private val memory = ConcurrentHashMap<String, String>() // "" = no encontrada
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    fun albumKey(artist: String, album: String) = TextNormalizer.basic(artist) + "|" + TextNormalizer.basic(album)

    /** Carátula ya conocida, sin tocar la red. */
    fun cached(artist: String, album: String): String? = memory[albumKey(artist, album)]?.takeIf { it.isNotBlank() }

    suspend fun find(artist: String, album: String): String? {
        if (album.isBlank() || album == Track.UNKNOWN_ALBUM) return null
        if (artist.isBlank() || artist == Track.UNKNOWN_ARTIST) return null
        val key = albumKey(artist, album)
        memory[key]?.let { return it.takeIf { url -> url.isNotBlank() } }
        val saved = dao.get(key)
        if (saved != null) {
            val stale = saved.url == null && System.currentTimeMillis() - saved.checkedAt > RETRY_AFTER_MS
            if (!stale) {
                memory[key] = saved.url.orEmpty()
                return saved.url
            }
        }
        return mutex.withLock {
            memory[key]?.let { return@withLock it.takeIf { url -> url.isNotBlank() } }
            val url = try {
                lookup(artist, album)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock null // sin red: se reintenta otro día, no se guarda como "no encontrada"
            }
            memory[key] = url.orEmpty()
            dao.upsert(AlbumArtEntity(key, url, System.currentTimeMillis()))
            url
        }
    }

    private suspend fun lookup(artist: String, album: String): String? {
        // MusicBrainz pide como máximo una consulta por segundo.
        val wait = lastRequestAt + 1_100 - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastRequestAt = System.currentTimeMillis()

        val query = "releasegroup:\"${escape(TextNormalizer.title(album))}\" AND artist:\"${escape(TextNormalizer.artist(artist))}\""
        val url = "https://musicbrainz.org/ws/2/release-group/".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "5")
            .build()
        val result = client.getText(url)
        if (!result.isSuccessful) throw java.io.IOException("MusicBrainz respondió ${result.code}")
        val id = MusicBrainzParser.bestReleaseGroupId(result.body, artist, album) ?: return null
        val cover = "https://coverartarchive.org/release-group/$id/front-500"
        // Se comprueba que la imagen exista (muchos álbumes no tienen carátula subida).
        val exists = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(cover).head().build()).execute().use { it.isSuccessful }
        }
        return if (exists) cover else null
    }

    suspend fun clear() {
        memory.clear()
        dao.clear()
    }

    private fun escape(text: String) = text.replace("\\", " ").replace("\"", " ")

    companion object {
        private const val RETRY_AFTER_MS = 30L * 24 * 3600 * 1000
    }
}
