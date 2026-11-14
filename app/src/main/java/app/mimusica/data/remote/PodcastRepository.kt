package app.mimusica.data.remote

import app.mimusica.data.db.AppDatabase
import app.mimusica.data.db.PodcastEntity
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource
import app.mimusica.domain.podcast.PodcastEpisode
import app.mimusica.domain.podcast.PodcastFeed
import app.mimusica.domain.podcast.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Resultado de buscar podcasts en el directorio. */
data class PodcastSummary(val feedUrl: String, val title: String, val author: String, val artworkUrl: String?)

/** Parser de la búsqueda de podcasts (directorio de iTunes). */
object PodcastSearchParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): List<PodcastSummary> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return root.array("results").orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val feed = o.text("feedUrl") ?: return@mapNotNull null
            PodcastSummary(
                feedUrl = feed,
                title = o.text("collectionName") ?: o.text("trackName") ?: return@mapNotNull null,
                author = o.text("artistName").orEmpty(),
                artworkUrl = o.text("artworkUrl600") ?: o.text("artworkUrl100"),
            )
        }
    }
}

/** Podcasts: búsqueda en el directorio público, lectura del feed RSS y suscripciones. */
class PodcastRepository(private val client: OkHttpClient, db: AppDatabase) {
    private val dao = db.podcastDao()
    private val feedCache = HashMap<String, Pair<Long, PodcastFeed>>()

    val subscriptions = dao.observeAll()
    val progress = dao.observeProgress()
    fun isSubscribed(feedUrl: String) = dao.observeSubscribed(feedUrl)

    suspend fun search(term: String, country: String = "CO"): List<PodcastSummary> = remoteCall {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("term", term.trim())
            .addQueryParameter("country", country.ifBlank { "CO" })
            .addQueryParameter("limit", "40")
            .build()
        val result = client.getText(url)
        if (!result.isSuccessful) throw RemoteApiException("El directorio de podcasts respondió con un error (código ${result.code}).")
        PodcastSearchParser.parse(result.body)
    }

    /** Descarga y lee el feed RSS (se recuerda 10 minutos en memoria). */
    suspend fun feed(feedUrl: String, refresh: Boolean = false): PodcastFeed = remoteCall {
        val now = System.currentTimeMillis()
        val cached = synchronized(feedCache) { feedCache[feedUrl] }
        if (!refresh && cached != null && now - cached.first < 10 * 60_000L) return@remoteCall cached.second
        val url = feedUrl.trim().toHttpUrlOrNull()
            ?: throw RemoteApiException("El enlace del podcast no es válido. Debe empezar por http:// o https://.")
        val feed = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw RemoteApiException("El podcast respondió con un error (código ${response.code}).")
                try {
                    response.body.byteStream().use { RssParser.parse(it) }
                } catch (e: java.io.IOException) {
                    throw e
                } catch (e: Exception) {
                    throw RemoteApiException("El enlace no parece un podcast (no es un feed RSS válido).")
                }
            }
        }
        synchronized(feedCache) { feedCache[feedUrl] = now to feed }
        feed
    }

    suspend fun subscribe(feedUrl: String, feed: PodcastFeed) {
        dao.upsert(PodcastEntity(feedUrl, feed.title, feed.author, feed.imageUrl, System.currentTimeMillis()))
    }

    suspend fun unsubscribe(feedUrl: String) = dao.delete(feedUrl)

    companion object {
        fun episodeToTrack(episode: PodcastEpisode, feed: PodcastFeed): Track = Track(
            key = Track.podcastKey(episode.id),
            title = episode.title,
            artist = feed.title,
            album = feed.title,
            durationMs = episode.durationMs,
            uri = episode.audioUrl,
            artworkUri = episode.imageUrl ?: feed.imageUrl,
            source = TrackSource.PODCAST,
            downloadUrl = episode.audioUrl,
        )
    }
}
