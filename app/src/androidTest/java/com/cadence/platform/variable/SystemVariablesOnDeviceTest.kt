package com.cadence.platform.variable

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadence.core.time.SystemDefaultZoneClock
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.model.Macro
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.domain.variable.SystemVariables
import com.cadence.platform.location.FusedLocationSource
import com.cadence.platform.permission.AndroidPermissionChecker
import com.cadence.platform.monitor.MediaSessionMonitor
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads every device-backed built-in variable on a real phone: each must be recognized (not
 * `null`) and none may throw. Values themselves vary by device and state.
 */
@RunWith(AndroidJUnit4::class)
class SystemVariablesOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val permissions = AndroidPermissionChecker(context)
    private val source = AndroidSystemVariableSource(
        context,
        permissions,
        FusedLocationSource(context),
        MediaSessionMonitor(context, CoroutineScope(Dispatchers.Default), permissions),
        SystemDefaultZoneClock,
    )
    private val now = Instant.now()
    private val run = ExecutionContext(
        macro = Macro(name = "Device test", triggers = emptyList(), actions = emptyList(), createdAt = now, updatedAt = now),
        trigger = TriggerEvent(TriggerConfig.Manual, now),
        startedAt = now,
    )

    @Test
    fun everyDeviceVariableIsRecognized() = runTest {
        val deviceKeys = SystemVariables.all
            .filter { it.group != SystemVariables.GROUP_RUN }
            .map { it.key }

        val unknown = deviceKeys.filter { source.value(it, run) == null }

        assertEquals("Keys the device source doesn't handle: $unknown", emptyList<String>(), unknown)
    }

    @Test
    fun valuesHaveTheDocumentedShape() = runTest {
        val battery = source.value("battery.level", run)!!.toInt()
        assertTrue(battery in 0..100)
        assertTrue(source.value("network.type", run) in setOf("wifi", "cellular", "ethernet", "vpn", "other", "none"))
        assertTrue(source.value("display.orientation", run) in setOf("portrait", "landscape"))
        assertTrue(source.value("time.month", run)!!.toInt() in 1..12)
        assertNotNull(source.value("storage.free_gb", run)!!.toDoubleOrNull())
        assertTrue(source.value("memory.total_mb", run)!!.toLong() > 0)
        assertEquals(null, source.value("battery.nonexistent", run))
    }
}
