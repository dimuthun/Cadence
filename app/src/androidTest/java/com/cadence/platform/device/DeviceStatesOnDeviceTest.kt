package com.cadence.platform.device

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadence.domain.device.DeviceSwitch
import com.cadence.domain.device.NetworkType
import com.cadence.platform.monitor.DeviceSwitchMonitor
import com.cadence.platform.monitor.NetworkStateMonitor
import com.cadence.platform.monitor.ScreenStateMonitor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads every device switch and registers each switch's listener on a real phone. Values vary
 * by device; what matters is that nothing throws (e.g. a missing permission or a broadcast that
 * can't be registered) and that each monitor reports its current state straight away.
 */
@RunWith(AndroidJUnit4::class)
class DeviceStatesOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scope = CoroutineScope(Dispatchers.Default)

    @Test
    fun everySwitchCanBeRead() = runBlocking {
        val reader = AndroidDeviceStateReader(context)
        val unreadable = DeviceSwitch.entries.filter { reader.switchState(it) == null }
        // Only hardware the phone may lack can be unknown.
        assertTrue("Unreadable: $unreadable", unreadable.all { it == DeviceSwitch.NFC || it == DeviceSwitch.BLUETOOTH })
        assertNotNull(reader.ringerMode())
        assertTrue(reader.networkType() in NetworkType.entries)
    }

    @Test
    fun everySwitchMonitorReportsItsCurrentState() = runBlocking {
        val reader = AndroidDeviceStateReader(context)
        val monitor = DeviceSwitchMonitor(context, scope, ScreenStateMonitor(context, scope))
        DeviceSwitch.entries.filter { reader.switchState(it) != null }.forEach { switch ->
            val first = withTimeout(5.seconds) { monitor.states(switch).first() }
            assertEquals("$switch", reader.switchState(switch), first)
        }
        withTimeout(5.seconds) { monitor.ringerModes.first() }
    }

    @Test
    fun networkMonitorReportsTheCurrentNetwork() = runBlocking {
        val state = withTimeout(10.seconds) { NetworkStateMonitor(context, scope).state.first() }
        assertEquals(AndroidDeviceStateReader(context).networkType(), state.type)
    }
}
