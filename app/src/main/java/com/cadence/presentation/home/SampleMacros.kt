package com.cadence.presentation.home

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.Macro
import com.cadence.domain.trigger.ConnectionEvent
import com.cadence.domain.trigger.TriggerConfig
import java.time.Instant
import java.time.LocalTime

/**
 * Ready-made macros for trying triggers on a real device (debug builds only). They mostly use
 * triggers and actions that need no special permission.
 */
object SampleMacros {

    fun create(now: Instant, localTime: LocalTime): List<Macro> {
        val inTwoMinutes = localTime.plusMinutes(2)
        fun macro(
            name: String,
            triggers: List<TriggerConfig>,
            actions: List<ActionConfig>,
            constraints: List<ConstraintConfig> = emptyList(),
        ) = Macro(
            name = name,
            category = "Samples",
            triggers = triggers,
            actions = actions,
            constraints = constraints,
            createdAt = now,
            updatedAt = now,
        )
        return listOf(
            macro(
                "Buzz on unlock",
                listOf(TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.ON)),
                listOf(ActionConfig.Vibrate(durationMillis = 150)),
            ),
            macro(
                "Charging announcer",
                listOf(TriggerConfig.PowerConnection(ConnectionEvent.CONNECTED)),
                listOf(ActionConfig.SpeakText("Charging started"), ActionConfig.Vibrate(durationMillis = 300)),
            ),
            macro(
                "Charger unplugged notice",
                listOf(TriggerConfig.PowerConnection(ConnectionEvent.DISCONNECTED)),
                listOf(ActionConfig.ShowNotification("Charger unplugged", "Cadence noticed the charger was disconnected.")),
            ),
            macro(
                "Wi-Fi lost alert",
                listOf(TriggerConfig.WifiConnection(ConnectionEvent.DISCONNECTED)),
                listOf(ActionConfig.ShowNotification("Wi-Fi disconnected", "You're no longer on Wi-Fi.")),
            ),
            macro(
                "Low battery warning",
                listOf(TriggerConfig.BatteryLevel(20, TriggerConfig.BatteryLevel.Direction.FALLS_BELOW)),
                listOf(ActionConfig.ShowNotification("Battery low", "Battery fell below 20%."), ActionConfig.Vibrate(500)),
                constraints = listOf(ConstraintConfig.ChargingState(charging = false)),
            ),
            macro(
                "Two-minute timer",
                listOf(TriggerConfig.TimeOfDay(inTwoMinutes.hour, inTwoMinutes.minute)),
                listOf(ActionConfig.SpeakText("Your Cadence test alarm fired"), ActionConfig.SetFlashlight(com.cadence.domain.action.ToggleState.TOGGLE)),
            ),
        )
    }
}
