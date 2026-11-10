@file:OptIn(ExperimentalLayoutApi::class)

package com.cadence.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cadence.domain.action.RingerMode
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.constraint.DeviceConstraint
import com.cadence.domain.device.DevicePosition
import com.cadence.domain.device.DeviceSwitch
import com.cadence.domain.device.Located
import com.cadence.domain.device.NetworkType
import com.cadence.domain.trigger.DeviceTrigger
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.presentation.catalog.ConfigText
import com.cadence.presentation.catalog.DeviceText
import com.cadence.presentation.components.ChipChoice
import com.cadence.presentation.components.FieldLabel
import com.cadence.presentation.components.InfoNote
import com.cadence.presentation.components.IntSlider
import com.cadence.presentation.components.SegmentedChoice
import com.cadence.presentation.components.TimeField
import java.util.Locale

private const val SCREEN_OFF_NOTE =
    "Most phones pause this sensor while the screen is off, so this mainly works with the screen on. " +
        "Listening uses a little extra battery."

// region Triggers

/** Forms for [DeviceTrigger] configs. */
@Composable
fun DeviceTriggerForm(config: DeviceTrigger, onChange: (TriggerConfig) -> Unit, data: FormData) {
    when (config) {
        is DeviceTrigger.SwitchChanged -> {
            FieldLabel("When")
            ChipChoice(
                options = DeviceSwitch.entries.filter { it.triggerable },
                selected = config.switch,
                label = DeviceText::switchLabel,
                onSelect = { onChange(config.copy(switch = it)) },
            )
            FieldLabel("Turns")
            OnOffChoice(config.switch, config.turnedOn) { onChange(config.copy(turnedOn = it)) }
            switchNote(config.switch)?.let { InfoNote(it) }
        }
        is DeviceTrigger.RingerModeChanged -> {
            FieldLabel("When the ringer changes to")
            SegmentedChoice(RingerMode.entries, config.mode, DeviceText::ringerLabel, { onChange(config.copy(mode = it)) })
        }
        is DeviceTrigger.MusicTrackChanged -> {
            FieldLabel("Player")
            OptionalAppPicker(config.packageName, data, "Any player") { onChange(config.copy(packageName = it)) }
            InfoNote(
                "Needs notification access, which is how Android lets apps see what's playing. " +
                    "Later actions can use {music.title} and {music.artist}; track names are never written to the log.",
            )
        }
        is DeviceTrigger.Shake -> {
            FieldLabel("Sensitivity")
            SegmentedChoice(
                options = DeviceTrigger.Shake.Sensitivity.entries,
                selected = config.sensitivity,
                label = {
                    when (it) {
                        DeviceTrigger.Shake.Sensitivity.LOW -> "Hard shake"
                        DeviceTrigger.Shake.Sensitivity.MEDIUM -> "Normal"
                        DeviceTrigger.Shake.Sensitivity.HIGH -> "Gentle"
                    }
                },
                onSelect = { onChange(config.copy(sensitivity = it)) },
            )
            InfoNote("Shake firmly back and forth two or three times. $SCREEN_OFF_NOTE")
        }
        is DeviceTrigger.PositionChanged -> {
            FieldLabel("When the phone is")
            ChipChoice(DevicePosition.entries, config.position, DeviceText::positionLabel, { onChange(config.copy(position = it)) })
            InfoNote("Fires once the phone has stayed in that position for about a second. $SCREEN_OFF_NOTE")
        }
        is DeviceTrigger.Proximity -> {
            FieldLabel("When the sensor is")
            SegmentedChoice(listOf(true, false), config.near, { if (it) "Covered" else "Uncovered" }, { onChange(config.copy(near = it)) })
            InfoNote("The proximity sensor is next to the earpiece; it's what turns the screen off during calls.")
        }
        is DeviceTrigger.LightLevel -> {
            FieldLabel("When the light")
            DirectionChoice(config.direction, "Falls below", "Rises above") { onChange(config.copy(direction = it)) }
            FieldLabel("Level")
            ChipChoice(
                options = LUX_PRESETS,
                selected = LUX_PRESETS.minBy { kotlin.math.abs(it - config.thresholdLux) },
                label = { luxLabel(it) },
                onSelect = { onChange(config.copy(thresholdLux = it)) },
            )
            InfoNote("Checked every couple of seconds. $SCREEN_OFF_NOTE")
        }
        is DeviceTrigger.BatteryTemperature -> {
            FieldLabel("When the battery")
            DirectionChoice(config.direction, "Cools below", "Heats above") { onChange(config.copy(direction = it)) }
            FieldLabel("Temperature")
            IntSlider(config.thresholdCelsius, 10..60, { onChange(config.copy(thresholdCelsius = it)) }, { "$it °C" })
            InfoNote("Phones normally run at 25–40 °C; above 45 °C most start limiting charging.")
        }
        is DeviceTrigger.AppPackageChanged -> {
            FieldLabel("When an app is")
            SegmentedChoice(
                options = DeviceTrigger.AppPackageChanged.Event.entries,
                selected = config.event,
                label = {
                    when (it) {
                        DeviceTrigger.AppPackageChanged.Event.INSTALLED -> "Installed"
                        DeviceTrigger.AppPackageChanged.Event.REMOVED -> "Removed"
                        DeviceTrigger.AppPackageChanged.Event.UPDATED -> "Updated"
                    }
                },
                // A new install can't be picked from the installed apps, so it always means any app.
                onSelect = { event ->
                    val installed = event == DeviceTrigger.AppPackageChanged.Event.INSTALLED
                    onChange(config.copy(event = event, packageName = if (installed) null else config.packageName))
                },
            )
            if (config.event != DeviceTrigger.AppPackageChanged.Event.INSTALLED) {
                FieldLabel("App")
                OptionalAppPicker(config.packageName, data, "Any app") { onChange(config.copy(packageName = it)) }
            }
            InfoNote("Later actions can use {trigger.package} and {trigger.app}. Apps without a home-screen icon may not be detected.")
        }
        is DeviceTrigger.NetworkTypeChanged -> {
            FieldLabel("When the phone switches to")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = config.to == null, onClick = { onChange(config.copy(to = null)) }, label = { Text("Any change") })
                NetworkType.entries.forEach { type ->
                    FilterChip(
                        selected = config.to == type,
                        onClick = { onChange(config.copy(to = type)) },
                        label = { Text(DeviceText.networkLabel(type)) },
                    )
                }
            }
            InfoNote("Fires a couple of seconds after the network settles. Later actions can use {trigger.networkType} and {trigger.ip}.")
        }
        DeviceTrigger.IpAddressChanged -> Text("No settings needed.")
        is DeviceTrigger.Sun -> {
            FieldLabel("At")
            SegmentedChoice(
                options = DeviceTrigger.Sun.Event.entries,
                selected = config.event,
                label = { if (it == DeviceTrigger.Sun.Event.SUNRISE) "Sunrise" else "Sunset" },
                onSelect = { onChange(config.copy(event = it)) },
            )
            FieldLabel("Shift")
            IntSlider(
                value = config.offsetMinutes,
                range = -DeviceTrigger.Sun.MAX_OFFSET_MINUTES..DeviceTrigger.Sun.MAX_OFFSET_MINUTES,
                onChange = { onChange(config.copy(offsetMinutes = it)) },
                valueLabel = { offsetLabel(it) },
                step = 5,
            )
            PlaceFields(config, config.label, { onChange(it as TriggerConfig) }, { onChange(config.copy(label = it)) }, data)
        }
        is DeviceTrigger.DayOfMonth -> {
            FieldLabel("Time")
            TimeField(config.hour, config.minute, onChange = { h, m -> onChange(config.copy(hour = h, minute = m)) })
            MonthDaysField(config.days, config.lastDay) { days, last ->
                if (days.isNotEmpty() || last) onChange(config.copy(days = days, lastDay = last))
            }
        }
    }
}

// endregion

// region Constraints

/** Forms for [DeviceConstraint] configs. */
@Composable
fun DeviceConstraintForm(config: DeviceConstraint, onChange: (ConstraintConfig) -> Unit, data: FormData) {
    when (config) {
        is DeviceConstraint.SwitchState -> {
            FieldLabel("Only while")
            ChipChoice(DeviceSwitch.entries, config.switch, DeviceText::switchLabel, { onChange(config.copy(switch = it)) })
            FieldLabel("Is")
            OnOffChoice(config.switch, config.on) { onChange(config.copy(on = it)) }
            switchNote(config.switch)?.let { InfoNote(it) }
        }
        is DeviceConstraint.RingerModeIs -> {
            FieldLabel("Only while the ringer is (any of)")
            MultiChips(RingerMode.entries, config.modes, DeviceText::ringerLabel) {
                if (it.isNotEmpty()) onChange(config.copy(modes = it))
            }
        }
        is DeviceConstraint.NetworkTypeIs -> {
            FieldLabel("Only while on (any of)")
            MultiChips(NetworkType.entries, config.types, DeviceText::networkLabel) {
                if (it.isNotEmpty()) onChange(config.copy(types = it))
            }
        }
        is DeviceConstraint.PositionIs -> {
            FieldLabel("Only while the phone is")
            ChipChoice(DevicePosition.entries, config.position, DeviceText::positionLabel, { onChange(config.copy(position = it)) })
            InfoNote("Takes a half-second reading when checked. With the screen off some phones don't answer, and the condition counts as not met.")
        }
        is DeviceConstraint.AppInForeground -> {
            FieldLabel("App")
            OptionalAppPicker(config.packageName.ifBlank { null }, data, null) { onChange(config.copy(packageName = it.orEmpty())) }
            FieldLabel("Only while it is")
            SegmentedChoice(listOf(true, false), config.inForeground, { if (it) "On screen" else "Not on screen" }, {
                onChange(config.copy(inForeground = it))
            })
            InfoNote("Needs usage access. While the screen is off, no app counts as on screen.")
        }
        is DeviceConstraint.Daylight -> {
            FieldLabel("Only")
            SegmentedChoice(listOf(true, false), config.daylight, { if (it) "In daylight" else "After dark" }, {
                onChange(config.copy(daylight = it))
            })
            PlaceFields(config, config.label, { onChange(it as ConstraintConfig) }, { onChange(config.copy(label = it)) }, data)
        }
        is DeviceConstraint.DaysOfMonth -> MonthDaysField(config.days, config.lastDay) { days, last ->
            if (days.isNotEmpty() || last) onChange(config.copy(days = days, lastDay = last))
        }
        is DeviceConstraint.Months -> {
            FieldLabel("Only in")
            MultiChips((1..12).toList(), config.months, DeviceText::monthLabel) {
                if (it.isNotEmpty()) onChange(config.copy(months = it))
            }
        }
    }
}

// endregion

// region Shared fields

@Composable
private fun OnOffChoice(switch: DeviceSwitch, on: Boolean, onSelect: (Boolean) -> Unit) {
    SegmentedChoice(listOf(true, false), on, { DeviceText.stateLabel(switch, it) }, onSelect)
}

@Composable
private fun DirectionChoice(
    selected: TriggerConfig.BatteryLevel.Direction,
    below: String,
    above: String,
    onSelect: (TriggerConfig.BatteryLevel.Direction) -> Unit,
) {
    SegmentedChoice(
        options = TriggerConfig.BatteryLevel.Direction.entries,
        selected = selected,
        label = { if (it == TriggerConfig.BatteryLevel.Direction.FALLS_BELOW) below else above },
        onSelect = onSelect,
    )
}

@Composable
private fun <T> MultiChips(options: List<T>, selected: Set<T>, label: (T) -> String, onChange: (Set<T>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onChange(if (option in selected) selected - option else selected + option) },
                label = { Text(label(option)) },
            )
        }
    }
}

/** Day 1..31 chips plus a "last day" switch. At least one must stay selected. */
@Composable
private fun MonthDaysField(days: Set<Int>, lastDay: Boolean, onChange: (Set<Int>, Boolean) -> Unit) {
    FieldLabel("Days of the month")
    MultiChips((1..31).toList(), days, { it.toString() }) { onChange(it, lastDay) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("Last day of every month", modifier = Modifier.weight(1f))
        Switch(checked = lastDay, onCheckedChange = { onChange(days, it) })
    }
    if (days.any { it > 28 }) {
        InfoNote("Months without a chosen day (like the 31st in April) are skipped. Use \"Last day\" for month ends.")
    }
}

/** An app chooser where "none" means any app ([anyLabel]), or a required app when [anyLabel] is `null`. */
@Composable
private fun OptionalAppPicker(packageName: String?, data: FormData, anyLabel: String?, onSelect: (String?) -> Unit) {
    if (anyLabel != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = packageName == null, onClick = { onSelect(null) }, label = { Text(anyLabel) })
            FilterChip(
                selected = packageName != null,
                onClick = { if (packageName == null) onSelect(data.apps.firstOrNull()?.packageName) },
                label = { Text("A chosen app") },
            )
        }
        if (packageName == null) return
    }
    AppPickerField(packageName.orEmpty(), data.apps) { onSelect(it) }
}

/** Place name, "use my current location", and coordinates for sunrise and sunset items. */
@Composable
private fun PlaceFields(
    config: Located,
    label: String?,
    onChange: (Located) -> Unit,
    onLabelChange: (String?) -> Unit,
    data: FormData,
) {
    var latitudeText by remember(data.locationVersion) { mutableStateOf(coordinate(config.latitude, config)) }
    var longitudeText by remember(data.locationVersion) { mutableStateOf(coordinate(config.longitude, config)) }
    OutlinedTextField(
        value = label.orEmpty(),
        onValueChange = { onLabelChange(it.ifBlank { null }) },
        label = { Text("Place name (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    FilledTonalButton(
        onClick = data.onUseCurrentLocation,
        enabled = !data.isLocating,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        if (data.isLocating) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.MyLocation, contentDescription = null)
        }
        Text("Use my current location", modifier = Modifier.padding(start = 8.dp))
    }
    Row(Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = latitudeText,
            onValueChange = { raw ->
                latitudeText = raw
                raw.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }?.let { onChange(config.at(it, config.longitude)) }
            },
            label = { Text("Latitude") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = longitudeText,
            onValueChange = { raw ->
                longitudeText = raw
                raw.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { onChange(config.at(config.latitude, it)) }
            },
            label = { Text("Longitude") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
    InfoNote(
        "Sun times are calculated on the phone for this place, so Cadence never needs location access while running. " +
            "Your location is only read now, if you tap the button.",
    )
}

// endregion

private fun coordinate(value: Double, config: Located): String =
    if (!config.hasLocation) "" else String.format(Locale.ROOT, "%.4f", value)

private val LUX_PRESETS = listOf(5, 10, 50, 200, 1_000, 10_000)

private fun luxLabel(lux: Int): String = when (lux) {
    5 -> "Dark (5 lx)"
    10 -> "Dim (10 lx)"
    50 -> "Low indoor (50 lx)"
    200 -> "Indoor (200 lx)"
    1_000 -> "Overcast (1,000 lx)"
    else -> "Daylight (10,000 lx)"
}

private fun offsetLabel(minutes: Int): String = when {
    minutes == 0 -> "Exactly then"
    minutes < 0 -> "${ConfigText.interval(-minutes)} before"
    else -> "${ConfigText.interval(minutes)} after"
}

private fun switchNote(switch: DeviceSwitch): String? = when (switch) {
    DeviceSwitch.HEADPHONES -> "Counts wired, USB and Bluetooth audio, including car kits and speakers."
    DeviceSwitch.MUSIC_PLAYING -> "Any app playing audio counts, including videos and navigation voices."
    DeviceSwitch.DO_NOT_DISTURB -> "On means any Do Not Disturb mode: priority only, alarms only, or total silence."
    DeviceSwitch.WIFI -> "Whether Wi-Fi is switched on. To react to joining a network, use the Wi-Fi trigger."
    DeviceSwitch.DOCKED -> "Only phones with desk or car dock support report this."
    DeviceSwitch.DEVICE_LOCKED -> "The lock can engage a few seconds after the screen turns off."
    else -> null
}
