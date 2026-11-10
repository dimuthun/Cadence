package com.cadence.domain.model

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.trigger.TriggerConfig
import java.time.Instant

/**
 * A user-defined automation: when any of [triggers] fires and all [constraints] hold,
 * [actions] run in order.
 *
 * @property id database id; `0` for a macro that has not been saved yet.
 * @property enabled disabled macros keep their configuration but register no triggers.
 * @property category free-form grouping label used by the Home screen filter.
 * @property unsupportedItemCount number of stored triggers, actions, or constraints this app
 * version could not read (e.g. data restored from a newer version). Such items are missing from
 * the lists above, so the macro is never run, see [isRunnable].
 */
data class Macro(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val category: String = DEFAULT_CATEGORY,
    val triggers: List<TriggerConfig>,
    val actions: List<ActionConfig>,
    val constraints: List<ConstraintConfig> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val unsupportedItemCount: Int = 0,
) {
    /**
     * `true` when the engine may register this macro's triggers. A macro with unreadable items
     * could be missing a constraint, so running it might do something the user never intended.
     */
    val isRunnable: Boolean get() = enabled && unsupportedItemCount == 0

    companion object {
        const val DEFAULT_CATEGORY = "General"
    }
}
