package app.mimusica.domain.links

import java.net.URLEncoder

/** Un servicio externo donde escuchar o comprar una canción. */
data class ExternalService(val id: String, val name: String, val kind: Kind, val url: String) {
    enum class Kind { LISTEN, BUY }
}

/**
 * Enlaces de búsqueda en servicios legales. La app no reproduce ni descarga nada de ellos:
 * solo abre su app o su página con la canción ya buscada. Sin dependencias de Android (se prueba en JVM).
 */
object ExternalLinks {

    /** Texto de búsqueda a partir de título y artista (sin "Artista desconocido"). */
    fun query(title: String, artist: String?): String =
        listOfNotNull(
            artist?.takeIf { it.isNotBlank() && it != "Artista desconocido" }?.substringBefore(',')?.trim(),
            title.trim().takeIf { it.isNotBlank() },
        ).joinToString(" ")

    private fun encode(text: String): String = URLEncoder.encode(text.trim(), "UTF-8").replace("+", "%20")

    fun services(query: String): List<ExternalService> {
        val e = encode(query)
        return listOf(
            ExternalService("spotify", "Spotify", ExternalService.Kind.LISTEN, "https://open.spotify.com/search/$e"),
            ExternalService("ytmusic", "YouTube Music", ExternalService.Kind.LISTEN, "https://music.youtube.com/search?q=$e"),
            ExternalService("youtube", "YouTube", ExternalService.Kind.LISTEN, "https://www.youtube.com/results?search_query=$e"),
            ExternalService("deezer", "Deezer", ExternalService.Kind.LISTEN, "https://www.deezer.com/search/$e"),
            ExternalService("amazonmusic", "Amazon Music", ExternalService.Kind.LISTEN, "https://music.amazon.com/search/$e"),
            ExternalService("bandcamp", "Bandcamp", ExternalService.Kind.BUY, "https://bandcamp.com/search?q=$e"),
            ExternalService("amazonmp3", "Amazon (MP3)", ExternalService.Kind.BUY, "https://www.amazon.com/s?k=$e&i=digital-music"),
            ExternalService("tiendas", "Otras tiendas", ExternalService.Kind.BUY, "https://www.google.com/search?q=$e%20comprar%20descargar%20mp3"),
        )
    }
}
