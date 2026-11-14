package app.mimusica.data.remote

import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class JamendoPage(val tracks: List<Track>, val resultsCount: Int, val offset: Int, val limit: Int) {
    /** Hay más páginas si la API devolvió el máximo pedido (aunque luego se filtren pistas). */
    val hasMore: Boolean get() = resultsCount >= limit
}

/** Parser de la API de Jamendo v3.0 (/tracks). */
object JamendoParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseTracks(body: String, offset: Int, limit: Int): JamendoPage {
        val root = json.parseToJsonElement(body).jsonObject
        val headers = root.obj("headers") ?: throw RemoteApiException("Respuesta inesperada de Jamendo.")
        val status = headers.text("status")
        if (status != null && status != "success") {
            val code = headers.long("code")
            val msg = headers.text("error_message") ?: "error desconocido"
            val hint = if (code == 5L || msg.contains("client", ignoreCase = true))
                " Revisa el client_id de Jamendo en Ajustes." else ""
            throw RemoteApiException("Jamendo respondió con un error ($msg).$hint")
        }
        val results = root.array("results").orEmpty()
        val tracks = results.mapNotNull { el ->
            val r = el as? JsonObject ?: return@mapNotNull null
            // Solo pistas cuya descarga está autorizada por el artista.
            if (!r.bool("audiodownload_allowed")) return@mapNotNull null
            val id = r.text("id") ?: return@mapNotNull null
            val audio = r.text("audio") ?: return@mapNotNull null
            Track(
                key = Track.jamendoKey(id),
                title = r.text("name") ?: "Sin título",
                artist = r.text("artist_name") ?: Track.UNKNOWN_ARTIST,
                album = r.text("album_name") ?: "Sencillo",
                durationMs = (r.long("duration") ?: 0L) * 1000L,
                uri = audio,
                artworkUri = r.text("image") ?: r.text("album_image"),
                source = TrackSource.JAMENDO,
                license = r.text("license_ccurl"),
                downloadUrl = r.text("audiodownload"),
                infoUrl = r.text("shareurl") ?: r.text("shorturl"),
                trackNumber = r.long("position")?.toInt() ?: 0,
            )
        }
        val count = headers.long("results_count")?.toInt() ?: results.size
        return JamendoPage(tracks, count, offset, limit)
    }
}
