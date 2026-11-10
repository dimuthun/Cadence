@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cadence.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.presentation.catalog.Catalog
import com.cadence.presentation.catalog.ConfigRules
import com.cadence.presentation.catalog.ConfigText
import com.cadence.presentation.components.WarningCard
import com.cadence.presentation.permissions.AccessRationaleDialog
import com.cadence.presentation.permissions.rememberAccessRequester

@Composable
fun MacroEditorScreen(viewModel: MacroEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val requestAccess = rememberAccessRequester(onReturn = viewModel::refreshPermissions)
    val labels = remember(state.apps) { state.apps.associate { it.packageName to it.label } }
    val macroNames = remember(state.otherMacros) { state.otherMacros.associate { it.id to it.name } }
    val text = remember(labels, macroNames) { ConfigText(macroName = { macroNames[it] }, appLabel = { labels[it] ?: it }) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                EditorEvent.Saved -> onClose()
                is EditorEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is EditorEvent.RequestAccess -> requestAccess(event.access)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New macro" else "Edit macro") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close") }
                },
                actions = {
                    TextButton(onClick = viewModel::testRun, enabled = !state.isTestRunning && state.actions.isNotEmpty()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Test")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = viewModel::previousStep, enabled = state.step != EditorStep.TRIGGERS) { Text("Back") }
                    if (state.step == EditorStep.DETAILS) {
                        Button(onClick = viewModel::save, enabled = !state.isSaving) { Text("Save macro") }
                    } else {
                        Button(onClick = viewModel::nextStep) { Text("Next") }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.step.ordinal) {
                EditorStep.entries.forEach { step ->
                    Tab(
                        selected = state.step == step,
                        onClick = { viewModel.goToStep(step) },
                        text = { Text("${step.ordinal + 1}. ${step.title}", maxLines = 1) },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.unsupportedItemCount > 0) {
                    item {
                        WarningCard(
                            title = "Some items can't be shown",
                            message = "${state.unsupportedItemCount} item(s) came from a newer version of Cadence. Saving will remove them.",
                        )
                    }
                }
                if (state.missingAccess.isNotEmpty()) {
                    item {
                        WarningCard(
                            title = "Permissions needed",
                            message = "This macro won't fully work until you grant: " +
                                state.missingAccess.joinToString { it.title.substringBefore(" (") } + ".",
                        )
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.missingAccess.forEach { access ->
                                AssistChip(onClick = { requestAccess(access) }, label = { Text("Grant ${access.title.substringBefore(" (")}") })
                            }
                        }
                    }
                }
                when (state.step) {
                    EditorStep.TRIGGERS -> {
                        item { StepIntro("The macro runs when any of these happens.") }
                        itemsIndexed(state.triggers) { index, trigger ->
                            ItemCard(
                                icon = Catalog.entryFor(trigger)?.icon,
                                title = Catalog.entryFor(trigger)?.title ?: "Trigger",
                                summary = text.trigger(trigger),
                                onEdit = { viewModel.editItem(ItemKind.TRIGGER, index) },
                                onDelete = { viewModel.removeItem(ItemKind.TRIGGER, index) },
                            )
                        }
                        item { AddButton("Add trigger") { viewModel.openPicker(ItemKind.TRIGGER) } }
                    }
                    EditorStep.ACTIONS -> {
                        item { StepIntro("These run in order, top to bottom. Actions inside an if or repeat are indented.") }
                        val rows = state.actions.toRows(describeConditions = { action, slot ->
                            when (action) {
                                is ActionConfig.IfElse -> action.branches.getOrNull(slot)
                                    ?.let { text.conditions(it.conditions, it.match) }.orEmpty()
                                is ActionConfig.Repeat -> text.repeatDescription(action)
                                else -> ""
                            }
                        })
                        items(rows.size) { position ->
                            val indent = Modifier.padding(start = (rows[position].depth * 20).dp)
                            when (val row = rows[position]) {
                                is ActionRow.Item -> ItemCard(
                                    icon = Catalog.entryFor(row.action)?.icon,
                                    title = Catalog.entryFor(row.action)?.title ?: "Action",
                                    summary = text.action(row.action),
                                    onEdit = { viewModel.editItem(ItemKind.ACTION, row.index, row.path) },
                                    onDelete = { viewModel.removeItem(ItemKind.ACTION, row.index, row.path) },
                                    onMoveUp = if (!row.isFirst) ({ viewModel.moveAction(row.index, -1, row.path) }) else null,
                                    onMoveDown = if (!row.isLast) ({ viewModel.moveAction(row.index, 1, row.path) }) else null,
                                    modifier = indent,
                                )
                                is ActionRow.Header -> Text(
                                    row.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = indent,
                                )
                                is ActionRow.Add -> AddButton(
                                    if (row.path.isEmpty()) "Add action" else "Add action here",
                                    modifier = indent,
                                ) { viewModel.openPicker(ItemKind.ACTION, row.path) }
                            }
                        }
                    }
                    EditorStep.CONSTRAINTS -> {
                        item { StepIntro("Optional. The actions only run if all of these are true when a trigger fires.") }
                        itemsIndexed(state.constraints) { index, constraint ->
                            ItemCard(
                                icon = Catalog.entryFor(constraint)?.icon,
                                title = Catalog.entryFor(constraint)?.title ?: "Constraint",
                                summary = text.constraint(constraint),
                                onEdit = { viewModel.editItem(ItemKind.CONSTRAINT, index) },
                                onDelete = { viewModel.removeItem(ItemKind.CONSTRAINT, index) },
                            )
                        }
                        item { AddButton("Add constraint") { viewModel.openPicker(ItemKind.CONSTRAINT) } }
                    }
                    EditorStep.DETAILS -> item { DetailsStep(state, viewModel, text) }
                }
            }
        }
    }

    when (state.picker) {
        ItemKind.TRIGGER -> ItemPickerSheet("Add a trigger", viewModel.triggerEntries, { viewModel.onItemPicked(ItemKind.TRIGGER, it) }, viewModel::closePicker)
        ItemKind.ACTION -> ItemPickerSheet("Add an action", viewModel.actionEntries, { viewModel.onItemPicked(ItemKind.ACTION, it) }, viewModel::closePicker)
        ItemKind.CONSTRAINT -> ItemPickerSheet("Add a constraint", viewModel.constraintEntries, { viewModel.onItemPicked(ItemKind.CONSTRAINT, it) }, viewModel::closePicker)
        null -> Unit
    }

    state.editing?.let { editing ->
        ConfigDialog(
            editing = editing,
            data = FormData(
                apps = state.apps,
                pairedDevices = state.pairedDevices,
                playServicesAvailable = state.playServicesAvailable,
                isLocating = state.isLocating,
                locationVersion = state.locationVersion,
                onUseCurrentLocation = viewModel::useCurrentLocation,
                globalVariables = state.globalVariableNames,
                otherMacros = state.otherMacros,
            ),
            onChange = viewModel::updateEditing,
            onConfirm = viewModel::confirmEditing,
            onDismiss = viewModel::cancelEditing,
        )
    }

    state.rationale?.let { access ->
        if (access.requiresProminentDisclosure) {
            // The requester shows its own disclosure before every request for this access.
            LaunchedEffect(access) {
                viewModel.onRationaleHandled()
                requestAccess(access)
            }
        } else {
            AccessRationaleDialog(
                access = access,
                onGrant = {
                    viewModel.onRationaleHandled()
                    requestAccess(access)
                },
                onDismiss = viewModel::onRationaleHandled,
            )
        }
    }
}

@Composable
private fun StepIntro(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AddButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ItemCard(
    icon: ImageVector?,
    title: String,
    summary: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onEdit, modifier = modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { Icon(icon ?: Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            trailingContent = {
                Row {
                    if (onMoveUp != null) IconButton(onClick = onMoveUp) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                    if (onMoveDown != null) IconButton(onClick = onMoveDown) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove") }
                }
            },
        )
    }
}

@Composable
private fun DetailsStep(state: EditorUiState, viewModel: MacroEditorViewModel, text: ConfigText) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            label = { Text("Name") },
            placeholder = { Text("Leave empty to name it automatically") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.category,
            onValueChange = viewModel::setCategory,
            label = { Text("Category") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.existingCategories.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.existingCategories.forEach { category ->
                    AssistChip(onClick = { viewModel.setCategory(category) }, label = { Text(category) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enabled", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
        }
        Text("Summary", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            buildString {
                appendLine("When: " + state.triggers.joinToString(" or ") { text.trigger(it) }.ifEmpty { "(no trigger)" })
                appendLine("Do: " + state.actions.joinToString(", then ") { text.action(it) }.ifEmpty { "(no action)" })
                if (state.constraints.isNotEmpty()) {
                    append("Only if: " + state.constraints.joinToString(" and ") { text.constraint(it) })
                }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ConfigDialog(
    editing: EditingItem,
    data: FormData,
    onChange: (Any) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (val config = editing.config) {
        is TriggerConfig -> Catalog.entryFor(config)?.title
        is ActionConfig -> Catalog.entryFor(config)?.title
        is ConstraintConfig -> Catalog.entryFor(config)?.title
        else -> null
    } ?: "Settings"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (val config = editing.config) {
                    is TriggerConfig -> TriggerForm(config, onChange, data)
                    is ActionConfig -> ActionForm(config, onChange, data)
                    is ConstraintConfig -> ConstraintForm(config, onChange, data)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = ConfigRules.isComplete(editing.config)) {
                Text(if (editing.index == null) "Add" else "Done")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
