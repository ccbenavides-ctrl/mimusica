package app.mimusica.data.remote

import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/** Un ítem (álbum, concierto, sello) de Internet Archive. */
data class IaItem(
    val identifier: String,
    val title: String,
    val creator: String,
    val licenseUrl: String?,
    val collections: List<String>,
    val date: String?,
) {
    val thumbnailUrl: String get() = InternetArchiveParser.thumbnailUrl(identifier)
}

data class IaSearchPage(val items: List<IaItem>, val numFound: Int, val page: Int, val rows: Int) {
    val hasMore: Boolean get() = page * rows < numFound
}

data class IaItemDetails(
    val identifier: String,
    val title: String,
    val creator: String,
    val licenseUrl: String?,
    val date: String?,
    val description: String?,
    val tracks: List<Track>,
) {
    val pageUrl: String get() = "https://archive.org/details/$identifier"
}

/**
 * Parsers de las respuestas JSON de Internet Archive:
 *  - advancedsearch.php?...&output=json  -> responseHeader / response.docs[]
 *  - metadata/<identifier>               -> metadata{} + files[]
 */
object InternetArchiveParser {

    const val BASE = "https://archive.org"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun thumbnailUrl(identifier: String) = "$BASE/services/img/$identifier"

    fun downloadUrl(identifier: String, fileName: String): String =
        "$BASE/download/$identifier/" + fileName.split('/').joinToString("/") { encodeSegment(it) }

    private fun encodeSegment(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun parseSearch(body: String, page: Int, rows: Int): IaSearchPage {
        val root = json.parseToJsonElement(body).jsonObject
        root.text("error")?.let { throw RemoteApiException("Internet Archive respondió con un error: $it") }
        val response = root.obj("response") ?: throw RemoteApiException("Respuesta inesperada de Internet Archive.")
        val numFound = response.long("numFound")?.toInt() ?: 0
        val items = response.array("docs").orEmpty().mapNotNull { el ->
            val doc = el as? JsonObject ?: return@mapNotNull null
            val id = doc.text("identifier") ?: return@mapNotNull null
            IaItem(
                identifier = id,
                title = doc.text("title") ?: id,
                creator = doc.text("creator") ?: Track.UNKNOWN_ARTIST,
                licenseUrl = doc.text("licenseurl"),
                collections = doc.textList("collection"),
                date = doc.text("date")?.take(10),
            )
        }
        return IaSearchPage(items, numFound, page, rows)
    }

    /** Prioridad de formatos: MP3 (mejor para streaming), luego Ogg y por último FLAC. */
    internal fun formatPriority(format: String, name: String): Int {
        val f = format.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            f == "vbr mp3" -> 0
            f.contains("mp3") || ext == "mp3" -> 1
            f.contains("ogg") || ext == "ogg" -> 2
            f.contains("flac") || ext == "flac" -> 3
            else -> -1
        }
    }

    /** Duración: "1765.23" (segundos) o "29:25" / "1:02:03". */
    internal fun parseLengthMs(length: String?): Long {
        if (length.isNullOrBlank()) return 0
        if (length.contains(':')) {
            val parts = length.split(':').map { it.trim().toDoubleOrNull() ?: return 0 }
            var seconds = 0.0
            for (p in parts) seconds = seconds * 60 + p
            return (seconds * 1000).toLong()
        }
        return ((length.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }

    private fun parseTrackNumber(track: String?): Int =
        track?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0

    fun parseMetadata(identifier: String, body: String): IaItemDetails {
        val root = json.parseToJsonElement(body).jsonObject
        val metadata = root.obj("metadata")
            ?: throw RemoteApiException("No se encontró el ítem \"$identifier\" en Internet Archive.")
        val itemTitle = metadata.text("title") ?: identifier
        val itemCreator = metadata.text("creator") ?: Track.UNKNOWN_ARTIST
        val license = metadata.text("licenseurl")
        val artwork = thumbnailUrl(identifier)
        val pageUrl = "$BASE/details/$identifier"

        data class Candidate(val file: JsonObject, val name: String, val priority: Int)

        val candidates = root.array("files").orEmpty().mapNotNull { el ->
            val f = el as? JsonObject ?: return@mapNotNull null
            val name = f.text("name") ?: return@mapNotNull null
            if (f.bool("private")) return@mapNotNull null
            val priority = formatPriority(f.text("format").orEmpty(), name)
            if (priority < 0) return@mapNotNull null
            Candidate(f, name, priority)
        }
        // Agrupa versiones del mismo tema (p. ej. .flac, .mp3 y .ogg) y se queda con la mejor para streaming.
        val chosen = candidates
            .groupBy { it.name.substringBeforeLast('.').lowercase() }
            .values
            .map { group -> group.minByOrNull { it.priority }!! }

        val tracks = chosen.map { c ->
            val f = c.file
            val title = f.text("title") ?: c.name.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ')
            val url = downloadUrl(identifier, c.name)
            Track(
                key = Track.internetArchiveKey(identifier, c.name),
                title = title,
                artist = f.text("artist") ?: f.text("creator") ?: itemCreator,
                album = f.text("album") ?: itemTitle,
                durationMs = parseLengthMs(f.text("length")),
                uri = url,
                artworkUri = artwork,
                source = TrackSource.INTERNET_ARCHIVE,
                license = license,
                downloadUrl = url,
                infoUrl = pageUrl,
                trackNumber = parseTrackNumber(f.text("track")),
            )
        }.sortedWith(compareBy<Track>({ if (it.trackNumber > 0) 0 else 1 }, { it.trackNumber }, { it.key }))

        return IaItemDetails(
            identifier = identifier,
            title = itemTitle,
            creator = itemCreator,
            licenseUrl = license,
            date = metadata.text("date"),
            description = metadata.text("description"),
            tracks = tracks,
        )
    }

    /** Limpia el texto del usuario para usarlo dentro de una consulta Lucene. */
    fun sanitizeQuery(text: String): String =
        text.replace(Regex("""[+\-&|!(){}\[\]^"~*?:\\/]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /**
     * Filtro de legalidad: solo audio de colecciones con permiso de redistribución
     * (Live Music Archive, netlabels) o con licencia Creative Commons / dominio público.
     */
    const val LICENSE_FILTER =
        "(collection:etree OR collection:netlabels OR licenseurl:*creativecommons* OR licenseurl:*publicdomain*)"

    fun buildQuery(text: String, collection: String?): String {
        val parts = mutableListOf<String>()
        val clean = sanitizeQuery(text)
        if (clean.isNotBlank()) parts.add("($clean)")
        if (!collection.isNullOrBlank()) parts.add("collection:$collection")
        parts.add("mediatype:(audio OR etree)")
        parts.add(LICENSE_FILTER)
        return parts.joinToString(" AND ")
    }
}
