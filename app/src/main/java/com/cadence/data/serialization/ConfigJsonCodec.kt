package com.cadence.data.serialization

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.trigger.TriggerConfig
import javax.inject.Inject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * Encodes and decodes trigger, action, and constraint configs for storage.
 *
 * Lists are decoded one element at a time, so one unreadable item (e.g. a type added by a newer
 * app version) doesn't lose the rest. Decode failures are counted in [Decoded.failures] instead
 * of thrown.
 */
class ConfigJsonCodec @Inject constructor(private val json: Json) {

    /** Result of decoding a stored list. */
    data class Decoded<T>(val items: List<T>, val failures: Int)

    fun encodeTriggers(items: List<TriggerConfig>): String = encodeList(TriggerConfig.serializer(), items)
    fun encodeActions(items: List<ActionConfig>): String = encodeList(ActionConfig.serializer(), items)
    fun encodeConstraints(items: List<ConstraintConfig>): String = encodeList(ConstraintConfig.serializer(), items)

    fun decodeTriggers(raw: String): Decoded<TriggerConfig> = decodeList(TriggerConfig.serializer(), raw)
    fun decodeActions(raw: String): Decoded<ActionConfig> = decodeList(ActionConfig.serializer(), raw)
    fun decodeConstraints(raw: String): Decoded<ConstraintConfig> = decodeList(ConstraintConfig.serializer(), raw)

    fun encodeTrigger(item: TriggerConfig): String = json.encodeToString(TriggerConfig.serializer(), item)
    fun encodeAction(item: ActionConfig): String = json.encodeToString(ActionConfig.serializer(), item)
    fun encodeConstraint(item: ConstraintConfig): String = json.encodeToString(ConstraintConfig.serializer(), item)

    /** Returns `null` when [raw] can't be decoded. */
    fun decodeTrigger(raw: String): TriggerConfig? = decodeOrNull(TriggerConfig.serializer(), raw)
    fun decodeAction(raw: String): ActionConfig? = decodeOrNull(ActionConfig.serializer(), raw)
    fun decodeConstraint(raw: String): ConstraintConfig? = decodeOrNull(ConstraintConfig.serializer(), raw)

    fun encodeStringMap(map: Map<String, String>): String = json.encodeToString(stringMapSerializer, map)

    /** Returns an empty map when [raw] can't be decoded. */
    fun decodeStringMap(raw: String): Map<String, String> = decodeOrNull(stringMapSerializer, raw) ?: emptyMap()

    private fun <T> encodeList(serializer: KSerializer<T>, items: List<T>): String =
        json.encodeToString(ListSerializer(serializer), items)

    private fun <T> decodeList(serializer: KSerializer<T>, raw: String): Decoded<T> {
        val array = try {
            json.parseToJsonElement(raw) as? JsonArray
        } catch (e: IllegalArgumentException) {
            null
        } ?: return Decoded(emptyList(), failures = 1)

        var failures = 0
        val items = array.mapNotNull { element ->
            try {
                json.decodeFromJsonElement(serializer, element)
            } catch (e: IllegalArgumentException) {
                // SerializationException (unknown type, bad shape) and failed `require` checks.
                failures++
                null
            }
        }
        return Decoded(items, failures)
    }

    private fun <T> decodeOrNull(serializer: KSerializer<T>, raw: String): T? =
        try {
            json.decodeFromString(serializer, raw)
        } catch (e: IllegalArgumentException) {
            null
        }

    private companion object {
        val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
