package com.cadence.presentation.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.cadence.platform.permission.SpecialAccess

/**
 * Returns a function that grants a [SpecialAccess]. Runtime permissions use the system dialog,
 * falling back to the Settings screen if the user denied it. Special access always opens the
 * matching Settings screen. [onReturn] runs when the user comes back, so callers can refresh.
 *
 * Location access always shows its prominent disclosure first (Google Play policy), and
 * background location asks for foreground location first, which Android requires.
 */
@Composable
fun rememberAccessRequester(onReturn: () -> Unit = {}): (SpecialAccess) -> Unit {
    val context = LocalContext.current
    val latestOnReturn by rememberUpdatedState(onReturn)
    var pendingRuntime by remember { mutableStateOf<SpecialAccess?>(null) }
    var disclosure by remember { mutableStateOf<SpecialAccess?>(null) }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        latestOnReturn()
    }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val access = pendingRuntime
        pendingRuntime = null
        if (!granted && access != null) {
            // Denied (possibly permanently): the only remaining path is the Settings screen.
            settingsLauncher.launchSafely(context, access.settingsIntent(context))
        } else {
            latestOnReturn()
        }
    }

    fun launch(access: SpecialAccess) {
        val effective = if (access == SpecialAccess.BACKGROUND_LOCATION && !context.hasFineLocation()) {
            SpecialAccess.LOCATION
        } else {
            access
        }
        val permission = effective.runtimePermission
        if (permission != null) {
            pendingRuntime = effective
            runtimeLauncher.launch(permission)
        } else {
            settingsLauncher.launchSafely(context, effective.settingsIntent(context))
        }
    }

    disclosure?.let { access ->
        AccessRationaleDialog(
            access = access,
            onGrant = {
                disclosure = null
                launch(access)
            },
            onDismiss = { disclosure = null },
        )
    }

    return { access ->
        if (access.requiresProminentDisclosure) disclosure = access else launch(access)
    }
}

private fun Context.hasFineLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Some OEMs don't implement every Settings screen; fall back to the app info screen. */
private fun ActivityResultLauncher<Intent>.launchSafely(context: Context, intent: Intent) {
    try {
        launch(intent)
    } catch (e: ActivityNotFoundException) {
        launch(SpecialAccess.appDetails(context))
    }
}

/**
 * Explanation shown before asking for a permission, as required before any system prompt or
 * Settings redirect.
 */
@Composable
fun AccessRationaleDialog(access: SpecialAccess, onGrant: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(access.title.substringBefore(" (")) },
        text = {
            Text(
                access.rationale +
                    if (access.runtimePermission == null) "\n\nYou'll be taken to Android Settings to turn it on for Cadence." else "",
            )
        },
        confirmButton = { TextButton(onClick = onGrant) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
