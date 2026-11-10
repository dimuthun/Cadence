package com.cadence.platform.common

import com.cadence.domain.device.DevicePosition
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorMathTest {

    private fun sample(ms: Long, x: Float = 0f, y: Float = 0f, z: Float = 9.81f) = AccelerationSample(x, y, z, ms * 1_000_000)

    @Test
    fun `positions follow gravity`() {
        assertEquals(DevicePosition.FACE_UP, classifyPosition(0f, 0.5f, 9.7f))
        assertEquals(DevicePosition.FACE_DOWN, classifyPosition(0.3f, 0f, -9.7f))
        assertEquals(DevicePosition.PORTRAIT, classifyPosition(0.5f, 9.5f, 2f))
        assertEquals(DevicePosition.LANDSCAPE, classifyPosition(-9.5f, 0.5f, 2f))
        assertNull(classifyPosition(5.5f, 5.5f, 5.5f))
    }

    @Test
    fun `three hard jolts in a second make one shake`() = runTest {
        val jolt = 30f
        val samples = listOf(
            sample(0), sample(100, x = jolt), sample(250, x = -jolt), sample(400, x = jolt),
            // Still shaking during the cooldown: no second event.
            sample(600, x = -jolt), sample(800, x = jolt), sample(1_000, x = -jolt),
        )

        assertEquals(1, samples.asFlow().shakes(thresholdG = 2.2f).toList().size)
    }

    @Test
    fun `a single bump or slow jolts are not a shake`() = runTest {
        val bump = listOf(sample(0), sample(100, x = 30f), sample(200))
        val slow = listOf(sample(0, x = 30f), sample(1_500, x = 30f), sample(3_000, x = 30f))

        assertEquals(0, bump.asFlow().shakes(2.2f).toList().size)
        assertEquals(0, slow.asFlow().shakes(2.2f).toList().size)
    }

    @Test
    fun `turning face down fires once it has settled`() = runTest {
        val faceUp = (0L..1_000L step 100).map { sample(it) }
        val flipping = listOf(sample(1_100, x = 6f, z = 6f))
        val faceDown = (1_200L..2_400L step 100).map { sample(it, z = -9.81f) }

        val fired = (faceUp + flipping + faceDown).asFlow()
            .stablePositions()
            .positionChangesTo(DevicePosition.FACE_DOWN)
            .toList()

        assertEquals(1, fired.size)
    }

    @Test
    fun `starting face down is a baseline`() = runTest {
        val faceDown = (0L..2_000L step 100).map { sample(it, z = -9.81f) }

        assertEquals(0, faceDown.asFlow().stablePositions().positionChangesTo(DevicePosition.FACE_DOWN).toList().size)
    }

    @Test
    fun `picking the phone up and putting it back down fires again`() = runTest {
        val down1 = (0L..1_000L step 100).map { sample(it, z = -9.81f) }
        val inHand = (1_100L..2_100L step 100).map { sample(it, x = 5.5f, y = 5.5f, z = 5.5f) }
        val down2 = (2_200L..3_200L step 100).map { sample(it, z = -9.81f) }

        val fired = (down1 + inHand + down2).asFlow().stablePositions().positionChangesTo(DevicePosition.FACE_DOWN).toList()

        assertEquals(1, fired.size)
    }
}
