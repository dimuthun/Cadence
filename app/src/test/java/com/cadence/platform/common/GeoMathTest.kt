package com.cadence.platform.common

import com.cadence.domain.trigger.TriggerConfig
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    /** Colombo Fort and Galle Face Green: about 1.0 km apart. */
    private val fort = GeoFix(6.93440, 79.84280)
    private val galleFace = GeoFix(6.92550, 79.84530)

    /** Moves [meters] north of [from] (1° latitude ≈ 111.2 km). */
    private fun north(from: GeoFix, meters: Double, accuracy: Float = 10f) =
        GeoFix(from.latitude + meters / 111_195.0, from.longitude, accuracy)

    @Test
    fun `haversine matches known distances`() {
        assertEquals(1_024.0, distanceMeters(fort, galleFace), 15.0)
        // London to Paris is about 344 km.
        assertEquals(343_500.0, distanceMeters(GeoFix(51.5074, -0.1278), GeoFix(48.8566, 2.3522)), 2_000.0)
        assertEquals(0.0, distanceMeters(fort, fort), 0.001)
    }

    @Test
    fun `first fix is the anchor and small movements don't fire`() = runTest {
        val fixes = flowOf(fort, north(fort, 300.0), north(fort, 700.0))

        assertTrue(fixes.movements(thresholdMeters = 1_000).toList().isEmpty())
    }

    @Test
    fun `fires on reaching the threshold and re-anchors there`() = runTest {
        val fixes = flowOf(
            fort,
            north(fort, 600.0),
            north(fort, 1_050.0), // fires (1050 m from start)
            north(fort, 1_500.0), // 450 m from the new anchor: no
            north(fort, 2_100.0), // fires (1050 m from the new anchor)
        )

        val moved = fixes.movements(thresholdMeters = 1_000).toList()

        assertEquals(2, moved.size)
        moved.forEach { assertEquals(1_050.0, it, 5.0) }
    }

    @Test
    fun `inaccurate fixes are ignored`() = runTest {
        val fixes = flowOf(
            fort.copy(accuracyMeters = 2_000f), // too inaccurate to be the anchor
            fort,
            north(fort, 1_200.0, accuracy = 800f), // jumpy cell fix: ignored
            north(fort, 1_200.0, accuracy = 20f),
        )

        assertEquals(1, fixes.movements(thresholdMeters = 1_000).toList().size)
    }

    @Test
    fun `location configs validate their ranges`() {
        assertThrows(IllegalArgumentException::class.java) {
            TriggerConfig.LocationArea(91.0, 0.0, 200, TriggerConfig.LocationArea.Event.ENTER)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TriggerConfig.LocationArea(6.9, 79.8, 50, TriggerConfig.LocationArea.Event.ENTER)
        }
        assertThrows(IllegalArgumentException::class.java) { TriggerConfig.LocationMoved(10) }
    }
}
