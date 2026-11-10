package com.cadence.data.serialization

import kotlinx.serialization.json.Json

/**
 * The single [Json] configuration used for configs stored in Room and for export files.
 *
 * - `classDiscriminator = "type"`: each config is written as `{"type":"trigger.wifi_connection",...}`
 *   using the stable `@SerialName`s on the sealed hierarchies. No `SerializersModule` is needed
 *   because sealed hierarchies register their subtypes automatically.
 * - `encodeDefaults = true`: stored data keeps its meaning even if a default changes later.
 * - `ignoreUnknownKeys = true`: data written by a newer app version with extra fields still decodes.
 */
object CadenceJson {
    val instance: Json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
}
