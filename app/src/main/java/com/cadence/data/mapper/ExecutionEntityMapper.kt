package com.cadence.data.mapper

import com.cadence.data.local.db.entity.ActionOutcomeEntity
import com.cadence.data.local.db.entity.ExecutionEntity
import com.cadence.data.local.db.entity.ExecutionWithOutcomes
import com.cadence.data.serialization.ConfigJsonCodec
import com.cadence.domain.model.ActionOutcome
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.MacroExecution
import com.cadence.domain.model.OutcomeStatus
import java.time.Instant
import javax.inject.Inject

/** Maps between [MacroExecution] and its Room rows. */
class ExecutionEntityMapper @Inject constructor(private val codec: ConfigJsonCodec) {

    /** The execution row plus its outcome rows (execution ids are set on insert). */
    fun toEntities(execution: MacroExecution): Pair<ExecutionEntity, List<ActionOutcomeEntity>> {
        val entity = ExecutionEntity(
            id = execution.id,
            macroId = execution.macroId,
            macroName = execution.macroName,
            triggerJson = codec.encodeTrigger(execution.trigger),
            triggerDataJson = codec.encodeStringMap(execution.triggerData),
            startedAt = execution.startedAt.toEpochMilli(),
            finishedAt = execution.finishedAt.toEpochMilli(),
            status = execution.status.name,
            failedConstraintJson = execution.failedConstraint?.let(codec::encodeConstraint),
        )
        val outcomes = execution.actionOutcomes.map { outcome ->
            ActionOutcomeEntity(
                position = outcome.index,
                actionJson = codec.encodeAction(outcome.action),
                status = outcome.status.name,
                message = outcome.message,
                durationMillis = outcome.durationMillis,
            )
        }
        return entity to outcomes
    }

    /**
     * Returns `null` if the row can't be read by this app version (e.g. after a downgrade).
     * Unreadable outcomes are dropped individually.
     */
    fun toDomain(row: ExecutionWithOutcomes): MacroExecution? {
        val execution = row.execution
        val trigger = codec.decodeTrigger(execution.triggerJson) ?: return null
        val status = enumValueOrNull<ExecutionStatus>(execution.status) ?: return null
        return MacroExecution(
            id = execution.id,
            macroId = execution.macroId,
            macroName = execution.macroName,
            trigger = trigger,
            triggerData = codec.decodeStringMap(execution.triggerDataJson),
            startedAt = Instant.ofEpochMilli(execution.startedAt),
            finishedAt = Instant.ofEpochMilli(execution.finishedAt),
            status = status,
            failedConstraint = execution.failedConstraintJson?.let(codec::decodeConstraint),
            actionOutcomes = row.outcomes
                .sortedBy { it.position }
                .mapNotNull { outcome ->
                    val action = codec.decodeAction(outcome.actionJson) ?: return@mapNotNull null
                    val outcomeStatus = enumValueOrNull<OutcomeStatus>(outcome.status) ?: return@mapNotNull null
                    ActionOutcome(
                        index = outcome.position,
                        action = action,
                        status = outcomeStatus,
                        message = outcome.message,
                        durationMillis = outcome.durationMillis,
                    )
                },
        )
    }

    private inline fun <reified E : Enum<E>> enumValueOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
