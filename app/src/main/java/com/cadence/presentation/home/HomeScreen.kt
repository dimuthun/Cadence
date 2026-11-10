@file:OptIn(ExperimentalMaterial3Api::class)

package com.cadence.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.BuildConfig
import com.cadence.presentation.catalog.Catalog
import com.cadence.presentation.components.EmptyState
import com.cadence.presentation.components.WarningCard

@Composable
fun HomeScreen(
    onCreateMacro: () -> Unit,
    onEditMacro: (Long) -> Unit,
    onShowMacroLog: (Long, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenVariables: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.undoMacro != null) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && message.undoMacro != null) {
                viewModel.undoDelete(message.undoMacro)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cadence") },
                actions = {
                    IconButton(onClick = onOpenVariables) {
                        Icon(Icons.Filled.DataObject, contentDescription = "Variables")
                    }
                    if (BuildConfig.DEBUG) {
                        IconButton(onClick = viewModel::addSampleMacros) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Add sample macros")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateMacro,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New macro") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.totalMacros == 0 -> EmptyState(
                icon = Icons.Filled.Bolt,
                title = "No macros yet",
                message = "A macro does something for you when an event happens, like silencing the phone when you get home from work.",
                modifier = Modifier.padding(padding),
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onCreateMacro) { Text("Create your first macro") }
                        if (BuildConfig.DEBUG) {
                            FilledTonalButton(onClick = viewModel::addSampleMacros) { Text("Add sample macros") }
                        }
                    }
                },
            )
            else -> MacroList(
                state = state,
                contentPadding = padding,
                onQueryChange = viewModel::onQueryChange,
                onCategorySelected = viewModel::onCategorySelected,
                onToggle = viewModel::setEnabled,
                onEdit = onEditMacro,
                onRunNow = viewModel::runNow,
                onShowLog = onShowMacroLog,
                onDelete = viewModel::delete,
                onOpenSettings = onOpenSettings,
                onOpenPermissions = onOpenPermissions,
            )
        }
    }
}

@Composable
private fun MacroList(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onRunNow: (Long) -> Unit,
    onShowLog: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search macros") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
            )
        }
        if (state.categories.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedCategory == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text("All") },
                        )
                    }
                    items(state.categories) { category ->
                        FilterChip(
                            selected = state.selectedCategory == category,
                            onClick = { onCategorySelected(if (state.selectedCategory == category) null else category) },
                            label = { Text(category) },
                        )
                    }
                }
            }
        }
        if (state.showBatteryWarning) {
            item {
                WarningCard(
                    title = "Battery optimization may stop your macros",
                    message = "Allow Cadence to run unrestricted so triggers aren't delayed or missed.",
                    actionLabel = "Fix in Settings",
                    onAction = onOpenSettings,
                )
            }
        }
        if (state.items.isEmpty()) {
            item {
                Text(
                    "No macros match your search.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(state.items, key = { it.id }) { item ->
            MacroCard(
                item = item,
                onToggle = { onToggle(item.id, it) },
                onClick = { onEdit(item.id) },
                onRunNow = { onRunNow(item.id) },
                onShowLog = { onShowLog(item.id, item.name) },
                onDelete = { onDelete(item.id) },
                onFixPermissions = onOpenPermissions,
            )
        }
    }
}

@Composable
private fun MacroCard(
    item: MacroListItem,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRunNow: () -> Unit,
    onShowLog: () -> Unit,
    onDelete: () -> Unit,
    onFixPermissions: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = item.firstTrigger?.let { Catalog.entryFor(it)?.icon } ?: Icons.Filled.Bolt
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    item.hasUnsupportedItems -> Text(
                        "Contains items from a newer version. It won't run until edited.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    item.missingPermissions > 0 -> Text(
                        "Needs ${item.missingPermissions} permission${if (item.missingPermissions > 1) "s" else ""}. Tap to fix",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Switch(checked = item.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Run now") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        onClick = { menuOpen = false; onRunNow() },
                    )
                    DropdownMenuItem(
                        text = { Text("View log") },
                        leadingIcon = { Icon(Icons.Filled.History, null) },
                        onClick = { menuOpen = false; onShowLog() },
                    )
                    if (item.missingPermissions > 0) {
                        DropdownMenuItem(
                            text = { Text("Grant permissions") },
                            onClick = { menuOpen = false; onFixPermissions() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}
