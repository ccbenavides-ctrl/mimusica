package app.mimusica.data.remote

import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** Parser de la API de Radio Browser (/json/stations/search). */
object RadioParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseStations(body: String): List<Track> {
        val array = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        val seen = HashSet<String>()
        return array.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val uuid = o.text("stationuuid") ?: return@mapNotNull null
            val url = (o.text("url_resolved") ?: o.text("url"))?.trim() ?: return@mapNotNull null
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
            val name = o.text("name")?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!seen.add(url) || !seen.add("id:$uuid")) return@mapNotNull null
            val tags = o.text("tags").orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }.take(2)
            val details = (listOfNotNull(o.text("country")) + tags).joinToString(" · ")
            Track(
                key = Track.radioKey(uuid),
                title = name,
                artist = details.ifBlank { "Radio en vivo" },
                album = "Radio en vivo",
                durationMs = 0L,
                uri = url,
                artworkUri = o.text("favicon")?.takeIf { it.startsWith("http") },
                source = TrackSource.RADIO,
                infoUrl = o.text("homepage")?.takeIf { it.startsWith("http") },
            )
        }
    }
}

data class RadioTag(val tag: String, val label: String)

/** Emisoras de radio por internet del directorio abierto Radio Browser (gratuito, sin clave). */
class RadioRepository(private val client: OkHttpClient) {

    /**
     * Busca emisoras. Todos los filtros son opcionales; ordena por popularidad.
     * Prueba varios servidores del directorio hasta que uno responda.
     */
    suspend fun stations(
        name: String? = null,
        countryCode: String? = null,
        tag: String? = null,
        limit: Int = 60,
    ): List<Track> = remoteCall {
        var lastError: Exception? = null
        for (server in SERVERS) {
            try {
                val url = "$server/json/stations/search".toHttpUrl().newBuilder().apply {
                    if (!name.isNullOrBlank()) addQueryParameter("name", name.trim())
                    if (!countryCode.isNullOrBlank()) addQueryParameter("countrycode", countryCode.trim().uppercase())
                    if (!tag.isNullOrBlank()) addQueryParameter("tag", tag)
                    addQueryParameter("order", "clickcount")
                    addQueryParameter("reverse", "true")
                    addQueryParameter("hidebroken", "true")
                    addQueryParameter("limit", limit.toString())
                }.build()
                val result = client.getText(url)
                if (!result.isSuccessful) throw RemoteApiException("El directorio de radios respondió con un error (código ${result.code}).")
                return@remoteCall RadioParser.parseStations(result.body)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RemoteApiException("No se pudo consultar el directorio de radios.")
    }

    companion object {
        private val SERVERS = listOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
        )

        val TAGS = listOf(
            RadioTag("pop", "Pop"),
            RadioTag("rock", "Rock"),
            RadioTag("salsa", "Salsa"),
            RadioTag("vallenato", "Vallenato"),
            RadioTag("reggaeton", "Reguetón"),
            RadioTag("tropical", "Tropical"),
            RadioTag("news", "Noticias"),
            RadioTag("jazz", "Jazz"),
            RadioTag("classical", "Clásica"),
            RadioTag("electronic", "Electrónica"),
            RadioTag("80s", "Años 80"),
        )
    }
}
