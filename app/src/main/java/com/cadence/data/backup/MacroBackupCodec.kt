package com.cadence.data.backup

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.Macro
import com.cadence.domain.trigger.TriggerConfig
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Encodes macros into the export file format and back.
 *
 * ```
 * { "format": "cadence.macros", "version": 1, "exportedAtMillis": ..., "macros": [ ... ] }
 * ```
 * Macros are decoded one by one, so a macro using a type this version doesn't know is skipped
 * instead of failing the whole import.
 */
class MacroBackupCodec @Inject constructor(private val json: Json) {

    /** Result of [decode]. */
    sealed interface DecodeResult {
        /** [macros] have `id = 0`; [skipped] counts unreadable entries. */
        data class Decoded(val macros: List<Macro>, val skipped: Int) : DecodeResult
        data class Invalid(val reason: String) : DecodeResult
    }

    fun encode(macros: List<Macro>, exportedAt: Instant): String {
        val file = BackupFile(
            format = FORMAT,
            version = VERSION,
            exportedAtMillis = exportedAt.toEpochMilli(),
            macros = macros.map { json.encodeToJsonElement(BackupMacro.serializer(), it.toBackup()) },
        )
        return prettyJson.encodeToString(BackupFile.serializer(), file)
    }

    fun decode(document: String): DecodeResult {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), document)
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Invalid("This file isn't a Cadence backup.")
        }
        if (file.format != FORMAT) return DecodeResult.Invalid("This file isn't a Cadence backup.")
        if (file.version > VERSION) {
            return DecodeResult.Invalid("This backup was made by a newer version of Cadence. Update the app and try again.")
        }

        var skipped = 0
        val macros = file.macros.mapNotNull { element ->
            try {
                json.decodeFromJsonElement(BackupMacro.serializer(), element).toDomain()
            } catch (e: IllegalArgumentException) {
                skipped++
                null
            }
        }
        return DecodeResult.Decoded(macros, skipped)
    }

    private val prettyJson = Json(json) { prettyPrint = true }

    @Serializable
    private data class BackupFile(
        val format: String,
        val version: Int,
        val exportedAtMillis: Long,
        val macros: List<JsonElement>,
    )

    @Serializable
    private data class BackupMacro(
        val name: String,
        val enabled: Boolean,
        val category: String = Macro.DEFAULT_CATEGORY,
        val triggers: List<TriggerConfig>,
        val actions: List<ActionConfig>,
        val constraints: List<ConstraintConfig> = emptyList(),
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
    ) {
        fun toDomain() = Macro(
            name = name,
            enabled = enabled,
            category = category,
            triggers = triggers,
            actions = actions,
            constraints = constraints,
            createdAt = Instant.ofEpochMilli(createdAtMillis),
            updatedAt = Instant.ofEpochMilli(updatedAtMillis),
        )
    }

    private fun Macro.toBackup() = BackupMacro(
        name = name,
        enabled = enabled,
        category = category,
        triggers = triggers,
        actions = actions,
        constraints = constraints,
        createdAtMillis = createdAt.toEpochMilli(),
        updatedAtMillis = updatedAt.toEpochMilli(),
    )

    companion object {
        const val FORMAT = "cadence.macros"
        const val VERSION = 1
    }
}
