package app.mimusica.domain.matching

import app.mimusica.domain.model.Track
import kotlin.math.max
import kotlin.math.min

/** Resultado de un emparejamiento. */
data class MatchResult(val track: Track, val score: Double)

/**
 * Empareja (título, artista) contra una biblioteca de pistas usando
 * normalización + coincidencia difusa (Levenshtein y tokens).
 */
class TrackMatcher(library: List<Track>, private val threshold: Double = DEFAULT_THRESHOLD) {

    private class Entry(val track: Track, val title: String, val artists: List<String>, val tokens: Set<String>)

    private val entries: List<Entry> = library.map { t ->
        val title = TextNormalizer.title(t.title)
        Entry(t, title, TextNormalizer.artists(t.artist), title.split(' ').filter { it.isNotBlank() }.toSet())
    }
    private val byTitle: Map<String, List<Entry>> = entries.groupBy { it.title }

    fun findBest(title: String, artist: String): MatchResult? {
        val qTitle = TextNormalizer.title(title)
        if (qTitle.isBlank()) return null
        val qArtists = TextNormalizer.artists(artist)
        val qTokens = qTitle.split(' ').filter { it.isNotBlank() }.toSet()

        // 1) Coincidencia exacta del título normalizado.
        byTitle[qTitle]?.let { exact ->
            val best = exact.maxByOrNull { artistSimilarity(qArtists, it.artists) }!!
            val aSim = artistSimilarity(qArtists, best.artists)
            val score = combine(1.0, aSim, qArtists.isEmpty() || best.artists.isEmpty())
            if (score >= threshold) return MatchResult(best.track, score)
        }

        // 2) Coincidencia difusa: solo candidatos que compartan algún token o tengan longitud parecida.
        var bestResult: MatchResult? = null
        for (e in entries) {
            val sharesToken = e.tokens.any { it in qTokens }
            val lengthClose = kotlin.math.abs(e.title.length - qTitle.length) <= max(3, qTitle.length / 3)
            if (!sharesToken && !lengthClose) continue
            val tSim = similarity(qTitle, e.title)
            if (tSim < 0.6) continue
            val aSim = artistSimilarity(qArtists, e.artists)
            val score = combine(tSim, aSim, qArtists.isEmpty() || e.artists.isEmpty())
            if (score >= threshold && (bestResult == null || score > bestResult.score)) {
                bestResult = MatchResult(e.track, score)
            }
        }
        return bestResult
    }

    private fun combine(titleSim: Double, artistSim: Double, noArtist: Boolean): Double =
        if (noArtist) titleSim * 0.9 else titleSim * 0.65 + artistSim * 0.35

    companion object {
        const val DEFAULT_THRESHOLD = 0.82

        /** Similitud de artistas: mejor par entre ambas listas. */
        fun artistSimilarity(a: List<String>, b: List<String>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            var best = 0.0
            for (x in a) for (y in b) {
                val s = if (x == y) 1.0 else similarity(x, y)
                if (s > best) best = s
            }
            return best
        }

        /** Similitud 0..1 combinando Levenshtein normalizado y coeficiente de Dice de tokens. */
        fun similarity(a: String, b: String): Double {
            if (a == b) return 1.0
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val lev = 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
            val ta = a.split(' ').filter { it.isNotBlank() }.toSet()
            val tb = b.split(' ').filter { it.isNotBlank() }.toSet()
            val dice = if (ta.isEmpty() || tb.isEmpty()) 0.0
            else 2.0 * ta.intersect(tb).size / (ta.size + tb.size)
            return max(lev, dice)
        }

        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                }
                val tmp = prev; prev = curr; curr = tmp
            }
            return prev[b.length]
        }
    }
}
