@file:OptIn(ExperimentalMaterial3Api::class)

package com.cadence.presentation.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.platform.permission.SpecialAccess
import com.cadence.presentation.components.SectionHeader

/**
 * Every permission and special access Cadence uses, its status, and a shortcut to grant it.
 * Items your macros need are listed first.
 */
@Composable
fun PermissionsScreen(viewModel: PermissionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestAccess = rememberAccessRequester(onReturn = viewModel::refresh)

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Permissions") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "Cadence only asks for what your macros use. Nothing here is required until a macro needs it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.needed.isNotEmpty()) {
                item { SectionHeader("Needed by your macros") }
                items(state.needed, key = { it.access.name }) { row -> AccessCard(row) { requestAccess(row.access) } }
            }
            if (state.other.isNotEmpty()) {
                item { SectionHeader("Other") }
                items(state.other, key = { it.access.name }) { row -> AccessCard(row) { requestAccess(row.access) } }
            }
        }
    }
}

@Composable
private fun AccessCard(row: AccessRow, onGrant: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (row.granted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (row.granted) "Granted" else "Not granted",
                    tint = if (row.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Text(row.access.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
            Text(row.access.rationale, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            if (row.neededBy.isNotEmpty()) {
                Text(
                    "Used by: " + row.neededBy.joinToString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!row.granted) {
                FilledTonalButton(onClick = onGrant, modifier = Modifier.padding(top = 12.dp).align(Alignment.End)) {
                    Text(if (row.access.runtimePermission != null) "Allow" else "Open settings")
                }
            } else if (row.access.runtimePermission == null && row.access != SpecialAccess.NOTIFICATIONS) {
                Text(
                    "Granted. You can turn it off in Android Settings at any time.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
