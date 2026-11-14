package app.mimusica.domain.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Serialización de [Track] a JSON (cola guardada y copia de seguridad). Sin dependencias de Android. */
object TrackJson {

    fun toJson(t: Track): JsonObject = buildJsonObject {
        put("key", t.key)
        put("title", t.title)
        put("artist", t.artist)
        put("album", t.album)
        put("durationMs", t.durationMs)
        put("uri", t.uri)
        t.artworkUri?.let { put("artworkUri", it) }
        put("source", t.source.name)
        t.license?.let { put("license", it) }
        t.downloadUrl?.let { put("downloadUrl", it) }
        t.infoUrl?.let { put("infoUrl", it) }
        if (t.genre.isNotBlank()) put("genre", t.genre)
    }

    fun fromJson(o: JsonObject): Track? {
        val key = o.str("key") ?: return null
        val uri = o.str("uri") ?: return null
        return Track(
            key = key,
            title = o.str("title") ?: "Sin título",
            artist = o.str("artist") ?: Track.UNKNOWN_ARTIST,
            album = o.str("album").orEmpty(),
            durationMs = o.num("durationMs"),
            uri = uri,
            artworkUri = o.str("artworkUri"),
            source = TrackSource.fromName(o.str("source") ?: Track.sourceOfKey(key).name),
            license = o.str("license"),
            downloadUrl = o.str("downloadUrl"),
            infoUrl = o.str("infoUrl"),
            genre = o.str("genre").orEmpty(),
        )
    }

    private fun JsonObject.str(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.num(name: String): Long =
        (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: 0L
}
