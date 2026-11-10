@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cadence.presentation.editor

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.platform.LocalContext
import com.cadence.presentation.catalog.ConfigRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.IconButton
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.HttpHeader
import com.cadence.domain.action.HttpMethod
import com.cadence.domain.action.HttpParameter
import com.cadence.domain.variable.VariableNames
import com.cadence.domain.variable.VariableScope
import com.cadence.presentation.components.PLACEHOLDER_HINT
import com.cadence.presentation.components.TemplateTextField
import com.cadence.presentation.components.VariablePickerDialog
import com.cadence.domain.action.RingerMode
import com.cadence.domain.action.ToggleState
import com.cadence.domain.action.VolumeStream
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.constraint.DeviceConstraint
import com.cadence.domain.trigger.ConnectionEvent
import com.cadence.domain.trigger.DeviceTrigger
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.platform.device.InstalledApp
import com.cadence.platform.device.PairedDevice
import com.cadence.presentation.catalog.ConfigText
import com.cadence.presentation.components.ChipChoice
import com.cadence.presentation.components.FieldLabel
import com.cadence.presentation.components.InfoNote
import com.cadence.presentation.components.IntSlider
import com.cadence.presentation.components.SegmentedChoice
import com.cadence.presentation.components.TimeField
import com.cadence.presentation.components.WeekdayChips
import kotlin.math.roundToInt

/** Data the forms need beyond the config itself. */
data class FormData(
    val apps: List<InstalledApp>,
    val pairedDevices: List<PairedDevice>,
    val playServicesAvailable: Boolean = true,
    val isLocating: Boolean = false,
    val locationVersion: Int = 0,
    val onUseCurrentLocation: () -> Unit = {},
    val globalVariables: List<String> = emptyList(),
    val otherMacros: List<MacroRef> = emptyList(),
)

private val connectionOptions = listOf(ConnectionEvent.CONNECTED, ConnectionEvent.DISCONNECTED)

// region Triggers

@Composable
fun TriggerForm(config: TriggerConfig, onChange: (TriggerConfig) -> Unit, data: FormData) {
    Column(Modifier.fillMaxWidth()) {
        when (config) {
            is TriggerConfig.TimeOfDay -> {
                FieldLabel("Time")
                TimeField(config.hour, config.minute, onChange = { h, m -> onChange(config.copy(hour = h, minute = m)) })
                FieldLabel("Days (none selected = every day)")
                WeekdayChips(config.days, onChange = { onChange(config.copy(days = it)) })
            }
            is TriggerConfig.BatteryLevel -> {
                FieldLabel("When the battery")
                SegmentedChoice(
                    options = TriggerConfig.BatteryLevel.Direction.entries,
                    selected = config.direction,
                    label = { if (it == TriggerConfig.BatteryLevel.Direction.FALLS_BELOW) "Falls below" else "Rises above" },
                    onSelect = { onChange(config.copy(direction = it)) },
                )
                FieldLabel("Level")
                IntSlider(config.thresholdPercent, 5..95, { onChange(config.copy(thresholdPercent = it)) }, { "$it%" }, step = 5)
            }
            is TriggerConfig.PowerConnection -> {
                FieldLabel("When the charger is")
                ConnectionChoice(config.event) { onChange(config.copy(event = it)) }
            }
            is TriggerConfig.WifiConnection -> {
                FieldLabel("When Wi-Fi is")
                ConnectionChoice(config.event) { onChange(config.copy(event = it)) }
                InfoNote("Works with any Wi-Fi network. Cadence doesn't read network names, so it never needs location access.")
            }
            is TriggerConfig.BluetoothConnection -> {
                FieldLabel("When a device is")
                ConnectionChoice(config.event) { onChange(config.copy(event = it)) }
                FieldLabel("Device")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.deviceAddress == null,
                        onClick = { onChange(config.copy(deviceAddress = null, deviceName = null)) },
                        label = { Text("Any device") },
                    )
                    data.pairedDevices.forEach { device ->
                        FilterChip(
                            selected = config.deviceAddress.equals(device.address, ignoreCase = true),
                            onClick = { onChange(config.copy(deviceAddress = device.address, deviceName = device.name)) },
                            label = { Text(device.name) },
                        )
                    }
                }
                if (data.pairedDevices.isEmpty()) {
                    InfoNote("Grant \"Nearby devices\" to pick a specific paired device.")
                }
            }
            is TriggerConfig.AppLifecycle -> {
                FieldLabel("App")
                AppPickerField(config.packageName, data.apps) { onChange(config.copy(packageName = it)) }
                FieldLabel("When it is")
                SegmentedChoice(
                    options = TriggerConfig.AppLifecycle.Event.entries,
                    selected = config.event,
                    label = { if (it == TriggerConfig.AppLifecycle.Event.LAUNCHED) "Opened" else "Closed" },
                    onSelect = { onChange(config.copy(event = it)) },
                )
                InfoNote("Detected within a couple of seconds while the screen is on. Needs usage access.")
            }
            is TriggerConfig.ScreenState -> {
                FieldLabel("When the screen turns")
                SegmentedChoice(
                    options = TriggerConfig.ScreenState.Event.entries,
                    selected = config.event,
                    label = {
                        when (it) {
                            TriggerConfig.ScreenState.Event.ON -> "On"
                            TriggerConfig.ScreenState.Event.OFF -> "Off"
                            TriggerConfig.ScreenState.Event.UNLOCKED -> "Unlocked"
                        }
                    },
                    onSelect = { onChange(config.copy(event = it)) },
                )
            }
            is DeviceTrigger -> DeviceTriggerForm(config, onChange, data)
            is TriggerConfig.NotificationPosted -> {
                FieldLabel("App")
                AppPickerField(config.packageName, data.apps) { onChange(config.copy(packageName = it)) }
                FieldLabel("Only if the title or text contains (optional)")
                OutlinedTextField(
                    value = config.textContains.orEmpty(),
                    onValueChange = { onChange(config.copy(textContains = it.ifEmpty { null })) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is TriggerConfig.DateTime -> DateTimeForm(config, onChange)
            is TriggerConfig.LocationArea -> LocationAreaForm(config, onChange, data)
            is TriggerConfig.LocationMoved -> {
                FieldLabel("Fire each time the phone has moved at least")
                IntSlider(
                    value = config.distanceMeters,
                    range = 100..20_000,
                    onChange = { onChange(config.copy(distanceMeters = it)) },
                    valueLabel = { ConfigText.distance(it) },
                    step = 100,
                )
                InfoNote(
                    "Measured in a straight line from where it last fired. Location is checked about once a minute " +
                        "using low-power positioning, so short distances may be detected late.",
                )
                LocationRequirementsNote(data.playServicesAvailable)
            }
            TriggerConfig.BootCompleted, TriggerConfig.Manual -> Text("No settings needed.")
        }
    }
}

@Composable
private fun LocationRequirementsNote(playServicesAvailable: Boolean) {
    InfoNote(
        if (playServicesAvailable) {
            "Needs location set to \"Allow all the time\". Uses Google Play services."
        } else {
            "This phone doesn't have Google Play services, which location triggers need. They won't fire on this device."
        },
    )
}

/** One-off date and time, or a regular interval starting then. */
@Composable
private fun DateTimeForm(config: TriggerConfig.DateTime, onChange: (TriggerConfig) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val start = remember(config.startAtEpochMillis) { Instant.ofEpochMilli(config.startAtEpochMillis).atZone(zone) }
    var pickingDate by remember { mutableStateOf(false) }

    fun update(date: LocalDate = start.toLocalDate(), hour: Int = start.hour, minute: Int = start.minute) {
        val millis = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        onChange(config.copy(startAtEpochMillis = millis))
    }

    FieldLabel(if (config.repeatIntervalMinutes == null) "Date" else "Starting on")
    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
        Text(start.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }
    FieldLabel("Time")
    TimeField(start.hour, start.minute, onChange = { h, m -> update(hour = h, minute = m) })

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Text("Repeat", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = config.repeatIntervalMinutes != null,
            onCheckedChange = { repeat -> onChange(config.copy(repeatIntervalMinutes = if (repeat) 60 else null)) },
        )
    }
    config.repeatIntervalMinutes?.let { minutes ->
        FieldLabel("Every")
        IntervalField(minutes) { onChange(config.copy(repeatIntervalMinutes = it)) }
        if (minutes < 10) {
            InfoNote("Android may hold back alarms more often than about every 10 minutes while the phone is idle.")
        }
        if (minutes % (24 * 60) == 0) {
            InfoNote("For \"every day at a set time\", the Time of day trigger stays correct across daylight saving changes.")
        }
    }
    if (config.repeatIntervalMinutes == null && config.startAtEpochMillis <= System.currentTimeMillis()) {
        InfoNote("This time has already passed, so the trigger won't fire.")
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utcMillis ->
                        update(date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

private enum class IntervalUnit(val label: String, val minutes: Int) { MINUTES("min", 1), HOURS("hours", 60), DAYS("days", 24 * 60) }

/** Interval as a number plus unit, stored as minutes. */
@Composable
private fun IntervalField(minutes: Int, onChange: (Int) -> Unit) {
    var unit by remember {
        mutableStateOf(IntervalUnit.entries.last { minutes % it.minutes == 0 })
    }
    var text by remember { mutableStateOf((minutes / unit.minutes).toString()) }
    fun emit(value: String, newUnit: IntervalUnit) {
        val count = value.toIntOrNull() ?: return
        val total = (count.toLong() * newUnit.minutes).coerceIn(1, TriggerConfig.DateTime.MAX_INTERVAL_MINUTES.toLong())
        onChange(total.toInt())
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw.filter(Char::isDigit).take(6)
                emit(text, unit)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        SegmentedChoice(
            options = IntervalUnit.entries,
            selected = unit,
            label = { it.label },
            onSelect = { newUnit ->
                unit = newUnit
                emit(text, newUnit)
            },
            modifier = Modifier.weight(2f).padding(start = 12.dp),
        )
    }
}

/** Area center (typed or current location), radius, and arrive/leave. */
@Composable
private fun LocationAreaForm(config: TriggerConfig.LocationArea, onChange: (TriggerConfig) -> Unit, data: FormData) {
    val context = LocalContext.current
    // Re-seed the text fields only when the location is filled in for the user, not while typing.
    var latitudeText by remember(data.locationVersion) { mutableStateOf(coordinateText(config.latitude, config)) }
    var longitudeText by remember(data.locationVersion) { mutableStateOf(coordinateText(config.longitude, config)) }

    FieldLabel("When the phone")
    SegmentedChoice(
        options = TriggerConfig.LocationArea.Event.entries,
        selected = config.event,
        label = { if (it == TriggerConfig.LocationArea.Event.ENTER) "Arrives" else "Leaves" },
        onSelect = { onChange(config.copy(event = it)) },
    )
    OutlinedTextField(
        value = config.label.orEmpty(),
        onValueChange = { onChange(config.copy(label = it.ifBlank { null })) },
        label = { Text("Place name (optional)") },
        placeholder = { Text("Home, Office…") },
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
                raw.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }?.let { onChange(config.copy(latitude = it)) }
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
                raw.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { onChange(config.copy(longitude = it)) }
            },
            label = { Text("Longitude") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
    if (ConfigRules.isComplete(config)) {
        TextButton(onClick = {
            val name = Uri.encode(config.label ?: "Cadence area")
            val uri = Uri.parse("geo:${config.latitude},${config.longitude}?q=${config.latitude},${config.longitude}($name)")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                // No maps app installed; nothing to show.
            }
        }) { Text("Check on a map") }
    }
    FieldLabel("Radius")
    IntSlider(
        value = config.radiusMeters,
        range = TriggerConfig.LocationArea.MIN_RADIUS_METERS..5_000,
        onChange = { onChange(config.copy(radiusMeters = it)) },
        valueLabel = { ConfigText.distance(it) },
        step = 50,
    )
    InfoNote("Arrivals and departures are usually detected within a few minutes. Radii of 150 m or more are most reliable.")
    LocationRequirementsNote(data.playServicesAvailable)
}

/** Empty until a real location is set (0,0 is the "not set" placeholder). */
private fun coordinateText(value: Double, config: TriggerConfig.LocationArea): String =
    if (!ConfigRules.isComplete(config)) "" else String.format(java.util.Locale.ROOT, "%.6f", value)

@Composable
private fun ConnectionChoice(selected: ConnectionEvent, onSelect: (ConnectionEvent) -> Unit) {
    SegmentedChoice(
        options = connectionOptions,
        selected = selected,
        label = { if (it == ConnectionEvent.CONNECTED) "Connected" else "Disconnected" },
        onSelect = onSelect,
    )
}

// endregion

// region Actions

@Composable
fun ActionForm(config: ActionConfig, onChange: (ActionConfig) -> Unit, data: FormData) {
    Column(Modifier.fillMaxWidth()) {
        when (config) {
            is ActionConfig.Wait -> {
                FieldLabel("Wait for")
                DurationField(config.durationMillis) { onChange(config.copy(durationMillis = it)) }
            }
            is ActionConfig.ShowNotification -> {
                TemplateTextField(config.title, { onChange(config.copy(title = it)) }, "Title", data.globalVariables)
                TemplateTextField(
                    config.text,
                    { onChange(config.copy(text = it)) },
                    "Text",
                    data.globalVariables,
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.padding(top = 8.dp),
                )
                InfoNote(PLACEHOLDER_HINT)
            }
            is ActionConfig.HttpRequest -> HttpRequestForm(config, onChange, data)
            is ActionConfig.SetVariable -> SetVariableForm(config, onChange, data)
            is ActionConfig.IfElse -> IfElseForm(config, onChange, data)
            is ActionConfig.Repeat -> RepeatForm(config, onChange, data)
            is ActionConfig.WaitForTrigger -> WaitForTriggerForm(config, onChange, data)
            is ActionConfig.RunMacro -> RunMacroForm(config, onChange, data)
            is ActionConfig.SetMacroEnabled -> SetMacroEnabledForm(config, onChange, data)
            is ActionConfig.LogMessage -> MessageForm(config.text, { onChange(config.copy(text = it)) }, "Message", data)
            is ActionConfig.ShowToast -> {
                MessageForm(config.text, { onChange(config.copy(text = it)) }, "Text", data)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Show for longer", modifier = Modifier.weight(1f))
                    Switch(checked = config.long, onCheckedChange = { onChange(config.copy(long = it)) })
                }
            }
            ActionConfig.ExitLoop, ActionConfig.NextIteration, ActionConfig.StopRun -> Text("No settings needed.")
            is ActionConfig.SetWifi -> {
                ToggleChoice(config.state) { onChange(config.copy(state = it)) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    InfoNote("Android 10+ doesn't let apps switch Wi-Fi. Cadence opens the Wi-Fi panel instead, or posts a tap-to-open notification when it's in the background.")
                }
            }
            is ActionConfig.SetBluetooth -> {
                ToggleChoice(config.state) { onChange(config.copy(state = it)) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    InfoNote("Android 13+ doesn't let apps switch Bluetooth. Cadence opens Bluetooth settings instead.")
                }
            }
            is ActionConfig.SetFlashlight -> ToggleChoice(config.state) { onChange(config.copy(state = it)) }
            is ActionConfig.SetRingerMode -> {
                SegmentedChoice(
                    options = RingerMode.entries,
                    selected = config.mode,
                    label = {
                        when (it) {
                            RingerMode.NORMAL -> "Sound"
                            RingerMode.VIBRATE -> "Vibrate"
                            RingerMode.SILENT -> "Silent"
                        }
                    },
                    onSelect = { onChange(config.copy(mode = it)) },
                )
                if (config.mode == RingerMode.SILENT) InfoNote("Silent mode needs Do Not Disturb access.")
            }
            is ActionConfig.SetVolume -> {
                FieldLabel("Stream")
                ChipChoice(
                    options = VolumeStream.entries,
                    selected = config.stream,
                    label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    onSelect = { onChange(config.copy(stream = it)) },
                )
                FieldLabel("Level")
                IntSlider(config.levelPercent, 0..100, { onChange(config.copy(levelPercent = it)) }, { "$it%" }, step = 5)
            }
            is ActionConfig.SetBrightness -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Adaptive brightness", modifier = Modifier.weight(1f))
                    Switch(checked = config.automatic, onCheckedChange = { onChange(config.copy(automatic = it)) })
                }
                if (!config.automatic) {
                    FieldLabel("Level")
                    IntSlider(config.levelPercent, 0..100, { onChange(config.copy(levelPercent = it)) }, { "$it%" }, step = 5)
                }
            }
            is ActionConfig.RefreshLocation -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("High accuracy (GPS)", modifier = Modifier.weight(1f))
                    Switch(checked = config.highAccuracy, onCheckedChange = { onChange(config.copy(highAccuracy = it)) })
                }
                FieldLabel("Give up after")
                IntSlider(config.timeoutSeconds, 5..120, { onChange(config.copy(timeoutSeconds = it)) }, { "$it s" }, step = 5)
                InfoNote(
                    "Location variables normally use the last known location, which can be minutes old. " +
                        "Put this before actions that use {location.latitude}, {location.speed_kmh} and so on. " +
                        "High accuracy is more precise outdoors but slower and uses more battery.",
                )
            }
            is ActionConfig.LaunchApp -> {
                FieldLabel("App")
                AppPickerField(config.packageName, data.apps) { onChange(config.copy(packageName = it)) }
                InfoNote("From the background, Android only allows this with \"Display over other apps\". Otherwise Cadence posts a tap-to-open notification.")
            }
            is ActionConfig.OpenUrl -> {
                TemplateTextField(
                    config.url,
                    { onChange(config.copy(url = it.trim())) },
                    "Link (http:// or https://)",
                    data.globalVariables,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            is ActionConfig.SpeakText -> {
                TemplateTextField(
                    config.text,
                    { onChange(config.copy(text = it)) },
                    "Text to speak",
                    data.globalVariables,
                    singleLine = false,
                    minLines = 2,
                )
                InfoNote("Example: \"Battery is at {battery.level} percent\".")
            }
            is ActionConfig.Vibrate -> {
                FieldLabel("Duration")
                IntSlider(
                    value = config.durationMillis.toInt(),
                    range = 100..5_000,
                    onChange = { onChange(config.copy(durationMillis = it.toLong())) },
                    valueLabel = { ConfigText.duration(it.toLong()) },
                    step = 100,
                )
            }
        }
    }
}

/** Method, URL, headers, body, and what to do with the response. */
@Composable
private fun HttpRequestForm(config: ActionConfig.HttpRequest, onChange: (ActionConfig) -> Unit, data: FormData) {
    FieldLabel("Method")
    ChipChoice(
        options = HttpMethod.entries,
        selected = config.method,
        label = { it.name },
        onSelect = { onChange(config.copy(method = it)) },
    )
    TemplateTextField(
        config.url,
        { onChange(config.copy(url = it.trim())) },
        "URL",
        data.globalVariables,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.padding(top = 12.dp),
    )
    if (config.url.trim().startsWith("http://", ignoreCase = true)) {
        InfoNote("http:// isn't encrypted. Use it only for devices on your own network.")
    }

    FieldLabel("Headers")
    config.headers.forEachIndexed { index, header ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            OutlinedTextField(
                value = header.name,
                onValueChange = { name -> onChange(config.copy(headers = config.headers.replaced(index, header.copy(name = name)))) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TemplateTextField(
                header.value,
                { value -> onChange(config.copy(headers = config.headers.replaced(index, header.copy(value = value)))) },
                "Value",
                data.globalVariables,
                modifier = Modifier.weight(1.4f).padding(start = 8.dp),
            )
            IconButton(onClick = { onChange(config.copy(headers = config.headers.filterIndexed { i, _ -> i != index })) }) {
                Icon(Icons.Filled.Close, contentDescription = "Remove header")
            }
        }
    }
    TextButton(onClick = { onChange(config.copy(headers = config.headers + HttpHeader("", ""))) }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("Add header", modifier = Modifier.padding(start = 4.dp))
    }

    FieldLabel("Query parameters")
    config.queryParameters.forEachIndexed { index, parameter ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            OutlinedTextField(
                value = parameter.name,
                onValueChange = { name ->
                    onChange(config.copy(queryParameters = config.queryParameters.replaced(index, parameter.copy(name = name))))
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TemplateTextField(
                parameter.value,
                { value -> onChange(config.copy(queryParameters = config.queryParameters.replaced(index, parameter.copy(value = value)))) },
                "Value",
                data.globalVariables,
                modifier = Modifier.weight(1.4f).padding(start = 8.dp),
            )
            IconButton(onClick = {
                onChange(config.copy(queryParameters = config.queryParameters.filterIndexed { i, _ -> i != index }))
            }) {
                Icon(Icons.Filled.Close, contentDescription = "Remove parameter")
            }
        }
    }
    TextButton(onClick = { onChange(config.copy(queryParameters = config.queryParameters + HttpParameter("", ""))) }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("Add query parameter", modifier = Modifier.padding(start = 4.dp))
    }
    if (config.queryParameters.isNotEmpty()) {
        val preview = config.queryParameters.filter { it.name.isNotBlank() }.joinToString("&") { "${it.name}=${it.value}" }
        InfoNote(
            "Sent as ?$preview with variables filled in and URL-encoded, " +
                "so values with spaces or & arrive intact. Example value: {battery.level}.",
        )
    }
    if (config.headers.isNotEmpty()) {
        InfoNote("Headers (including tokens) are saved with the macro and included in exported backups.")
    }

    if (config.method != HttpMethod.GET && config.method != HttpMethod.HEAD) {
        OutlinedTextField(
            value = config.contentType,
            onValueChange = { onChange(config.copy(contentType = it.trim())) },
            label = { Text("Content type") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        TemplateTextField(
            config.body.orEmpty(),
            { onChange(config.copy(body = it.ifEmpty { null })) },
            "Body",
            data.globalVariables,
            singleLine = false,
            minLines = 3,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    FieldLabel("Timeout")
    IntSlider(config.timeoutSeconds, 5..120, { onChange(config.copy(timeoutSeconds = it)) }, { "$it s" }, step = 5)

    FieldLabel("Response")
    VariableNameField(
        name = config.saveResponseTo.orEmpty(),
        onChange = { onChange(config.copy(saveResponseTo = it.ifBlank { null }?.takeIf(VariableNames::isValidUserName))) },
        label = "Save to variable (optional)",
        data = data,
    )
    if (config.saveResponseTo != null) {
        OutlinedTextField(
            value = config.jsonPath.orEmpty(),
            onValueChange = { onChange(config.copy(jsonPath = it.trim().ifEmpty { null })) },
            label = { Text("JSON path (optional), e.g. data.items[0].name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("Treat 4xx/5xx responses as failures", modifier = Modifier.weight(1f))
        Switch(checked = config.failOnErrorStatus, onCheckedChange = { onChange(config.copy(failOnErrorStatus = it)) })
    }
    InfoNote("Later actions can use {http.status}, {http.body} and {http.content_type}.")
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

/** Name, scope, operation, and value. */
@Composable
private fun SetVariableForm(config: ActionConfig.SetVariable, onChange: (ActionConfig) -> Unit, data: FormData) {
    VariableNameField(
        name = config.name,
        onChange = { onChange(config.copy(name = it)) },
        label = "Variable name",
        data = data,
    )
    FieldLabel("Keep it")
    SegmentedChoice(
        options = VariableScope.entries,
        selected = config.scope,
        label = { if (it == VariableScope.GLOBAL) "Saved (all macros)" else "This run only" },
        onSelect = { onChange(config.copy(scope = it)) },
    )
    FieldLabel("Operation")
    SegmentedChoice(
        options = ActionConfig.SetVariable.Operation.entries,
        selected = config.operation,
        label = {
            when (it) {
                ActionConfig.SetVariable.Operation.SET -> "Set"
                ActionConfig.SetVariable.Operation.ADD -> "Add number"
                ActionConfig.SetVariable.Operation.APPEND -> "Append"
            }
        },
        onSelect = { onChange(config.copy(operation = it)) },
    )
    TemplateTextField(
        config.value,
        { onChange(config.copy(value = it)) },
        if (config.operation == ActionConfig.SetVariable.Operation.ADD) "Number to add (negative subtracts)" else "Value",
        data.globalVariables,
        singleLine = config.operation == ActionConfig.SetVariable.Operation.ADD,
        modifier = Modifier.padding(top = 12.dp),
    )
    InfoNote("Use it elsewhere as {${config.name.ifBlank { "name" }}}. " + PLACEHOLDER_HINT)
}

/** Variable name with validation and a picker for existing variables. */
@Composable
private fun VariableNameField(
    name: String,
    onChange: (String) -> Unit,
    label: String,
    data: FormData,
    allowBuiltIns: Boolean = false,
) {
    var picking by remember { mutableStateOf(false) }
    fun isValid(value: String) = if (allowBuiltIns) VariableNames.isValidReference(value) else VariableNames.isValidUserName(value)
    OutlinedTextField(
        value = name,
        // Configs reject invalid names, so characters that would make the name invalid are ignored.
        onValueChange = { raw ->
            val value = raw.trim()
            if (value.isEmpty() || isValid(value)) onChange(value)
        },
        label = { Text(label) },
        singleLine = true,
        supportingText = {
            Text(if (allowBuiltIns) "Letters, digits, _ and . (for built-ins)" else "Letters, digits and _; can't start with a digit")
        },
        trailingIcon = {
            IconButton(onClick = { picking = true }) { Icon(Icons.Filled.DataObject, contentDescription = "Choose variable") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (picking) {
        VariablePickerDialog(
            globalVariables = data.globalVariables,
            allowBuiltIns = allowBuiltIns,
            onDismiss = { picking = false },
            onPick = { onChange(it); picking = false },
        )
    }
}

@Composable
private fun ToggleChoice(selected: ToggleState, onSelect: (ToggleState) -> Unit) {
    SegmentedChoice(
        options = ToggleState.entries,
        selected = selected,
        label = {
            when (it) {
                ToggleState.ON -> "On"
                ToggleState.OFF -> "Off"
                ToggleState.TOGGLE -> "Toggle"
            }
        },
        onSelect = onSelect,
    )
}

/** Whole seconds or minutes. */
@Composable
private fun DurationField(millis: Long, onChange: (Long) -> Unit) {
    val inMinutes = millis >= 60_000 && millis % 60_000 == 0L
    var useMinutes by remember { mutableStateOf(inMinutes) }
    val unitMillis = if (useMinutes) 60_000L else 1_000L
    var text by remember { mutableStateOf((millis / unitMillis).toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit).take(4)
                text = digits
                digits.toLongOrNull()?.let { onChange(it * unitMillis) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        SegmentedChoice(
            options = listOf(false, true),
            selected = useMinutes,
            label = { if (it) "min" else "sec" },
            onSelect = { minutes ->
                useMinutes = minutes
                text.toLongOrNull()?.let { onChange(it * if (minutes) 60_000L else 1_000L) }
            },
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}

// endregion

// region Constraints

@Composable
fun ConstraintForm(config: ConstraintConfig, onChange: (ConstraintConfig) -> Unit, data: FormData) {
    Column(Modifier.fillMaxWidth()) {
        when (config) {
            is ConstraintConfig.Group, is ConstraintConfig.MacroRunning, is ConstraintConfig.MacroEnabled,
            is ConstraintConfig.TimeSinceLastRun,
            -> MacroConstraintForm(config, onChange, data)
            is DeviceConstraint -> DeviceConstraintForm(config, onChange, data)
            is ConstraintConfig.VariableCompare -> {
                VariableNameField(
                    name = config.name,
                    onChange = { onChange(config.copy(name = it)) },
                    label = "Variable (yours, or built-in like battery.level)",
                    data = data,
                    allowBuiltIns = true,
                )
                FieldLabel("Condition")
                ChipChoice(
                    options = ConstraintConfig.VariableCompare.Operator.entries,
                    selected = config.operator,
                    label = {
                        when (it) {
                            ConstraintConfig.VariableCompare.Operator.EQUALS -> "is"
                            ConstraintConfig.VariableCompare.Operator.NOT_EQUALS -> "isn't"
                            ConstraintConfig.VariableCompare.Operator.CONTAINS -> "contains"
                            ConstraintConfig.VariableCompare.Operator.GREATER_THAN -> "greater than"
                            ConstraintConfig.VariableCompare.Operator.LESS_THAN -> "less than"
                            ConstraintConfig.VariableCompare.Operator.IS_SET -> "has a value"
                            ConstraintConfig.VariableCompare.Operator.IS_NOT_SET -> "is empty"
                        }
                    },
                    onSelect = { onChange(config.copy(operator = it)) },
                )
                if (config.operator != ConstraintConfig.VariableCompare.Operator.IS_SET &&
                    config.operator != ConstraintConfig.VariableCompare.Operator.IS_NOT_SET
                ) {
                    TemplateTextField(
                        config.value,
                        { onChange(config.copy(value = it)) },
                        "Value",
                        data.globalVariables,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                InfoNote("Numbers are compared as numbers (so 9 < 10); everything else as text, ignoring case.")
            }
            is ConstraintConfig.TimeRange -> {
                FieldLabel("From")
                TimeField(config.startMinuteOfDay / 60, config.startMinuteOfDay % 60, onChange = { h, m ->
                    onChange(config.copy(startMinuteOfDay = h * 60 + m))
                })
                FieldLabel("Until")
                TimeField(config.endMinuteOfDay / 60, config.endMinuteOfDay % 60, onChange = { h, m ->
                    onChange(config.copy(endMinuteOfDay = h * 60 + m))
                })
                InfoNote("Ranges can cross midnight, for example 22:00 to 06:00.")
            }
            is ConstraintConfig.DaysOfWeek -> {
                FieldLabel("Days")
                WeekdayChips(config.days, onChange = { if (it.isNotEmpty()) onChange(config.copy(days = it)) })
            }
            is ConstraintConfig.BatteryLevelRange -> {
                FieldLabel("Battery between ${config.minPercent}% and ${config.maxPercent}%")
                RangeSlider(
                    value = config.minPercent.toFloat()..config.maxPercent.toFloat(),
                    onValueChange = { range ->
                        val min = (range.start / 5).roundToInt() * 5
                        val max = (range.endInclusive / 5).roundToInt() * 5
                        onChange(config.copy(minPercent = min.coerceIn(0, 100), maxPercent = max.coerceIn(min, 100)))
                    },
                    valueRange = 0f..100f,
                    steps = 19,
                )
            }
            is ConstraintConfig.ChargingState -> SegmentedChoice(
                options = listOf(true, false),
                selected = config.charging,
                label = { if (it) "Charging" else "Not charging" },
                onSelect = { onChange(config.copy(charging = it)) },
            )
            is ConstraintConfig.WifiNetwork -> SegmentedChoice(
                options = listOf(true, false),
                selected = config.connected,
                label = { if (it) "On Wi-Fi" else "Not on Wi-Fi" },
                onSelect = { onChange(config.copy(connected = it)) },
            )
        }
    }
}

// endregion

// region App picker

/** Button showing the chosen app; opens a searchable list. */
@Composable
internal fun AppPickerField(packageName: String, apps: List<InstalledApp>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = apps.firstOrNull { it.packageName == packageName }?.label ?: packageName.ifBlank { "Choose an app" }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (open) {
        AppPickerDialog(apps = apps, onDismiss = { open = false }, onSelect = { onSelect(it); open = false })
    }
}

@Composable
private fun AppPickerDialog(apps: List<InstalledApp>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (apps.isEmpty()) {
                    Text("Loading apps…", modifier = Modifier.padding(top = 16.dp))
                }
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            },
                            modifier = Modifier.clickable { onSelect(app.packageName) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// endregion
