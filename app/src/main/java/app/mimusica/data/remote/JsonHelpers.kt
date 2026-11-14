package app.mimusica.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Lee un campo que puede venir como texto, número o arreglo (Internet Archive mezcla ambos). */
internal fun JsonObject.text(name: String): String? = this[name].asText()

internal fun JsonElement?.asText(): String? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> content.takeIf { it.isNotBlank() }
    is JsonArray -> mapNotNull { it.asText() }.joinToString(", ").takeIf { it.isNotBlank() }
    is JsonObject -> null
}

internal fun JsonObject.textList(name: String): List<String> = when (val e = this[name]) {
    null, is JsonNull -> emptyList()
    is JsonPrimitive -> listOfNotNull(e.content.takeIf { it.isNotBlank() })
    is JsonArray -> e.mapNotNull { it.asText() }
    is JsonObject -> emptyList()
}

internal fun JsonObject.bool(name: String): Boolean = when (val e = this[name]) {
    is JsonPrimitive -> e.content.equals("true", ignoreCase = true) || e.content == "1"
    else -> false
}

internal fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()

internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

internal fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

/** Error de la API remota con un mensaje listo para mostrar en español. */
class RemoteApiException(message: String) : Exception(message)
