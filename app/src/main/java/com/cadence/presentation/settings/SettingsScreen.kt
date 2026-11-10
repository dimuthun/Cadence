@file:OptIn(ExperimentalMaterial3Api::class)

package com.cadence.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.BuildConfig
import com.cadence.domain.model.AppSettings
import com.cadence.domain.model.ThemeMode
import com.cadence.platform.permission.SpecialAccess
import com.cadence.presentation.components.ChipChoice
import com.cadence.presentation.components.SectionHeader
import com.cadence.presentation.components.SegmentedChoice
import com.cadence.presentation.permissions.rememberAccessRequester

@Composable
fun SettingsScreen(onOpenVariables: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val requestAccess = rememberAccessRequester(onReturn = viewModel::refresh)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (state.isBusy) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            item { SectionHeader("Appearance") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SegmentedChoice(
                        options = ThemeMode.entries,
                        selected = state.settings.themeMode,
                        label = {
                            when (it) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }
                        },
                        onSelect = viewModel::setThemeMode,
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        headlineContent = { Text("Use wallpaper colors") },
                        supportingContent = { Text("Replace Cadence's teal theme with your wallpaper's colors") },
                        trailingContent = {
                            Switch(checked = state.settings.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                        },
                    )
                }
            }

            item { SectionHeader("Background reliability") }
            item {
                BatteryCard(
                    unrestricted = state.batteryUnrestricted,
                    onOpenBatterySettings = { requestAccess(SpecialAccess.UNRESTRICTED_BATTERY) },
                    onOpenGuideSite = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OemBatteryGuide.MORE_INFO_URL)))
                    },
                )
            }

            item { SectionHeader("Data") }
            item {
                ListItem(
                    headlineContent = { Text("Variables") },
                    supportingContent = { Text("View and edit saved variables, and see the built-in ones") },
                    leadingContent = { Icon(Icons.Filled.DataObject, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenVariables),
                )
            }

            item { SectionHeader("Backup") }
            item {
                ListItem(
                    headlineContent = { Text("Export macros") },
                    supportingContent = { Text("Save all macros to a JSON file") },
                    leadingContent = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                    trailingContent = {
                        TextButton(onClick = { exportLauncher.launch(viewModel.suggestedExportName()) }, enabled = !state.isBusy) {
                            Text("Export")
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Import macros") },
                    supportingContent = { Text("Imported macros start disabled, so you can check permissions first") },
                    leadingContent = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                    trailingContent = {
                        TextButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            enabled = !state.isBusy,
                        ) { Text("Import") }
                    },
                )
            }

            item { SectionHeader("Log") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Keep log entries for", style = MaterialTheme.typography.bodyLarge)
                    ChipChoice(
                        options = AppSettings.LOG_RETENTION_OPTIONS,
                        selected = state.settings.logRetentionDays,
                        label = { if (it == 1) "1 day" else "$it days" },
                        onSelect = viewModel::setLogRetentionDays,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item { SectionHeader("About") }
            item {
                ListItem(
                    headlineContent = { Text("Cadence ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("Automations run on your device. Nothing is sent anywhere.") },
                )
            }
        }
    }
}

@Composable
private fun BatteryCard(unrestricted: Boolean, onOpenBatterySettings: () -> Unit, onOpenGuideSite: () -> Unit) {
    val thisDevice = remember { OemBatteryGuide.forThisDevice() }
    var showAll by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (unrestricted) Icons.Filled.BatteryFull else Icons.Filled.BatteryAlert,
                    contentDescription = null,
                    tint = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(
                    if (unrestricted) "Battery optimization is off for Cadence" else "Battery optimization is on",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                if (unrestricted) {
                    "Android won't pause Cadence. Some phones have extra restrictions; check the steps below if triggers still stop."
                } else {
                    "Android may pause Cadence in the background, delaying or skipping triggers. Find Cadence in the list and choose \"Don't optimize\" / \"Unrestricted\"."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!unrestricted) {
                FilledTonalButton(onClick = onOpenBatterySettings, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Open battery settings")
                }
            }

            Text(
                "On your phone (${thisDevice.brand})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp),
            )
            GuideSteps(thisDevice)

            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(if (showAll) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                Text(if (showAll) "Hide other brands" else "Show other brands")
            }
            if (showAll) {
                OemBatteryGuide.all.filter { it != thisDevice }.forEach { guide ->
                    Text(guide.brand, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    GuideSteps(guide)
                }
            }
            TextButton(onClick = onOpenGuideSite) { Text("More details at dontkillmyapp.com") }
        }
    }
}

@Composable
private fun GuideSteps(guide: OemGuide) {
    guide.steps.forEachIndexed { index, step ->
        Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
