package app.mimusica.domain.lyrics

/** Una línea de letra con su instante de inicio. */
data class LyricLine(val timeMs: Long, val text: String)

/** Parser del formato LRC: `[mm:ss.xx] texto` (admite varias marcas por línea). */
object LrcParser {

    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val stamps = mutableListOf<Long>()
            var rest = line
            while (true) {
                val m = stamp.find(rest) ?: break
                if (m.range.first != 0) break
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracText = m.groupValues[3]
                val frac = when (fracText.length) {
                    0 -> 0L
                    1 -> fracText.toLong() * 100
                    2 -> fracText.toLong() * 10
                    else -> fracText.take(3).toLong()
                }
                stamps.add(min * 60_000 + sec * 1000 + frac)
                rest = rest.substring(m.range.last + 1).trimStart()
            }
            if (stamps.isEmpty()) continue // etiquetas como [ar:...] o líneas sin marca
            for (t in stamps) out.add(LyricLine(t, rest))
        }
        return out.sortedBy { it.timeMs }
    }

    /** Índice de la línea que suena en [positionMs] (-1 si aún no empieza la primera). */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else hi = mid - 1
        }
        return ans
    }
}
