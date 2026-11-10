package com.cadence.domain.constraint

import com.cadence.domain.model.Weekday
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persisted configuration of a constraint: a condition that must hold for a macro's actions to
 * run. A macro runs only when all of its constraints hold. Phase 2 can add `AnyOf`/`Not`
 * subtypes for grouping without changing the engine.
 *
 * Evaluation lives in a matching [com.cadence.domain.plugin.ConstraintEvaluator].
 * Every [SerialName] is part of the storage and export format. Never change an existing one.
 */
@Serializable
sealed interface ConstraintConfig {

    /** Constraints evaluated by the engine itself, because they need other constraints or run state. */
    @Serializable
    sealed interface EngineConstraint : ConstraintConfig

    /** Combines [constraints]: all must hold, any may hold, or none may hold. */
    @Serializable
    @SerialName("constraint.group")
    data class Group(
        val operator: Operator = Operator.ANY,
        val constraints: List<ConstraintConfig> = emptyList(),
    ) : EngineConstraint {
        @Serializable
        enum class Operator { ALL, ANY, NONE }
    }

    /** Holds when [macroId] is (or isn't) currently running. */
    @Serializable
    @SerialName("constraint.macro_running")
    data class MacroRunning(val macroId: Long, val running: Boolean = true) : EngineConstraint

    /** Holds when [macroId] is (or isn't) enabled. */
    @Serializable
    @SerialName("constraint.macro_enabled")
    data class MacroEnabled(val macroId: Long, val enabled: Boolean = true) : ConstraintConfig

    /**
     * Holds when the macro last ran at least [minutes] ago (or never ran): a cooldown.
     *
     * @property macroId the macro to check, or `null` for the macro being evaluated.
     */
    @Serializable
    @SerialName("constraint.time_since_last_run")
    data class TimeSinceLastRun(val minutes: Int = 10, val macroId: Long? = null) : ConstraintConfig {
        init {
            require(minutes in 1..MAX_MINUTES) { "minutes must be 1..$MAX_MINUTES, was $minutes" }
        }

        companion object {
            const val MAX_MINUTES = 31 * 24 * 60
        }
    }

    /**
     * Holds when local time is within [startMinuteOfDay] (inclusive) and [endMinuteOfDay]
     * (exclusive). The range may wrap past midnight (e.g. 22:00 to 06:00). Equal bounds mean
     * the whole day.
     */
    @Serializable
    @SerialName("constraint.time_range")
    data class TimeRange(
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
    ) : ConstraintConfig {
        init {
            require(startMinuteOfDay in 0 until MINUTES_PER_DAY) { "startMinuteOfDay out of range: $startMinuteOfDay" }
            require(endMinuteOfDay in 0 until MINUTES_PER_DAY) { "endMinuteOfDay out of range: $endMinuteOfDay" }
        }

        companion object {
            const val MINUTES_PER_DAY = 24 * 60
        }
    }

    /** Holds when the battery level is within [minPercent]..[maxPercent] inclusive. */
    @Serializable
    @SerialName("constraint.battery_level_range")
    data class BatteryLevelRange(
        val minPercent: Int,
        val maxPercent: Int,
    ) : ConstraintConfig {
        init {
            require(minPercent in 0..100 && maxPercent in 0..100) { "percent values must be 0..100" }
            require(minPercent <= maxPercent) { "minPercent must be <= maxPercent" }
        }
    }

    /** Holds when the device's charging state equals [charging]. */
    @Serializable
    @SerialName("constraint.charging_state")
    data class ChargingState(val charging: Boolean) : ConstraintConfig

    /**
     * Holds when the device's Wi-Fi connection state equals [connected] (any network).
     * Specific SSIDs are unsupported for the same reason as
     * [com.cadence.domain.trigger.TriggerConfig.WifiConnection].
     */
    @Serializable
    @SerialName("constraint.wifi_network")
    data class WifiNetwork(val connected: Boolean = true) : ConstraintConfig

    /** Holds when today is one of [days]. */
    @Serializable
    @SerialName("constraint.days_of_week")
    data class DaysOfWeek(val days: Set<Weekday>) : ConstraintConfig {
        init {
            require(days.isNotEmpty()) { "days must not be empty" }
        }
    }

    /**
     * Compares a variable (user or built-in, e.g. `battery.level`) with [value], which may contain
     * `{placeholders}`. Comparisons are numeric when both sides are numbers, otherwise
     * case-insensitive text.
     */
    @Serializable
    @SerialName("constraint.variable")
    data class VariableCompare(
        val name: String,
        val operator: Operator = Operator.EQUALS,
        val value: String = "",
    ) : ConstraintConfig {
        init {
            require(name.isEmpty() || com.cadence.domain.variable.VariableNames.isValidReference(name)) {
                "Invalid variable name: $name"
            }
        }

        @Serializable
        enum class Operator { EQUALS, NOT_EQUALS, CONTAINS, GREATER_THAN, LESS_THAN, IS_SET, IS_NOT_SET }
    }
}
