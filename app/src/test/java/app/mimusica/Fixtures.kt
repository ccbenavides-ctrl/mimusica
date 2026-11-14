package app.mimusica

import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackSource

object Fixtures {
    fun read(name: String): String {
        val stream = Fixtures::class.java.classLoader!!.getResourceAsStream("fixtures/$name")
            ?: error("No se encontró el fixture $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun local(id: Long, title: String, artist: String, album: String = "Álbum") = Track(
        key = Track.localKey(id),
        title = title,
        artist = artist,
        album = album,
        durationMs = 200_000,
        uri = "content://media/external/audio/media/$id",
        artworkUri = null,
        source = TrackSource.LOCAL,
    )
}
