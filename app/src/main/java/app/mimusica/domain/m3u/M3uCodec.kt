package app.mimusica.domain.m3u

import java.net.URLDecoder

data class M3uEntry(
    val location: String,
    val title: String? = null,
    val artist: String? = null,
    val durationSec: Long = -1,
)

data class M3uPlaylist(val name: String?, val entries: List<M3uEntry>)

/** Lectura y escritura de listas .m3u / .m3u8 (formato extendido). */
object M3uCodec {

    private val extInf = Regex("""^#EXTINF:\s*(-?\d+)[^,]*,(.*)$""")
    private val iaUrl = Regex("""^https?://(?:www\.)?archive\.org/download/([^/?#]+)/([^?#]+)""")
    private val jamendoTrackId = Regex("""[?&]trackid=(\d+)""")
    private val jamendoTrackPage = Regex("""jamendo\.com/track/(\d+)""")
    private val mediaStoreUri = Regex("""^content://media/[^/]+/audio/media/(\d+)""")

    fun encode(name: String, entries: List<M3uEntry>): String = buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:").append(name.replace('\n', ' ')).append('\n')
        for (e in entries) {
            val label = listOfNotNull(e.artist?.takeIf { it.isNotBlank() }, e.title?.takeIf { it.isNotBlank() })
                .joinToString(" - ")
                .replace('\n', ' ')
            append("#EXTINF:").append(e.durationSec).append(',').append(label).append('\n')
            append(e.location).append('\n')
        }
    }

    fun decode(content: String): M3uPlaylist {
        var name: String? = null
        val entries = mutableListOf<M3uEntry>()
        var pendingDuration = -1L
        var pendingTitle: String? = null
        var pendingArtist: String? = null
        for (raw in content.removePrefix("﻿").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#PLAYLIST:", ignoreCase = true) ->
                    name = line.substringAfter(':').trim().ifBlank { null }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val m = extInf.find(line)
                    if (m != null) {
                        pendingDuration = m.groupValues[1].toLongOrNull() ?: -1
                        val label = m.groupValues[2].trim()
                        val sep = label.indexOf(" - ")
                        if (sep > 0) {
                            pendingArtist = label.substring(0, sep).trim()
                            pendingTitle = label.substring(sep + 3).trim()
                        } else {
                            pendingArtist = null
                            pendingTitle = label.ifBlank { null }
                        }
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    entries.add(M3uEntry(line, pendingTitle, pendingArtist, pendingDuration))
                    pendingDuration = -1; pendingTitle = null; pendingArtist = null
                }
            }
        }
        return M3uPlaylist(name, entries)
    }

    /** Intenta reconstruir la clave de pista (trackKey) a partir de la ubicación guardada. */
    fun keyFromLocation(location: String): String? {
        mediaStoreUri.find(location)?.let { return "local:${it.groupValues[1]}" }
        iaUrl.find(location)?.let {
            val file = URLDecoder.decode(it.groupValues[2].replace("+", "%2B"), "UTF-8")
            return "ia:${it.groupValues[1]}/$file"
        }
        if (location.contains("jamendo.com")) {
            jamendoTrackId.find(location)?.let { return "jam:${it.groupValues[1]}" }
            jamendoTrackPage.find(location)?.let { return "jam:${it.groupValues[1]}" }
        }
        return null
    }

    /** Nombre del archivo sin extensión (para emparejar rutas de otras apps). */
    fun baseName(location: String): String {
        val decoded = try { URLDecoder.decode(location.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { location }
        return decoded.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
    }
}
