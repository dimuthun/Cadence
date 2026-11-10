package com.cadence.domain.plugin

import com.cadence.domain.trigger.ConnectionEvent
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.testutil.FakeTriggerHandler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRegistryTest {

    private val screenHandler = FakeTriggerHandler(TriggerConfig.ScreenState::class)
    private val bootHandler = FakeTriggerHandler(TriggerConfig.BootCompleted::class)

    @Test
    fun `finds the handler for a config by its type`() {
        val registry = TriggerRegistry(setOf(screenHandler, bootHandler))

        assertSame(screenHandler, registry.handlerFor(TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.OFF)))
        assertSame(bootHandler, registry.handlerFor(TriggerConfig.BootCompleted))
    }

    @Test
    fun `returns null for unregistered types`() {
        val registry = TriggerRegistry(setOf(screenHandler))

        assertNull(registry.handlerFor(TriggerConfig.PowerConnection(ConnectionEvent.CONNECTED)))
        assertFalse(registry.supports(TriggerConfig.PowerConnection::class))
        assertTrue(registry.supports(TriggerConfig.ScreenState::class))
    }

    @Test
    fun `rejects two handlers for the same type`() {
        val duplicate = FakeTriggerHandler(TriggerConfig.ScreenState::class)

        assertThrows(IllegalArgumentException::class.java) {
            TriggerRegistry(setOf(screenHandler, duplicate))
        }
    }
}
