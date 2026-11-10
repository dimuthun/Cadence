package com.cadence.data.mapper

import com.cadence.data.local.db.entity.MacroEntity
import com.cadence.data.serialization.ConfigJsonCodec
import com.cadence.domain.model.Macro
import java.time.Instant
import javax.inject.Inject

/** Maps between [Macro] and its Room row. */
class MacroEntityMapper @Inject constructor(private val codec: ConfigJsonCodec) {

    fun toEntity(macro: Macro): MacroEntity = MacroEntity(
        id = macro.id,
        name = macro.name,
        enabled = macro.enabled,
        category = macro.category,
        triggersJson = codec.encodeTriggers(macro.triggers),
        actionsJson = codec.encodeActions(macro.actions),
        constraintsJson = codec.encodeConstraints(macro.constraints),
        createdAt = macro.createdAt.toEpochMilli(),
        updatedAt = macro.updatedAt.toEpochMilli(),
    )

    /** Never throws. Unreadable items are dropped and counted in [Macro.unsupportedItemCount]. */
    fun toDomain(entity: MacroEntity): Macro {
        val triggers = codec.decodeTriggers(entity.triggersJson)
        val actions = codec.decodeActions(entity.actionsJson)
        val constraints = codec.decodeConstraints(entity.constraintsJson)
        return Macro(
            id = entity.id,
            name = entity.name,
            enabled = entity.enabled,
            category = entity.category,
            triggers = triggers.items,
            actions = actions.items,
            constraints = constraints.items,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt),
            unsupportedItemCount = triggers.failures + actions.failures + constraints.failures,
        )
    }
}
