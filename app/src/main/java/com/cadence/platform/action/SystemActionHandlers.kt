package com.cadence.platform.action

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.cadence.R
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.action.RingerMode
import com.cadence.domain.action.ToggleState
import com.cadence.domain.action.VolumeStream
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.engine.render
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.ActionHandler
import com.cadence.platform.notification.CadenceNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.reflect.KClass

// Device setting actions. Where Android blocks a direct change for third-party apps, the
// handler opens the matching settings screen and returns ActionResult.Fallback, which the log
// shows to the user.

private fun ToggleState.resolve(current: Boolean): Boolean = when (this) {
    ToggleState.ON -> true
    ToggleState.OFF -> false
    ToggleState.TOGGLE -> !current
}

private fun ActivityLauncher.Outcome.toResult(startedMessage: String, promptMessage: String): ActionResult =
    when (this) {
        ActivityLauncher.Outcome.Started -> ActionResult.Fallback(startedMessage)
        ActivityLauncher.Outcome.PromptPosted -> ActionResult.Fallback(promptMessage)
        is ActivityLauncher.Outcome.Failed -> ActionResult.Failure(reason)
    }

/** [ActionConfig.ShowNotification]. */
class ShowNotificationActionHandler @Inject constructor(
    private val notifications: CadenceNotifications,
) : ActionHandler<ActionConfig.ShowNotification> {
    override val configType: KClass<ActionConfig.ShowNotification> = ActionConfig.ShowNotification::class

    override fun requiredPermissions(config: ActionConfig.ShowNotification) =
        setOf(PermissionRequirement.POST_NOTIFICATIONS)

    override suspend fun execute(config: ActionConfig.ShowNotification, context: ExecutionContext): ActionResult =
        if (notifications.postMacroNotification(context.render(config.title), context.render(config.text))) {
            ActionResult.Success()
        } else {
            ActionResult.Failure("Notifications are turned off for Cadence")
        }
}

/**
 * [ActionConfig.SetWifi]. Android 10+ doesn't let apps toggle Wi-Fi, so on those versions this
 * opens the system Wi-Fi panel instead.
 */
class SetWifiActionHandler @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val launcher: ActivityLauncher,
) : ActionHandler<ActionConfig.SetWifi> {
    override val configType: KClass<ActionConfig.SetWifi> = ActionConfig.SetWifi::class

    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    override suspend fun execute(config: ActionConfig.SetWifi, context: ExecutionContext): ActionResult {
        val target = config.state.resolve(current = wifiManager.isWifiEnabled)
        if (wifiManager.isWifiEnabled == target) return ActionResult.Success(if (target) "Wi-Fi was already on" else "Wi-Fi was already off")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            return if (wifiManager.setWifiEnabled(target)) ActionResult.Success() else ActionResult.Failure("Android refused to change Wi-Fi")
        }
        return launcher.launch(
            Intent(Settings.Panel.ACTION_WIFI),
            promptTitle = appContext.getString(if (target) R.string.prompt_wifi_on else R.string.prompt_wifi_off),
            promptText = appContext.getString(R.string.prompt_text, context.macro.name),
        ).toResult(
            startedMessage = "Android 10+ doesn't let apps change Wi-Fi, so the Wi-Fi panel was opened",
            promptMessage = "Android 10+ doesn't let apps change Wi-Fi, so a notification was posted to open the Wi-Fi panel",
        )
    }
}

/**
 * [ActionConfig.SetBluetooth]. Android 13+ doesn't let apps toggle Bluetooth, so on those
 * versions this opens Bluetooth settings instead.
 */
class SetBluetoothActionHandler @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val launcher: ActivityLauncher,
) : ActionHandler<ActionConfig.SetBluetooth> {
    override val configType: KClass<ActionConfig.SetBluetooth> = ActionConfig.SetBluetooth::class

    override fun requiredPermissions(config: ActionConfig.SetBluetooth): Set<PermissionRequirement> =
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S until Build.VERSION_CODES.TIRAMISU) {
            setOf(PermissionRequirement.BLUETOOTH_CONNECT)
        } else {
            emptySet()
        }

    @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is enforced by requiredPermissions on API 31-32.
    override suspend fun execute(config: ActionConfig.SetBluetooth, context: ExecutionContext): ActionResult {
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return ActionResult.Failure("This device has no Bluetooth")
        val target = config.state.resolve(current = adapter.isEnabled)
        if (adapter.isEnabled == target) {
            return ActionResult.Success(if (target) "Bluetooth was already on" else "Bluetooth was already off")
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return try {
                @Suppress("DEPRECATION")
                val accepted = if (target) adapter.enable() else adapter.disable()
                if (accepted) ActionResult.Success() else ActionResult.Failure("Android refused to change Bluetooth")
            } catch (e: SecurityException) {
                ActionResult.Failure("Needs the Nearby devices permission", e)
            }
        }
        return launcher.launch(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            promptTitle = appContext.getString(if (target) R.string.prompt_bluetooth_on else R.string.prompt_bluetooth_off),
            promptText = appContext.getString(R.string.prompt_text, context.macro.name),
        ).toResult(
            startedMessage = "Android 13+ doesn't let apps change Bluetooth, so Bluetooth settings were opened",
            promptMessage = "Android 13+ doesn't let apps change Bluetooth, so a notification was posted to open Bluetooth settings",
        )
    }
}

/** [ActionConfig.SetRingerMode]. Silent mode needs Do Not Disturb access. */
class SetRingerModeActionHandler @Inject constructor(
    @ApplicationContext appContext: Context,
) : ActionHandler<ActionConfig.SetRingerMode> {
    override val configType: KClass<ActionConfig.SetRingerMode> = ActionConfig.SetRingerMode::class

    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    override fun requiredPermissions(config: ActionConfig.SetRingerMode): Set<PermissionRequirement> =
        if (config.mode == RingerMode.SILENT) setOf(PermissionRequirement.DO_NOT_DISTURB_ACCESS) else emptySet()

    override suspend fun execute(config: ActionConfig.SetRingerMode, context: ExecutionContext): ActionResult = try {
        audioManager.ringerMode = when (config.mode) {
            RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
        }
        ActionResult.Success()
    } catch (e: SecurityException) {
        ActionResult.Failure("Changing the ringer during Do Not Disturb needs Do Not Disturb access", e)
    }
}

/** [ActionConfig.SetVolume]: percent of the stream's range. */
class SetVolumeActionHandler @Inject constructor(
    @ApplicationContext appContext: Context,
) : ActionHandler<ActionConfig.SetVolume> {
    override val configType: KClass<ActionConfig.SetVolume> = ActionConfig.SetVolume::class

    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    override suspend fun execute(config: ActionConfig.SetVolume, context: ExecutionContext): ActionResult {
        val stream = when (config.stream) {
            VolumeStream.RING -> AudioManager.STREAM_RING
            VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
            VolumeStream.ALARM -> AudioManager.STREAM_ALARM
            VolumeStream.CALL -> AudioManager.STREAM_VOICE_CALL
        }
        val max = audioManager.getStreamMaxVolume(stream)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audioManager.getStreamMinVolume(stream) else 0
        val level = (min + (max - min) * config.levelPercent / 100.0).roundToInt()
        return try {
            audioManager.setStreamVolume(stream, level, 0)
            ActionResult.Success("Set to $level of $max")
        } catch (e: SecurityException) {
            ActionResult.Failure("Changing this volume during Do Not Disturb needs Do Not Disturb access", e)
        }
    }
}

/** [ActionConfig.SetBrightness]. Needs "Modify system settings". */
class SetBrightnessActionHandler @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : ActionHandler<ActionConfig.SetBrightness> {
    override val configType: KClass<ActionConfig.SetBrightness> = ActionConfig.SetBrightness::class

    override fun requiredPermissions(config: ActionConfig.SetBrightness) = setOf(PermissionRequirement.WRITE_SETTINGS)

    override suspend fun execute(config: ActionConfig.SetBrightness, context: ExecutionContext): ActionResult {
        val resolver = appContext.contentResolver
        val mode = if (config.automatic) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }
        if (!Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)) {
            return ActionResult.Failure("Android refused to change the brightness mode")
        }
        if (!config.automatic) {
            val value = (config.levelPercent * MAX_BRIGHTNESS / 100.0).roundToInt().coerceIn(1, MAX_BRIGHTNESS)
            if (!Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)) {
                return ActionResult.Failure("Android refused to change the brightness")
            }
        }
        return ActionResult.Success()
    }

    private companion object {
        /** `Settings.System.SCREEN_BRIGHTNESS` is defined as 0..255. */
        const val MAX_BRIGHTNESS = 255
    }
}
