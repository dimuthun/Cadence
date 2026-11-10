package com.cadence.domain.variable

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Minimal JSON path for picking one value out of an HTTP response: dot-separated keys with
 * optional array indexes, e.g. `data.items[0].name` or `data.items.0.name`.
 */
object JsonPath {

    /**
     * The value at [path] in [json]: primitives as plain text, objects and arrays as JSON.
     * Returns `null` if the body isn't JSON or the path doesn't exist.
     */
    fun extract(json: String, path: String): String? {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: IllegalArgumentException) {
            return null
        }
        var node: JsonElement = root
        for (segment in segments(path)) {
            node = when (val current = node) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> segment.toIntOrNull()?.let(current::getOrNull) ?: return null
                else -> return null
            }
        }
        return when (val value = node) {
            is JsonNull -> ""
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
    }

    private fun segments(path: String): List<String> =
        path.trim()
            .replace("[", ".")
            .replace("]", "")
            .split('.')
            .filter { it.isNotEmpty() }
}
