package com.cadence.domain.action

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persisted configuration of an action: something a macro does.
 *
 * Device actions are executed by a matching [com.cadence.domain.plugin.ActionHandler].
 * [ControlFlowAction]s are interpreted by the engine itself.
 *
 * Every [SerialName] is part of the storage and export format. Never change an existing one.
 */
@Serializable
sealed interface ActionConfig {

    /**
     * Actions that shape execution instead of touching the device. The engine runs them directly.
     * Blocks are nested (an [IfElse] holds its branches' actions), so there are no separate
     * "end if" markers that could get out of balance.
     */
    @Serializable
    sealed interface ControlFlowAction : ActionConfig

    /** Pauses the macro for [durationMillis] before the next action. */
    @Serializable
    @SerialName("action.wait")
    data class Wait(val durationMillis: Long) : ControlFlowAction {
        init {
            require(durationMillis >= 0) { "durationMillis must be >= 0, was $durationMillis" }
        }
    }

    /**
     * Runs the actions of the first branch whose conditions hold (if, else if, …), or
     * [elseActions] when none does.
     */
    @Serializable
    @SerialName("action.if")
    data class IfElse(
        val branches: List<ConditionalBlock>,
        val elseActions: List<ActionConfig> = emptyList(),
    ) : ControlFlowAction {
        init {
            require(branches.isNotEmpty()) { "An if needs at least one branch" }
        }
    }

    /**
     * Repeats [actions]. `{loop.index}` (1-based) is available inside the body.
     *
     * @property count iterations for [Mode.COUNT].
     * @property conditions checked before each iteration for [Mode.WHILE].
     * @property maxIterations safety limit for [Mode.WHILE] and [Mode.UNTIL_EXIT].
     */
    @Serializable
    @SerialName("action.repeat")
    data class Repeat(
        val mode: Mode = Mode.COUNT,
        val count: Int = 3,
        val conditions: List<com.cadence.domain.constraint.ConstraintConfig> = emptyList(),
        val match: ConditionMatch = ConditionMatch.ALL,
        val actions: List<ActionConfig> = emptyList(),
        val maxIterations: Int = 1_000,
    ) : ControlFlowAction {
        init {
            require(count in 1..MAX_ITERATIONS) { "count must be 1..$MAX_ITERATIONS, was $count" }
            require(maxIterations in 1..MAX_ITERATIONS) { "maxIterations must be 1..$MAX_ITERATIONS" }
        }

        @Serializable
        enum class Mode {
            /** A fixed number of times. */
            COUNT,

            /** While [conditions] hold, checked before each iteration. */
            WHILE,

            /** Until an [ExitLoop] runs (bounded by [maxIterations]). */
            UNTIL_EXIT,
        }

        companion object {
            const val MAX_ITERATIONS = 10_000
        }
    }

    /** Leaves the innermost [Repeat]. */
    @Serializable
    @SerialName("action.exit_loop")
    data object ExitLoop : ControlFlowAction

    /** Skips to the next iteration of the innermost [Repeat]. */
    @Serializable
    @SerialName("action.next_iteration")
    data object NextIteration : ControlFlowAction

    /** Ends this run; later actions don't run. The run still counts as successful. */
    @Serializable
    @SerialName("action.stop_run")
    data object StopRun : ControlFlowAction

    /**
     * Pauses until [trigger] fires or [timeoutSeconds] pass. Sets `{wait.timed_out}` and the
     * trigger's details as `{wait.<key>}`.
     */
    @Serializable
    @SerialName("action.wait_for_trigger")
    data class WaitForTrigger(
        val trigger: com.cadence.domain.trigger.TriggerConfig,
        val timeoutSeconds: Int = 300,
    ) : ControlFlowAction {
        init {
            require(timeoutSeconds in 1..MAX_TIMEOUT_SECONDS) { "timeoutSeconds must be 1..$MAX_TIMEOUT_SECONDS" }
        }

        companion object {
            const val MAX_TIMEOUT_SECONDS = 24 * 60 * 60
        }
    }

    /**
     * Runs another macro's actions now and waits for it to finish. It gets its own log entry.
     * Calls nest at most a few levels deep, so macros calling each other can't loop forever.
     */
    @Serializable
    @SerialName("action.run_macro")
    data class RunMacro(
        val macroId: Long,
        val checkConstraints: Boolean = false,
    ) : ControlFlowAction

    /**
     * Enables, disables, or toggles a macro.
     *
     * @property macroId the macro to change, or `null` for the macro running this action.
     */
    @Serializable
    @SerialName("action.set_macro_enabled")
    data class SetMacroEnabled(
        val macroId: Long? = null,
        val state: ToggleState = ToggleState.OFF,
    ) : ActionConfig

    /** Adds a message (with `{placeholders}`) to this run's log entry. */
    @Serializable
    @SerialName("action.log_message")
    data class LogMessage(val text: String) : ActionConfig

    /** Shows a short toast message. */
    @Serializable
    @SerialName("action.show_toast")
    data class ShowToast(val text: String, val long: Boolean = false) : ActionConfig

    /**
     * Requests a fresh location fix, so later `{location.*}` variables in this run use it instead
     * of a possibly stale last-known location. Also refreshes the location other apps see.
     *
     * @property highAccuracy use GPS (slower, more battery) rather than Wi-Fi/cell positioning.
     */
    @Serializable
    @SerialName("action.refresh_location")
    data class RefreshLocation(
        val timeoutSeconds: Int = 30,
        val highAccuracy: Boolean = true,
    ) : ActionConfig {
        init {
            require(timeoutSeconds in 5..120) { "timeoutSeconds must be 5..120, was $timeoutSeconds" }
        }
    }

    /** Posts a notification from Cadence. */
    @Serializable
    @SerialName("action.show_notification")
    data class ShowNotification(
        val title: String,
        val text: String,
    ) : ActionConfig

    /** Turns Wi-Fi on/off. On Android 10+ this falls back to the system Wi-Fi panel. */
    @Serializable
    @SerialName("action.set_wifi")
    data class SetWifi(val state: ToggleState) : ActionConfig

    /** Turns Bluetooth on/off. On Android 13+ this falls back to the Bluetooth settings screen. */
    @Serializable
    @SerialName("action.set_bluetooth")
    data class SetBluetooth(val state: ToggleState) : ActionConfig

    /** Sets the ringer mode. Silent mode needs Do Not Disturb access. */
    @Serializable
    @SerialName("action.set_ringer_mode")
    data class SetRingerMode(val mode: RingerMode) : ActionConfig

    /** Sets the volume of one audio stream as a percentage of its maximum. */
    @Serializable
    @SerialName("action.set_volume")
    data class SetVolume(
        val stream: VolumeStream,
        val levelPercent: Int,
    ) : ActionConfig {
        init {
            require(levelPercent in 0..100) { "levelPercent must be 0..100, was $levelPercent" }
        }
    }

    /**
     * Sets the screen brightness. Needs the "modify system settings" permission.
     *
     * @property automatic enable adaptive brightness; when true [levelPercent] is ignored.
     */
    @Serializable
    @SerialName("action.set_brightness")
    data class SetBrightness(
        val levelPercent: Int = 50,
        val automatic: Boolean = false,
    ) : ActionConfig {
        init {
            require(levelPercent in 0..100) { "levelPercent must be 0..100, was $levelPercent" }
        }
    }

    /** Opens the launcher activity of [packageName]. */
    @Serializable
    @SerialName("action.launch_app")
    data class LaunchApp(val packageName: String) : ActionConfig

    /** Opens [url] in the default handler. */
    @Serializable
    @SerialName("action.open_url")
    data class OpenUrl(val url: String) : ActionConfig

    /**
     * Speaks [text] with the system text-to-speech engine.
     *
     * @property languageTag BCP 47 tag such as `en-US`; `null` uses the engine default.
     */
    @Serializable
    @SerialName("action.speak_text")
    data class SpeakText(
        val text: String,
        val languageTag: String? = null,
    ) : ActionConfig

    /** Vibrates for [durationMillis]. */
    @Serializable
    @SerialName("action.vibrate")
    data class Vibrate(val durationMillis: Long = 500) : ActionConfig {
        init {
            require(durationMillis in 1..60_000) { "durationMillis must be 1..60000, was $durationMillis" }
        }
    }

    /** Turns the camera flashlight on, off, or toggles it. */
    @Serializable
    @SerialName("action.set_flashlight")
    data class SetFlashlight(val state: ToggleState) : ActionConfig

    /**
     * Sends an HTTP request. [url], header values, and [body] may contain `{placeholders}`.
     *
     * The response is always available to later actions in the run as `{http.status}`,
     * `{http.body}` and `{http.content_type}`.
     *
     * @property queryParameters appended to [url] as `?name=value&…`. Names and values may contain
     * `{placeholders}` and are URL-encoded after rendering, so spaces, `&` and `=` arrive intact.
     * @property saveResponseTo optional global variable to store the body in, or the value at
     * [jsonPath] if set (e.g. `data.items[0].name`).
     * @property failOnErrorStatus record 4xx/5xx responses as failures.
     */
    @Serializable
    @SerialName("action.http_request")
    data class HttpRequest(
        val url: String,
        val method: HttpMethod = HttpMethod.GET,
        val headers: List<HttpHeader> = emptyList(),
        val queryParameters: List<HttpParameter> = emptyList(),
        val body: String? = null,
        val contentType: String = "application/json",
        val timeoutSeconds: Int = 20,
        val saveResponseTo: String? = null,
        val jsonPath: String? = null,
        val failOnErrorStatus: Boolean = true,
    ) : ActionConfig {
        init {
            require(timeoutSeconds in 1..120) { "timeoutSeconds must be 1..120, was $timeoutSeconds" }
            require(saveResponseTo == null || com.cadence.domain.variable.VariableNames.isValidUserName(saveResponseTo)) {
                "Invalid variable name: $saveResponseTo"
            }
        }
    }

    /**
     * Writes a variable. [value] may contain `{placeholders}`.
     *
     * An empty [name] is allowed only while editing; the editor requires a valid one to save.
     */
    @Serializable
    @SerialName("action.set_variable")
    data class SetVariable(
        val name: String,
        val value: String,
        val scope: com.cadence.domain.variable.VariableScope = com.cadence.domain.variable.VariableScope.GLOBAL,
        val operation: Operation = Operation.SET,
    ) : ActionConfig {
        init {
            require(name.isEmpty() || com.cadence.domain.variable.VariableNames.isValidUserName(name)) {
                "Invalid variable name: $name"
            }
        }

        @Serializable
        enum class Operation {
            /** Replace the value. */
            SET,

            /** Add a number (negative to subtract); a missing or non-numeric value counts as 0. */
            ADD,

            /** Append text to the current value. */
            APPEND,
        }
    }
}

/** One `if` / `else if` branch: its conditions and the actions to run when they hold. */
@Serializable
data class ConditionalBlock(
    val conditions: List<com.cadence.domain.constraint.ConstraintConfig> = emptyList(),
    val match: ConditionMatch = ConditionMatch.ALL,
    val actions: List<ActionConfig> = emptyList(),
)

/** How a list of conditions combines. An empty list always holds. */
@Serializable
enum class ConditionMatch { ALL, ANY }

/** HTTP methods supported by [ActionConfig.HttpRequest]. */
@Serializable
enum class HttpMethod { GET, POST, PUT, PATCH, DELETE, HEAD }

/** One request header. The value may contain `{placeholders}`. */
@Serializable
data class HttpHeader(val name: String, val value: String)

/** One query parameter. Name and value may contain `{placeholders}`; both are URL-encoded. */
@Serializable
data class HttpParameter(val name: String, val value: String)

/** Target state for on/off actions. */
@Serializable
enum class ToggleState { ON, OFF, TOGGLE }

/** Ringer modes supported by `AudioManager`. */
@Serializable
enum class RingerMode { NORMAL, VIBRATE, SILENT }

/** Audio streams that volume actions can change. */
@Serializable
enum class VolumeStream { RING, NOTIFICATION, MEDIA, ALARM, CALL }
