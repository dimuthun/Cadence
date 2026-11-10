package com.cadence.data.serialization

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.RingerMode
import com.cadence.domain.action.ToggleState
import com.cadence.domain.action.VolumeStream
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.constraint.DeviceConstraint
import com.cadence.domain.device.DevicePosition
import com.cadence.domain.device.DeviceSwitch
import com.cadence.domain.device.NetworkType
import com.cadence.domain.trigger.DeviceTrigger
import com.cadence.domain.model.Weekday
import com.cadence.domain.trigger.ConnectionEvent
import com.cadence.domain.trigger.TriggerConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CadenceJsonTest {

    private val json = CadenceJson.instance

    private val triggers: List<TriggerConfig> = listOf(
        TriggerConfig.Manual,
        TriggerConfig.TimeOfDay(hour = 7, minute = 30, days = setOf(Weekday.MONDAY, Weekday.FRIDAY)),
        TriggerConfig.BatteryLevel(thresholdPercent = 20, direction = TriggerConfig.BatteryLevel.Direction.FALLS_BELOW),
        TriggerConfig.PowerConnection(ConnectionEvent.CONNECTED),
        TriggerConfig.WifiConnection(ConnectionEvent.CONNECTED),
        TriggerConfig.WifiConnection(ConnectionEvent.DISCONNECTED),
        TriggerConfig.BluetoothConnection(ConnectionEvent.CONNECTED, deviceAddress = "00:11:22:33:44:55", deviceName = "Car"),
        TriggerConfig.AppLifecycle("com.example.maps", TriggerConfig.AppLifecycle.Event.LAUNCHED),
        TriggerConfig.BootCompleted,
        TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.OFF),
        TriggerConfig.NotificationPosted("com.example.chat", textContains = "urgent"),
        TriggerConfig.DateTime(startAtEpochMillis = 1_794_300_000_000, repeatIntervalMinutes = 90),
        TriggerConfig.DateTime(startAtEpochMillis = 1_794_300_000_000),
        TriggerConfig.LocationArea(6.927079, 79.861244, radiusMeters = 250, event = TriggerConfig.LocationArea.Event.EXIT, label = "Office"),
        TriggerConfig.LocationMoved(distanceMeters = 2_000),
        TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.UNLOCKED),
        DeviceTrigger.SwitchChanged(DeviceSwitch.AIRPLANE_MODE, turnedOn = false),
        DeviceTrigger.RingerModeChanged(RingerMode.SILENT),
        DeviceTrigger.Shake(DeviceTrigger.Shake.Sensitivity.HIGH),
        DeviceTrigger.PositionChanged(DevicePosition.FACE_DOWN),
        DeviceTrigger.Proximity(near = false),
        DeviceTrigger.LightLevel(50, TriggerConfig.BatteryLevel.Direction.RISES_ABOVE),
        DeviceTrigger.BatteryTemperature(42, TriggerConfig.BatteryLevel.Direction.RISES_ABOVE),
        DeviceTrigger.AppPackageChanged(DeviceTrigger.AppPackageChanged.Event.UPDATED, "com.example.maps"),
        DeviceTrigger.NetworkTypeChanged(NetworkType.CELLULAR),
        DeviceTrigger.NetworkTypeChanged(),
        DeviceTrigger.IpAddressChanged,
        DeviceTrigger.MusicTrackChanged("com.example.music"),
        DeviceTrigger.Sun(DeviceTrigger.Sun.Event.SUNRISE, 6.9271, 79.8612, offsetMinutes = -30, label = "Home"),
        DeviceTrigger.DayOfMonth(days = setOf(1, 15), lastDay = true, hour = 8, minute = 30),
    )

    private val actions: List<ActionConfig> = listOf(
        ActionConfig.Wait(durationMillis = 1_500),
        ActionConfig.ShowNotification(title = "Leaving home", text = "Wi-Fi off"),
        ActionConfig.SetWifi(ToggleState.OFF),
        ActionConfig.SetBluetooth(ToggleState.TOGGLE),
        ActionConfig.SetRingerMode(RingerMode.VIBRATE),
        ActionConfig.SetVolume(VolumeStream.MEDIA, levelPercent = 40),
        ActionConfig.SetBrightness(levelPercent = 70),
        ActionConfig.LaunchApp("com.example.music"),
        ActionConfig.OpenUrl("https://example.com"),
        ActionConfig.SpeakText("Good morning", languageTag = "en-GB"),
        ActionConfig.Vibrate(durationMillis = 300),
        ActionConfig.SetFlashlight(ToggleState.ON),
        ActionConfig.HttpRequest(
            url = "https://api.example.com/log?battery={battery.level}",
            method = com.cadence.domain.action.HttpMethod.POST,
            headers = listOf(com.cadence.domain.action.HttpHeader("Authorization", "Bearer {token}")),
            queryParameters = listOf(com.cadence.domain.action.HttpParameter("speed", "{location.speed_kmh}")),
            body = """{"lat":{location.latitude}}""",
            saveResponseTo = "lastReply",
            jsonPath = "data.id",
        ),
        ActionConfig.SetVariable("counter", "1", operation = ActionConfig.SetVariable.Operation.ADD),
        ActionConfig.IfElse(
            branches = listOf(
                com.cadence.domain.action.ConditionalBlock(
                    conditions = listOf(ConstraintConfig.ChargingState(true)),
                    actions = listOf(ActionConfig.Vibrate(100)),
                ),
            ),
            elseActions = listOf(
                ActionConfig.Repeat(
                    mode = ActionConfig.Repeat.Mode.UNTIL_EXIT,
                    actions = listOf(ActionConfig.NextIteration, ActionConfig.ExitLoop),
                ),
            ),
        ),
        ActionConfig.StopRun,
        ActionConfig.Repeat(count = 5, actions = listOf(ActionConfig.LogMessage("#{loop.index}"))),
        ActionConfig.ExitLoop,
        ActionConfig.NextIteration,
        ActionConfig.WaitForTrigger(TriggerConfig.PowerConnection(ConnectionEvent.CONNECTED), timeoutSeconds = 600),
        ActionConfig.RunMacro(macroId = 7, checkConstraints = true),
        ActionConfig.SetMacroEnabled(macroId = null, state = ToggleState.TOGGLE),
        ActionConfig.LogMessage("Battery {battery.level}"),
        ActionConfig.ShowToast("Hi", long = true),
        ActionConfig.RefreshLocation(timeoutSeconds = 45, highAccuracy = false),
    )

    private val constraints: List<ConstraintConfig> = listOf(
        ConstraintConfig.TimeRange(startMinuteOfDay = 22 * 60, endMinuteOfDay = 6 * 60),
        ConstraintConfig.BatteryLevelRange(minPercent = 10, maxPercent = 90),
        ConstraintConfig.ChargingState(charging = false),
        ConstraintConfig.WifiNetwork(connected = false),
        ConstraintConfig.DaysOfWeek(setOf(Weekday.SATURDAY, Weekday.SUNDAY)),
        ConstraintConfig.VariableCompare("battery.level", ConstraintConfig.VariableCompare.Operator.LESS_THAN, "30"),
        ConstraintConfig.Group(
            ConstraintConfig.Group.Operator.NONE,
            listOf(ConstraintConfig.ChargingState(true), ConstraintConfig.Group(constraints = listOf(ConstraintConfig.WifiNetwork()))),
        ),
        ConstraintConfig.MacroRunning(macroId = 3, running = false),
        ConstraintConfig.MacroEnabled(macroId = 3),
        ConstraintConfig.TimeSinceLastRun(minutes = 30),
        DeviceConstraint.SwitchState(DeviceSwitch.DEVICE_LOCKED, on = false),
        DeviceConstraint.RingerModeIs(setOf(RingerMode.VIBRATE, RingerMode.SILENT)),
        DeviceConstraint.NetworkTypeIs(setOf(NetworkType.WIFI, NetworkType.ETHERNET)),
        DeviceConstraint.PositionIs(DevicePosition.LANDSCAPE),
        DeviceConstraint.AppInForeground("com.example.maps", inForeground = false),
        DeviceConstraint.Daylight(daylight = false, latitude = 51.5, longitude = -0.12),
        DeviceConstraint.DaysOfMonth(lastDay = true),
        DeviceConstraint.Months(setOf(6, 7, 8)),
    )

    private fun <T> roundTrip(serializer: KSerializer<T>, values: List<T>): List<T> {
        val listSerializer = ListSerializer(serializer)
        return json.decodeFromString(listSerializer, json.encodeToString(listSerializer, values))
    }

    /** Serial names of every concrete subtype of a sealed hierarchy. */
    private fun subtypeNames(serializer: KSerializer<*>): Set<String> =
        serializer.descriptor.getElementDescriptor(1).elementNames.toSet()

    private fun <T> encodedTypes(serializer: KSerializer<T>, values: List<T>): Set<String> =
        values.map { json.encodeToJsonElement(serializer, it).jsonObject.getValue("type").jsonPrimitive.content }.toSet()

    @Test
    fun `triggers round trip`() {
        assertEquals(triggers, roundTrip(TriggerConfig.serializer(), triggers))
    }

    @Test
    fun `actions round trip`() {
        assertEquals(actions, roundTrip(ActionConfig.serializer(), actions))
    }

    @Test
    fun `constraints round trip`() {
        assertEquals(constraints, roundTrip(ConstraintConfig.serializer(), constraints))
    }

    @Test
    fun `samples cover every subtype so new types get round-trip coverage`() {
        assertEquals(subtypeNames(TriggerConfig.serializer()), encodedTypes(TriggerConfig.serializer(), triggers))
        assertEquals(subtypeNames(ActionConfig.serializer()), encodedTypes(ActionConfig.serializer(), actions))
        assertEquals(subtypeNames(ConstraintConfig.serializer()), encodedTypes(ConstraintConfig.serializer(), constraints))
    }

    @Test
    fun `encoded format uses stable type names and omits nulls`() {
        assertEquals(
            """{"type":"trigger.bluetooth_connection","event":"CONNECTED","deviceAddress":"00:11:22:33:44:55"}""",
            json.encodeToString(
                TriggerConfig.serializer(),
                TriggerConfig.BluetoothConnection(ConnectionEvent.CONNECTED, deviceAddress = "00:11:22:33:44:55"),
            ),
        )
        assertEquals(
            """{"type":"trigger.bluetooth_connection","event":"DISCONNECTED"}""",
            json.encodeToString(TriggerConfig.serializer(), TriggerConfig.BluetoothConnection(ConnectionEvent.DISCONNECTED)),
        )
        assertEquals(
            """{"type":"trigger.boot_completed"}""",
            json.encodeToString(TriggerConfig.serializer(), TriggerConfig.BootCompleted),
        )
    }

    @Test
    fun `defaults are written explicitly`() {
        assertEquals(
            """{"type":"action.vibrate","durationMillis":500}""",
            json.encodeToString(ActionConfig.serializer(), ActionConfig.Vibrate()),
        )
    }

    @Test
    fun `unknown keys from newer versions are ignored`() {
        val decoded = json.decodeFromString(
            ActionConfig.serializer(),
            """{"type":"action.vibrate","durationMillis":300,"intensity":"high"}""",
        )
        assertEquals(ActionConfig.Vibrate(300), decoded)
    }

    @Test
    fun `legacy ssid field on wifi configs is ignored`() {
        assertEquals(
            TriggerConfig.WifiConnection(ConnectionEvent.CONNECTED),
            json.decodeFromString(
                TriggerConfig.serializer(),
                """{"type":"trigger.wifi_connection","event":"CONNECTED","ssid":"HomeNet"}""",
            ),
        )
    }

    @Test
    fun `http requests saved before query parameters existed still decode`() {
        val decoded = json.decodeFromString(
            ActionConfig.serializer(),
            """{"type":"action.http_request","url":"https://x.test","method":"GET"}""",
        ) as ActionConfig.HttpRequest

        assertEquals(emptyList<com.cadence.domain.action.HttpParameter>(), decoded.queryParameters)
    }

    @Test
    fun `device trigger format is stable`() {
        assertEquals(
            """{"type":"trigger.device_switch","switch":"HEADPHONES","turnedOn":true}""",
            json.encodeToString(TriggerConfig.serializer(), DeviceTrigger.SwitchChanged(DeviceSwitch.HEADPHONES)),
        )
    }

    @Test
    fun `switches that can't be triggers are rejected on decode`() {
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(TriggerConfig.serializer(), """{"type":"trigger.device_switch","switch":"DEVICE_LOCKED"}""")
        }
    }

    @Test
    fun `missing optional fields use defaults`() {
        assertEquals(
            ActionConfig.Vibrate(500),
            json.decodeFromString(ActionConfig.serializer(), """{"type":"action.vibrate"}"""),
        )
    }

    @Test
    fun `invalid values are rejected on decode`() {
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(TriggerConfig.serializer(), """{"type":"trigger.time_of_day","hour":25,"minute":0}""")
        }
    }

    @Test
    fun `unknown type fails with a serialization exception`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(TriggerConfig.serializer(), """{"type":"trigger.from_the_future"}""")
        }
    }
}
