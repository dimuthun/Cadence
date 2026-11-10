package com.cadence.presentation.catalog

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.RingerMode
import com.cadence.domain.action.ToggleState
import com.cadence.domain.action.VolumeStream
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.constraint.DeviceConstraint
import com.cadence.domain.model.Weekday
import com.cadence.domain.trigger.ConnectionEvent
import com.cadence.domain.trigger.DeviceTrigger
import com.cadence.domain.trigger.TriggerConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Plain-language summaries of configs for lists, the editor, and the log.
 *
 * @param zone time zone for dates in summaries.
 * @param locale locale for date formatting.
 * @param macroName resolves a macro id to its name, for actions that refer to other macros.
 * @param appLabel resolves a package name to the app's name; defaults to the package name.
 */
class ConfigText(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
    private val macroName: (Long) -> String? = { null },
    private val appLabel: (String) -> String = { it },
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)

    fun trigger(config: TriggerConfig): String = when (config) {
        TriggerConfig.Manual -> "Run manually"
        is TriggerConfig.TimeOfDay -> "At ${time(config.hour, config.minute)}${days(config.days, prefix = ", ")}"
        is TriggerConfig.BatteryLevel -> when (config.direction) {
            TriggerConfig.BatteryLevel.Direction.FALLS_BELOW -> "Battery falls below ${config.thresholdPercent}%"
            TriggerConfig.BatteryLevel.Direction.RISES_ABOVE -> "Battery rises above ${config.thresholdPercent}%"
        }
        is TriggerConfig.PowerConnection -> "Charger ${connected(config.event)}"
        is TriggerConfig.WifiConnection -> "Wi-Fi ${connected(config.event)}"
        is TriggerConfig.BluetoothConnection -> {
            val device = config.deviceName ?: config.deviceAddress ?: "Any Bluetooth device"
            "$device ${connected(config.event)}"
        }
        is TriggerConfig.AppLifecycle -> {
            val app = appOrPlaceholder(config.packageName)
            when (config.event) {
                TriggerConfig.AppLifecycle.Event.LAUNCHED -> "$app opened"
                TriggerConfig.AppLifecycle.Event.CLOSED -> "$app closed"
            }
        }
        TriggerConfig.BootCompleted -> "Device finished starting"
        is TriggerConfig.ScreenState -> when (config.event) {
            TriggerConfig.ScreenState.Event.ON -> "Screen turns on"
            TriggerConfig.ScreenState.Event.OFF -> "Screen turns off"
            TriggerConfig.ScreenState.Event.UNLOCKED -> "Phone unlocked"
        }
        is DeviceTrigger -> DeviceText.trigger(config, appLabel)
        is TriggerConfig.NotificationPosted -> buildString {
            append("Notification from ${appOrPlaceholder(config.packageName)}")
            config.textContains?.takeIf { it.isNotBlank() }?.let { append(" containing \"$it\"") }
        }
        is TriggerConfig.DateTime -> {
            val start = Instant.ofEpochMilli(config.startAtEpochMillis).atZone(zone)
            val at = "${dateFormatter.format(start)} at ${time(start.hour, start.minute)}"
            config.repeatIntervalMinutes?.let { "Every ${interval(it)} from $at" } ?: "On $at"
        }
        is TriggerConfig.LocationArea -> {
            val place = config.label?.takeIf { it.isNotBlank() }
                ?: if (ConfigRules.isComplete(config)) {
                    String.format(Locale.ROOT, "%.5f, %.5f", config.latitude, config.longitude)
                } else {
                    "a place"
                }
            val verb = if (config.event == TriggerConfig.LocationArea.Event.ENTER) "Arriving at" else "Leaving"
            "$verb $place (${distance(config.radiusMeters)})"
        }
        is TriggerConfig.LocationMoved -> "Moved ${distance(config.distanceMeters)}"
    }

    fun action(config: ActionConfig): String = when (config) {
        is ActionConfig.Wait -> "Wait ${duration(config.durationMillis)}"
        is ActionConfig.IfElse -> when {
            config.branches.size == 1 && config.elseActions.isEmpty() -> "If ${conditions(config.branches[0].conditions, config.branches[0].match)}"
            else -> "If / else (${config.branches.size + if (config.elseActions.isNotEmpty()) 1 else 0} branches)"
        }
        is ActionConfig.Repeat -> "Repeat ${repeatDescription(config)}"
        ActionConfig.ExitLoop -> "Exit loop"
        ActionConfig.NextIteration -> "Next iteration"
        ActionConfig.StopRun -> "Stop this run"
        is ActionConfig.WaitForTrigger ->
            "Wait for: ${trigger(config.trigger).replaceFirstChar(Char::lowercase)} (up to ${duration(config.timeoutSeconds * 1_000L)})"
        is ActionConfig.RunMacro -> "Run \"${macro(config.macroId)}\""
        is ActionConfig.SetMacroEnabled -> {
            val target = config.macroId?.let { "\"${macro(it)}\"" } ?: "this macro"
            when (config.state) {
                ToggleState.ON -> "Enable $target"
                ToggleState.OFF -> "Disable $target"
                ToggleState.TOGGLE -> "Toggle $target"
            }
        }
        is ActionConfig.LogMessage -> "Log \"${config.text.ifBlank { "…" }.ellipsize()}\""
        is ActionConfig.ShowToast -> "Toast \"${config.text.ifBlank { "…" }.ellipsize()}\""
        is ActionConfig.ShowNotification -> "Show notification \"${config.title.ifBlank { "…" }}\""
        is ActionConfig.SetWifi -> "Wi-Fi ${toggle(config.state)}"
        is ActionConfig.SetBluetooth -> "Bluetooth ${toggle(config.state)}"
        is ActionConfig.SetRingerMode -> when (config.mode) {
            RingerMode.NORMAL -> "Ringer: sound on"
            RingerMode.VIBRATE -> "Ringer: vibrate"
            RingerMode.SILENT -> "Ringer: silent"
        }
        is ActionConfig.SetVolume -> "${stream(config.stream)} volume ${config.levelPercent}%"
        is ActionConfig.SetBrightness ->
            if (config.automatic) "Brightness: adaptive" else "Brightness ${config.levelPercent}%"
        is ActionConfig.LaunchApp -> "Open ${appOrPlaceholder(config.packageName)}"
        is ActionConfig.OpenUrl -> "Open ${config.url}"
        is ActionConfig.SpeakText -> "Say \"${config.text.ifBlank { "…" }}\""
        is ActionConfig.Vibrate -> "Vibrate ${duration(config.durationMillis)}"
        is ActionConfig.SetFlashlight -> "Flashlight ${toggle(config.state)}"
        is ActionConfig.HttpRequest -> "${config.method} ${shortUrl(config.url)}" +
            (if (config.queryParameters.isNotEmpty()) " (+${config.queryParameters.size} params)" else "") +
            (config.saveResponseTo?.let { " → {$it}" } ?: "")
        is ActionConfig.RefreshLocation ->
            "Refresh location (${if (config.highAccuracy) "GPS" else "low power"}, up to ${config.timeoutSeconds} s)"
        is ActionConfig.SetVariable -> {
            val name = config.name.ifBlank { "…" }
            val scope = if (config.scope == com.cadence.domain.variable.VariableScope.LOCAL) " (this run)" else ""
            when (config.operation) {
                ActionConfig.SetVariable.Operation.SET -> "Set $name to \"${config.value.ellipsize()}\"$scope"
                ActionConfig.SetVariable.Operation.ADD -> "Add ${config.value.ellipsize()} to $name$scope"
                ActionConfig.SetVariable.Operation.APPEND -> "Append \"${config.value.ellipsize()}\" to $name$scope"
            }
        }
    }

    /** "any condition", "battery below 20% and while charging", … for headers of branches and loops. */
    fun conditions(
        conditions: List<ConstraintConfig>,
        match: com.cadence.domain.action.ConditionMatch,
    ): String = when {
        conditions.isEmpty() -> "(no conditions: always)"
        else -> conditions.joinToString(
            if (match == com.cadence.domain.action.ConditionMatch.ALL) " and " else " or ",
        ) { constraint(it).replaceFirstChar(Char::lowercase) }
    }

    /** "3 times", "while …", "until exit". */
    fun repeatDescription(config: ActionConfig.Repeat): String = when (config.mode) {
        ActionConfig.Repeat.Mode.COUNT -> "${config.count} time${if (config.count == 1) "" else "s"}"
        ActionConfig.Repeat.Mode.WHILE -> "while ${conditions(config.conditions, config.match)}"
        ActionConfig.Repeat.Mode.UNTIL_EXIT -> "until \"Exit loop\" (at most ${config.maxIterations} times)"
    }

    fun constraint(config: ConstraintConfig): String = when (config) {
        is ConstraintConfig.Group -> {
            val parts = config.constraints.joinToString(", ") { constraint(it).replaceFirstChar(Char::lowercase) }.ifEmpty { "…" }
            when (config.operator) {
                ConstraintConfig.Group.Operator.ALL -> "All of: $parts"
                ConstraintConfig.Group.Operator.ANY -> "Any of: $parts"
                ConstraintConfig.Group.Operator.NONE -> "None of: $parts"
            }
        }
        is DeviceConstraint -> DeviceText.constraint(config, appLabel)
        is ConstraintConfig.MacroRunning ->
            "\"${macro(config.macroId)}\" ${if (config.running) "is running" else "isn't running"}"
        is ConstraintConfig.MacroEnabled ->
            "\"${macro(config.macroId)}\" is ${if (config.enabled) "enabled" else "disabled"}"
        is ConstraintConfig.TimeSinceLastRun -> {
            val target = config.macroId?.let { "\"${macro(it)}\"" } ?: "this macro"
            "$target hasn't run in the last ${interval(config.minutes)}"
        }
        is ConstraintConfig.TimeRange -> {
            val start = time(config.startMinuteOfDay / 60, config.startMinuteOfDay % 60)
            val end = time(config.endMinuteOfDay / 60, config.endMinuteOfDay % 60)
            if (config.startMinuteOfDay == config.endMinuteOfDay) "Any time of day" else "Between $start and $end"
        }
        is ConstraintConfig.BatteryLevelRange -> "Battery between ${config.minPercent}% and ${config.maxPercent}%"
        is ConstraintConfig.ChargingState -> if (config.charging) "While charging" else "While not charging"
        is ConstraintConfig.WifiNetwork -> if (config.connected) "While on Wi-Fi" else "While not on Wi-Fi"
        is ConstraintConfig.DaysOfWeek -> "On ${days(config.days, prefix = "").ifBlank { "every day" }}"
        is ConstraintConfig.VariableCompare -> {
            val name = config.name.ifBlank { "…" }
            val value = "\"${config.value.ellipsize()}\""
            when (config.operator) {
                ConstraintConfig.VariableCompare.Operator.EQUALS -> "$name is $value"
                ConstraintConfig.VariableCompare.Operator.NOT_EQUALS -> "$name isn't $value"
                ConstraintConfig.VariableCompare.Operator.CONTAINS -> "$name contains $value"
                ConstraintConfig.VariableCompare.Operator.GREATER_THAN -> "$name > ${config.value}"
                ConstraintConfig.VariableCompare.Operator.LESS_THAN -> "$name < ${config.value}"
                ConstraintConfig.VariableCompare.Operator.IS_SET -> "$name has a value"
                ConstraintConfig.VariableCompare.Operator.IS_NOT_SET -> "$name is empty"
            }
        }
    }

    private fun macro(id: Long): String = if (id <= 0) "…" else macroName(id) ?: "deleted macro"

    private fun appOrPlaceholder(packageName: String) =
        if (packageName.isBlank()) "an app" else appLabel(packageName)

    /** Host and path without the scheme, shortened for lists. */
    private fun shortUrl(url: String): String =
        url.substringAfter("://").substringBefore('?').ifBlank { "…" }.ellipsize(40)

    private fun String.ellipsize(max: Int = 30) = if (length <= max) this else take(max - 1) + "…"

    companion object {
        fun time(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

        /** "500 m", "1.5 km", "20 km". */
        fun distance(meters: Int): String = when {
            meters < 1_000 -> "$meters m"
            meters % 1_000 == 0 -> "${meters / 1_000} km"
            else -> String.format(Locale.ROOT, "%.1f km", meters / 1_000.0)
        }

        /** "30 min", "2 hours", "1 day". */
        fun interval(minutes: Int): String = when {
            minutes % (24 * 60) == 0 -> plural(minutes / (24 * 60), "day")
            minutes % 60 == 0 -> plural(minutes / 60, "hour")
            else -> "$minutes min"
        }

        private fun plural(count: Int, unit: String) = if (count == 1) "1 $unit" else "$count ${unit}s"

        fun duration(millis: Long): String = when {
            millis < 1_000 -> "${millis} ms"
            millis % 60_000 == 0L && millis >= 60_000 -> "${millis / 60_000} min"
            millis % 1_000 == 0L -> "${millis / 1_000} s"
            else -> String.format(Locale.ROOT, "%.1f s", millis / 1_000.0)
        }

        /** "every day", "weekdays", "weekends", or "Mon, Wed". Empty [days] means every day. */
        fun days(days: Set<Weekday>, prefix: String): String = when {
            days.isEmpty() || days.size == Weekday.entries.size -> if (prefix.isEmpty()) "every day" else ""
            days == Weekday.entries.take(5).toSet() -> "${prefix}weekdays"
            days == setOf(Weekday.SATURDAY, Weekday.SUNDAY) -> "${prefix}weekends"
            else -> prefix + days.sortedBy { it.ordinal }.joinToString(", ") { shortDay(it) }
        }

        fun shortDay(day: Weekday): String = day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

        private fun connected(event: ConnectionEvent) =
            if (event == ConnectionEvent.CONNECTED) "connected" else "disconnected"

        private fun toggle(state: ToggleState) = when (state) {
            ToggleState.ON -> "on"
            ToggleState.OFF -> "off"
            ToggleState.TOGGLE -> "toggle"
        }

        private fun stream(stream: VolumeStream) = when (stream) {
            VolumeStream.RING -> "Ring"
            VolumeStream.NOTIFICATION -> "Notification"
            VolumeStream.MEDIA -> "Media"
            VolumeStream.ALARM -> "Alarm"
            VolumeStream.CALL -> "Call"
        }
    }
}

/** Editor rules that sit on top of the configs' own `require` validation. */
object ConfigRules {

    /** `false` while required fields (like the app to launch) are still empty. */
    fun isComplete(config: Any): Boolean = when (config) {
        is TriggerConfig.AppLifecycle -> config.packageName.isNotBlank()
        is TriggerConfig.NotificationPosted -> config.packageName.isNotBlank()
        is ActionConfig.ShowNotification -> config.title.isNotBlank()
        is ActionConfig.LaunchApp -> config.packageName.isNotBlank()
        is ActionConfig.OpenUrl -> URL_PATTERN.matches(config.url.trim())
        is ActionConfig.SpeakText -> config.text.isNotBlank()
        // 0,0 is the "not set yet" placeholder (it's in the Atlantic Ocean).
        is TriggerConfig.LocationArea -> config.latitude != 0.0 || config.longitude != 0.0
        is ActionConfig.HttpRequest -> HTTP_URL_PATTERN.matches(config.url.trim())
        is ActionConfig.SetVariable -> com.cadence.domain.variable.VariableNames.isValidUserName(config.name)
        is ConstraintConfig.VariableCompare -> com.cadence.domain.variable.VariableNames.isValidReference(config.name)
        is ActionConfig.RunMacro -> config.macroId > 0
        is ActionConfig.WaitForTrigger -> isComplete(config.trigger)
        is ActionConfig.LogMessage -> config.text.isNotBlank()
        is ActionConfig.ShowToast -> config.text.isNotBlank()
        is ConstraintConfig.MacroRunning -> config.macroId > 0
        is ConstraintConfig.MacroEnabled -> config.macroId > 0
        is ConstraintConfig.Group -> config.constraints.isNotEmpty() && config.constraints.all(::isComplete)
        is com.cadence.domain.device.Located -> config.hasLocation
        is DeviceConstraint.AppInForeground -> config.packageName.isNotBlank()
        is ActionConfig.IfElse -> config.branches.all { branch -> branch.conditions.all(::isComplete) }
        is ActionConfig.Repeat -> config.conditions.all(::isComplete)
        else -> true
    }

    /** Like [URL_PATTERN] but the host may be a `{placeholder}`. */
    private val HTTP_URL_PATTERN = Regex("^https?://[^\\s/?#][^\\s]*$", RegexOption.IGNORE_CASE)

    /** Configs without fields are added straight away instead of opening a form. */
    fun hasSettings(config: Any): Boolean = when (config) {
        TriggerConfig.BootCompleted, DeviceTrigger.IpAddressChanged,
        ActionConfig.ExitLoop, ActionConfig.NextIteration, ActionConfig.StopRun,
        -> false
        else -> true
    }

    private val URL_PATTERN = Regex("^https?://[^\\s/$.?#][^\\s]*$", RegexOption.IGNORE_CASE)
}
