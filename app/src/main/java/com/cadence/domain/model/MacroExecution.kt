package com.cadence.domain.model

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.trigger.TriggerConfig
import java.time.Instant

/**
 * One entry in the macro log: a single trigger firing and what happened as a result.
 *
 * @property macroName copied at execution time so the log stays readable after renames or deletion.
 * @property trigger the trigger configuration that fired.
 * @property triggerData event details reported by the trigger (e.g. the SSID or package name).
 * @property failedConstraint the first constraint that did not hold, when
 * [status] is [ExecutionStatus.CONSTRAINTS_NOT_MET].
 */
data class MacroExecution(
    val id: Long = 0,
    val macroId: Long,
    val macroName: String,
    val trigger: TriggerConfig,
    val triggerData: Map<String, String> = emptyMap(),
    val startedAt: Instant,
    val finishedAt: Instant,
    val status: ExecutionStatus,
    val failedConstraint: ConstraintConfig? = null,
    val actionOutcomes: List<ActionOutcome> = emptyList(),
)

/** Overall result of a [MacroExecution]. */
enum class ExecutionStatus {
    /** Every action succeeded (fallbacks count as success). */
    SUCCESS,

    /** At least one action failed and at least one did not. */
    PARTIAL_FAILURE,

    /** Every action failed. */
    FAILURE,

    /** A constraint did not hold, so no action ran. */
    CONSTRAINTS_NOT_MET,

    /** The run was stopped early, e.g. the macro was disabled during a wait. */
    CANCELLED,
}

/**
 * Result of one action within a run.
 *
 * @property index position of the action in execution order.
 * @property message success detail, fallback explanation, or failure reason.
 */
data class ActionOutcome(
    val index: Int,
    val action: ActionConfig,
    val status: OutcomeStatus,
    val message: String?,
    val durationMillis: Long,
)

/** Per-action status stored in the log. */
enum class OutcomeStatus {
    SUCCESS,

    /** Android blocked the direct action, so a fallback ran instead (e.g. a settings panel opened). */
    FALLBACK,
    FAILURE,
}
