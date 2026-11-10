@file:OptIn(ExperimentalMaterial3Api::class)

package com.cadence.presentation.log

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.presentation.components.EmptyState
import com.cadence.presentation.components.style
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Execution log. [onBack] is `null` when shown as a tab.
 */
@Composable
fun LogScreen(viewModel: LogViewModel, title: String, onBack: (() -> Unit)?) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = {
                    if (onBack == null && state.entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear log") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.problemsOnly, onClick = { viewModel.setProblemsOnly(false) }, label = { Text("All") })
                FilterChip(selected = state.problemsOnly, onClick = { viewModel.setProblemsOnly(true) }, label = { Text("Problems & skips") })
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.entries.isEmpty() -> EmptyState(
                    icon = Icons.Filled.History,
                    title = if (state.problemsOnly) "No problems" else "Nothing here yet",
                    message = "Each time a macro is triggered, what happened appears here: which trigger fired and how every action went.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry -> LogEntryCard(entry, showMacroName = onBack == null) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the log?") },
            text = { Text("This deletes the history of all macros. Your macros aren't affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clear(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM)

@Composable
private fun LogEntryCard(entry: LogEntry, showMacroName: Boolean) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val style = entry.status.style()
    ElevatedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(style.icon, contentDescription = style.label, tint = style.color, modifier = Modifier.size(20.dp))
                Text(
                    if (showMacroName) entry.macroName else style.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Text(
                    timeFormatter.format(entry.startedAt.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Trigger: ${entry.triggerSummary}" + if (showMacroName) " · ${style.label}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            entry.skippedBecause?.let {
                Text("Skipped: $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            if (expanded && entry.outcomes.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                entry.outcomes.forEach { outcome -> OutcomeLine(outcome) }
            } else if (entry.outcomes.isNotEmpty()) {
                Text(
                    "${entry.outcomes.size} action(s). Tap for details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun OutcomeLine(outcome: OutcomeRow) {
    val style = outcome.status.style()
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(style.icon, contentDescription = style.label, tint = style.color, modifier = Modifier.size(18.dp))
        Column(Modifier.padding(start = 8.dp)) {
            Text("${outcome.position + 1}. ${outcome.summary}", style = MaterialTheme.typography.bodyMedium)
            outcome.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${style.label} · ${outcome.durationMillis} ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
