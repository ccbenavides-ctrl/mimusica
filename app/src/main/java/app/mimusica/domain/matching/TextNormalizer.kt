package app.mimusica.domain.matching

import java.text.Normalizer

/**
 * Normaliza títulos y artistas para poder compararlos:
 * minúsculas, sin tildes, sin "(Remastered 2011)", "- Live", "feat. X", signos, etc.
 */
object TextNormalizer {

    private val noiseWords = listOf(
        "remaster", "remastered", "remasterizado", "remasterizada", "remix", "live", "en vivo",
        "en directo", "version", "versión", "edit", "mono", "stereo", "deluxe", "acoustic",
        "acústico", "acustico", "bonus", "demo", "explicit", "radio", "single", "original mix",
        "feat", "ft.", "featuring", "with ", "con ", "instrumental", "mix", "edición", "edicion",
        "anniversary", "aniversario",
    )

    private val bracketRegex = Regex("""[\(\[\{]([^\)\]\}]*)[\)\]\}]""")
    private val dashSuffixRegex = Regex("""\s+[-–—]\s+(.*)$""")
    private val featRegex = Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val diacriticsRegex = Regex("""\p{Mn}+""")
    private val nonAlnumRegex = Regex("""[^\p{L}\p{N}]+""")
    private val artistSplitRegex = Regex("""\s*(,|;|&|/|\+|\bfeat\.?|\bft\.?|\bfeaturing\b|\bx\b|\by\b|\band\b)\s*""", RegexOption.IGNORE_CASE)

    /** Minúsculas, sin tildes, solo letras/números separados por un espacio. */
    fun basic(text: String): String {
        val lower = text.lowercase()
            .replace("&", " and ")
            .replace("ß", "ss")
            .replace("ø", "o")
            .replace("æ", "ae")
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val noAccents = diacriticsRegex.replace(decomposed, "")
        return nonAlnumRegex.replace(noAccents, " ").trim()
    }

    private fun isNoise(content: String): Boolean {
        val c = content.lowercase()
        return noiseWords.any { c.contains(it) } || c.trim().matches(Regex("""\d{4}"""))
    }

    /** Normaliza un título quitando el "ruido" típico de versiones. */
    fun title(text: String): String {
        var t = text
        // Quita paréntesis/corchetes con ruido: "(Remastered 2011)", "[Live]", "(feat. X)"
        t = bracketRegex.replace(t) { m -> if (isNoise(m.groupValues[1])) " " else m.value }
        // Quita sufijos con guion: "Song - Remastered 2009", "Song - Live at ..."
        val dash = dashSuffixRegex.find(t)
        if (dash != null && isNoise(dash.groupValues[1])) {
            t = t.substring(0, dash.range.first)
        }
        t = featRegex.replace(t, "")
        val result = basic(t)
        return result.ifBlank { basic(text) }
    }

    /** Lista de artistas normalizados ("A, B feat. C" -> [a, b, c]). */
    fun artists(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.split(artistSplitRegex)
            .map { basic(it) }
            .map { it.removePrefix("the ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** Artista principal normalizado. */
    fun artist(text: String): String = artists(text).firstOrNull() ?: ""
}
