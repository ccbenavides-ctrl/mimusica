package app.mimusica.playback

import android.content.Context
import app.mimusica.domain.model.Track
import app.mimusica.domain.model.TrackJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

/** Cola guardada: lo que estaba sonando, para retomarlo al volver a abrir la app. */
data class SavedQueue(
    /** Pista + URI real de reproducción (puede ser la copia descargada). */
    val items: List<Pair<Track, String>>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

class QueueStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "cola.json")

    fun save(queue: SavedQueue) {
        runCatching {
            if (queue.items.isEmpty()) {
                file.delete()
                return
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(encode(queue), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    fun load(): SavedQueue? = runCatching {
        if (!file.exists()) null else decode(file.readText(Charsets.UTF_8))
    }.getOrNull()

    companion object {
        const val MAX_ITEMS = 1000

        fun encode(queue: SavedQueue): String {
            // Si la cola es enorme se guarda una ventana alrededor de la pista actual.
            val from = if (queue.items.size > MAX_ITEMS) (queue.index - MAX_ITEMS / 2).coerceIn(0, queue.items.size - MAX_ITEMS) else 0
            val window = queue.items.drop(from).take(MAX_ITEMS)
            return buildJsonObject {
                put("index", (queue.index - from).coerceAtLeast(0))
                put("positionMs", queue.positionMs)
                put("shuffle", queue.shuffle)
                put("repeat", queue.repeatMode)
                put("items", buildJsonArray {
                    for ((track, playUri) in window) {
                        add(JsonObject(TrackJson.toJson(track) + ("playUri" to JsonPrimitive(playUri))))
                    }
                })
            }.toString()
        }

        fun decode(text: String): SavedQueue? {
            val root = Json.parseToJsonElement(text).jsonObject
            val items = (root["items"] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val track = TrackJson.fromJson(o) ?: return@mapNotNull null
                val playUri = (o["playUri"] as? JsonPrimitive)?.content ?: track.uri
                track to playUri
            }
            if (items.isEmpty()) return null
            fun long(name: String) = (root[name] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            return SavedQueue(
                items = items,
                index = long("index").toInt().coerceIn(0, items.lastIndex),
                positionMs = long("positionMs").coerceAtLeast(0),
                shuffle = (root["shuffle"] as? JsonPrimitive)?.content == "true",
                repeatMode = long("repeat").toInt().coerceIn(0, 2),
            )
        }
    }
}
