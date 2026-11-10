package com.cadence.presentation.catalog

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.Weekday
import com.cadence.domain.trigger.TriggerConfig
import kotlinx.serialization.descriptors.elementNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    private val text = ConfigText()

    /** Serial names of every subtype, from the sealed serializer. */
    private fun subtypeCount(descriptor: kotlinx.serialization.descriptors.SerialDescriptor) =
        descriptor.getElementDescriptor(1).elementNames.count()

    @Test
    fun `every config type is pickable except the internal manual trigger`() {
        assertEquals(subtypeCount(TriggerConfig.serializer().descriptor) - 1, TriggerCatalog.size)
        assertEquals(subtypeCount(ActionConfig.serializer().descriptor), ActionCatalog.size)
        assertEquals(subtypeCount(ConstraintConfig.serializer().descriptor), ConstraintCatalog.size)
    }

    @Test
    fun `default configs map back to their own entry and can be described`() {
        TriggerCatalog.forEach { entry ->
            val config = entry.create()
            assertEquals(entry, Catalog.entryFor(config))
            assertTrue(text.trigger(config).isNotBlank())
        }
        ActionCatalog.forEach { entry ->
            val config = entry.create()
            assertEquals(entry, Catalog.entryFor(config))
            assertTrue(text.action(config).isNotBlank())
        }
        ConstraintCatalog.forEach { entry ->
            val config = entry.create()
            assertEquals(entry, Catalog.entryFor(config))
            assertTrue(text.constraint(config).isNotBlank())
        }
    }

    @Test
    fun `configs needing user input start incomplete`() {
        assertFalse(ConfigRules.isComplete(ActionConfig.LaunchApp("")))
        assertFalse(ConfigRules.isComplete(ActionConfig.OpenUrl("https://")))
        assertFalse(ConfigRules.isComplete(ActionConfig.OpenUrl("javascript:alert(1)")))
        assertTrue(ConfigRules.isComplete(ActionConfig.OpenUrl("https://example.com/page?q=1")))
        assertFalse(ConfigRules.isComplete(TriggerConfig.NotificationPosted("")))
        assertTrue(ConfigRules.isComplete(TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.ON)))
    }

    @Test
    fun `summaries read naturally`() {
        assertEquals("At 07:05, weekdays", text.trigger(TriggerConfig.TimeOfDay(7, 5, Weekday.entries.take(5).toSet())))
        assertEquals("At 22:00", text.trigger(TriggerConfig.TimeOfDay(22, 0)))
        assertEquals("Between 22:00 and 06:00", text.constraint(ConstraintConfig.TimeRange(22 * 60, 6 * 60)))
        assertEquals("Wait 2 min", text.action(ActionConfig.Wait(120_000)))
        assertEquals("Wait 1.5 s", text.action(ActionConfig.Wait(1_500)))
        assertEquals("On Mon, Wed", text.constraint(ConstraintConfig.DaysOfWeek(setOf(Weekday.WEDNESDAY, Weekday.MONDAY))))
        assertEquals("Open Maps", ConfigText { "Maps" }.action(ActionConfig.LaunchApp("com.example.maps")))
    }

    @Test
    fun `new trigger summaries`() {
        val utc = ConfigText(zone = java.time.ZoneOffset.UTC, locale = java.util.Locale.US)
        val start = java.time.Instant.parse("2026-11-12T08:00:00Z").toEpochMilli()

        assertEquals("On Nov 12, 2026 at 08:00", utc.trigger(TriggerConfig.DateTime(start)))
        assertEquals("Every 30 min from Nov 12, 2026 at 08:00", utc.trigger(TriggerConfig.DateTime(start, 30)))
        assertEquals("Every 2 hours from Nov 12, 2026 at 08:00", utc.trigger(TriggerConfig.DateTime(start, 120)))
        assertEquals(
            "Arriving at Home (250 m)",
            utc.trigger(TriggerConfig.LocationArea(6.9, 79.8, 250, TriggerConfig.LocationArea.Event.ENTER, label = "Home")),
        )
        assertEquals(
            "Leaving 6.90000, 79.80000 (1.5 km)",
            utc.trigger(TriggerConfig.LocationArea(6.9, 79.8, 1_500, TriggerConfig.LocationArea.Event.EXIT)),
        )
        assertEquals("Moved 2 km", utc.trigger(TriggerConfig.LocationMoved(2_000)))
    }

    @Test
    fun `device summaries`() {
        val text = ConfigText(appLabel = { if (it == "com.example.maps") "Maps" else it })
        assertEquals("Headphones connected", text.trigger(com.cadence.domain.trigger.DeviceTrigger.SwitchChanged(com.cadence.domain.device.DeviceSwitch.HEADPHONES)))
        assertEquals(
            "Airplane mode turns off",
            text.trigger(com.cadence.domain.trigger.DeviceTrigger.SwitchChanged(com.cadence.domain.device.DeviceSwitch.AIRPLANE_MODE, false)),
        )
        assertEquals(
            "30 min before sunrise at Home",
            text.trigger(com.cadence.domain.trigger.DeviceTrigger.Sun(com.cadence.domain.trigger.DeviceTrigger.Sun.Event.SUNRISE, 6.9, 79.8, -30, "Home")),
        )
        assertEquals(
            "On the 1st, 22nd and last day at 09:00",
            text.trigger(com.cadence.domain.trigger.DeviceTrigger.DayOfMonth(setOf(22, 1), lastDay = true)),
        )
        assertEquals(
            "Maps updated",
            text.trigger(
                com.cadence.domain.trigger.DeviceTrigger.AppPackageChanged(
                    com.cadence.domain.trigger.DeviceTrigger.AppPackageChanged.Event.UPDATED,
                    "com.example.maps",
                ),
            ),
        )
        assertEquals("While dark theme is on", text.constraint(com.cadence.domain.constraint.DeviceConstraint.SwitchState(com.cadence.domain.device.DeviceSwitch.DARK_THEME)))
        assertEquals("While Wi-Fi is off", text.constraint(com.cadence.domain.constraint.DeviceConstraint.SwitchState(com.cadence.domain.device.DeviceSwitch.WIFI, on = false)))
        assertEquals("In Jun, Jul, Aug", text.constraint(com.cadence.domain.constraint.DeviceConstraint.Months(setOf(8, 6, 7))))
        assertEquals("11th", DeviceText.ordinal(11))
        assertEquals("23rd", DeviceText.ordinal(23))
    }

    @Test
    fun `sun items need a place and app items need an app`() {
        assertFalse(ConfigRules.isComplete(com.cadence.domain.trigger.DeviceTrigger.Sun(com.cadence.domain.trigger.DeviceTrigger.Sun.Event.SUNSET)))
        assertTrue(ConfigRules.isComplete(com.cadence.domain.constraint.DeviceConstraint.Daylight(latitude = 6.9, longitude = 79.8)))
        assertFalse(ConfigRules.isComplete(com.cadence.domain.constraint.DeviceConstraint.AppInForeground("")))
        assertFalse(ConfigRules.hasSettings(com.cadence.domain.trigger.DeviceTrigger.IpAddressChanged))
    }

    @Test
    fun `location area needs a real location before it can be added`() {
        assertFalse(ConfigRules.isComplete(TriggerConfig.LocationArea(0.0, 0.0, 200, TriggerConfig.LocationArea.Event.ENTER)))
        assertTrue(ConfigRules.isComplete(TriggerConfig.LocationArea(6.9, 79.8, 200, TriggerConfig.LocationArea.Event.ENTER)))
    }
}
