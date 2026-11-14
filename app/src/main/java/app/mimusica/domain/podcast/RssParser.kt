package app.mimusica.domain.podcast

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

data class PodcastEpisode(
    val id: String,
    val title: String,
    val audioUrl: String,
    val publishedAt: Long,
    val durationMs: Long,
    val imageUrl: String?,
    val description: String,
)

data class PodcastFeed(
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String?,
    val episodes: List<PodcastEpisode>,
)

/** Parser de un feed RSS de podcast (etiquetas estándar + itunes:). */
object RssParser {

    private val htmlTag = Regex("<[^>]+>")
    private val spaces = Regex("\\s+")

    fun parse(input: InputStream): PodcastFeed {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        // Un feed es contenido externo: sin entidades externas ni DTD remotos.
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.isExpandEntityReferences = false }
        val doc = factory.newDocumentBuilder().parse(input)
        val channel = doc.getElementsByTagName("channel").item(0) as? Element
            ?: throw IllegalArgumentException("El enlace no es un feed de podcast (RSS).")

        val channelImage = channel.childElement("itunes:image")?.getAttribute("href")?.takeIf { it.isNotBlank() }
            ?: channel.childElement("image")?.childText("url")
        val title = channel.childText("title") ?: "Podcast"
        val author = channel.childText("itunes:author") ?: channel.childText("managingEditor").orEmpty()

        val episodes = mutableListOf<PodcastEpisode>()
        val items = channel.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val enclosure = item.childElement("enclosure") ?: continue
            val url = enclosure.getAttribute("url").trim()
            if (url.isBlank()) continue
            val type = enclosure.getAttribute("type").lowercase()
            if (type.isNotBlank() && !type.startsWith("audio") && !type.contains("mpeg") && !type.contains("ogg")) continue
            val guid = item.childText("guid")?.takeIf { it.isNotBlank() } ?: url
            episodes += PodcastEpisode(
                id = shortHash(guid),
                title = item.childText("title") ?: "Episodio",
                audioUrl = url,
                publishedAt = parseDate(item.childText("pubDate")),
                durationMs = parseDuration(item.childText("itunes:duration")),
                imageUrl = item.childElement("itunes:image")?.getAttribute("href")?.takeIf { it.isNotBlank() } ?: channelImage,
                description = cleanText(item.childText("itunes:summary") ?: item.childText("description").orEmpty()).take(600),
            )
        }
        return PodcastFeed(
            title = title,
            author = author,
            description = cleanText(channel.childText("description").orEmpty()).take(800),
            imageUrl = channelImage,
            episodes = episodes,
        )
    }

    /** "1:02:03", "45:10" o "3723" (segundos) → milisegundos. */
    fun parseDuration(text: String?): Long {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return 0L
        val parts = t.split(':').map { it.trim().toDoubleOrNull() ?: return 0L }
        val seconds = when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return 0L
        }
        return (seconds * 1000).toLong()
    }

    fun parseDate(text: String?): Long {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ssZ",
        )
        for (p in patterns) {
            try {
                return SimpleDateFormat(p, Locale.US).parse(t)?.time ?: continue
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    fun shortHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    fun cleanText(text: String): String =
        text.replace(htmlTag, " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace(spaces, " ").trim()

    private fun Element.childElement(name: String): Element? {
        var n: Node? = firstChild
        while (n != null) {
            if (n is Element && n.tagName.equals(name, ignoreCase = true)) return n
            n = n.nextSibling
        }
        return null
    }

    private fun Element.childText(name: String): String? =
        childElement(name)?.textContent?.trim()?.takeIf { it.isNotBlank() }
}
