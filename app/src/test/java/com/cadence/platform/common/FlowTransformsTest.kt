package com.cadence.platform.common

import com.cadence.domain.trigger.TriggerConfig.AppLifecycle.Event.CLOSED
import com.cadence.domain.trigger.TriggerConfig.AppLifecycle.Event.LAUNCHED
import com.cadence.domain.trigger.TriggerConfig.BatteryLevel.Direction.FALLS_BELOW
import com.cadence.domain.trigger.TriggerConfig.BatteryLevel.Direction.RISES_ABOVE
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowTransformsTest {

    @Test
    fun `zipWithPrevious pairs each value with the one before`() = runTest {
        assertEquals(listOf(1 to 2, 2 to 3), flowOf(1, 2, 3).zipWithPrevious().toList())
        assertEquals(emptyList<Pair<Int, Int>>(), flowOf(1).zipWithPrevious().toList())
    }

    @Test
    fun `falls below fires once when crossing under the threshold`() = runTest {
        val levels = flowOf(25, 21, 20, 19, 18, 22, 19)

        assertEquals(listOf(19, 19), levels.thresholdCrossings(20, FALLS_BELOW).toList())
    }

    @Test
    fun `rises above fires once when crossing over the threshold`() = runTest {
        val levels = flowOf(78, 80, 81, 85, 79, 81)

        assertEquals(listOf(81, 81), levels.thresholdCrossings(80, RISES_ABOVE).toList())
    }

    @Test
    fun `starting below the threshold does not fire`() = runTest {
        assertEquals(emptyList<Int>(), flowOf(10, 9, 8).thresholdCrossings(20, FALLS_BELOW).toList())
    }

    @Test
    fun `transitions ignore the initial state and repeats`() = runTest {
        val states = flowOf(true, true, false, false, true, false)

        assertEquals(2, states.transitionsTo(false).toList().size)
        assertEquals(1, states.transitionsTo(true).toList().size)
    }

    @Test
    fun `app transitions detect launch and close of one package`() = runTest {
        val foreground = flowOf("com.maps", "com.launcher", "com.music", "com.music", "com.launcher", null, "com.music")

        assertEquals(listOf(LAUNCHED, CLOSED, LAUNCHED), foreground.appTransitions("com.music").toList())
    }

    @Test
    fun `app already in the foreground at start is a baseline`() = runTest {
        assertEquals(listOf(CLOSED), flowOf("com.music", "com.launcher").appTransitions("com.music").toList())
    }
}
