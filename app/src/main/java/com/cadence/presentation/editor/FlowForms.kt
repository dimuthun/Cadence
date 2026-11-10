@file:OptIn(ExperimentalMaterial3Api::class)

package com.cadence.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ConditionMatch
import com.cadence.domain.action.ConditionalBlock
import com.cadence.domain.action.ToggleState
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.presentation.catalog.Catalog
import com.cadence.presentation.catalog.CatalogEntry
import com.cadence.presentation.catalog.ConfigRules
import com.cadence.presentation.catalog.ConfigText
import com.cadence.presentation.catalog.ConstraintCatalog
import com.cadence.presentation.catalog.TriggerCatalog
import com.cadence.presentation.components.FieldLabel
import com.cadence.presentation.components.InfoNote
import com.cadence.presentation.components.IntSlider
import com.cadence.presentation.components.PLACEHOLDER_HINT
import com.cadence.presentation.components.SegmentedChoice
import com.cadence.presentation.components.TemplateTextField

// Forms for flow and macro-management items. Conditions (for if branches, while loops and
// groups) are edited inline here; the actions inside blocks are edited in the action tree.

private fun FormData.text() = ConfigText(macroName = { id -> otherMacros.firstOrNull { it.id == id }?.name })

/** If / else-if branches with their conditions, and whether there's an else. */
@Composable
fun IfElseForm(config: ActionConfig.IfElse, onChange: (ActionConfig) -> Unit, data: FormData) {
    config.branches.forEachIndexed { index, branch ->
        OutlinedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (index == 0) "If" else "Else if",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (config.branches.size > 1) {
                        IconButton(onClick = {
                            onChange(config.copy(branches = config.branches.filterIndexed { i, _ -> i != index }))
                        }) { Icon(Icons.Filled.Close, contentDescription = "Remove branch") }
                    }
                }
                ConditionsEditor(
                    conditions = branch.conditions,
                    match = branch.match,
                    onChange = { conditions, match ->
                        onChange(config.copy(branches = config.branches.replacedAt(index, branch.copy(conditions = conditions, match = match))))
                    },
                    data = data,
                )
                if (branch.actions.isNotEmpty()) {
                    Text(
                        "${branch.actions.size} action(s) inside. Edit them in the action list.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    TextButton(onClick = { onChange(config.copy(branches = config.branches + ConditionalBlock())) }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("Add else if", modifier = Modifier.padding(start = 4.dp))
    }
    InfoNote(
        "The first branch whose conditions hold runs; otherwise the Else actions run. " +
            "Add actions to each branch with the \"Add action here\" buttons in the action list.",
    )
}

/** Count, while conditions, or until-exit. */
@Composable
fun RepeatForm(config: ActionConfig.Repeat, onChange: (ActionConfig) -> Unit, data: FormData) {
    FieldLabel("Repeat")
    SegmentedChoice(
        options = ActionConfig.Repeat.Mode.entries,
        selected = config.mode,
        label = {
            when (it) {
                ActionConfig.Repeat.Mode.COUNT -> "Times"
                ActionConfig.Repeat.Mode.WHILE -> "While"
                ActionConfig.Repeat.Mode.UNTIL_EXIT -> "Until exit"
            }
        },
        onSelect = { onChange(config.copy(mode = it)) },
    )
    when (config.mode) {
        ActionConfig.Repeat.Mode.COUNT -> {
            FieldLabel("Number of times")
            IntSlider(config.count, 1..100, { onChange(config.copy(count = it)) }, { "$it×" })
        }
        ActionConfig.Repeat.Mode.WHILE -> {
            FieldLabel("While these hold (checked before each round)")
            ConditionsEditor(
                conditions = config.conditions,
                match = config.match,
                onChange = { conditions, match -> onChange(config.copy(conditions = conditions, match = match)) },
                data = data,
            )
        }
        ActionConfig.Repeat.Mode.UNTIL_EXIT -> InfoNote("Add an \"Exit loop\" action inside, usually within an if.")
    }
    if (config.mode != ActionConfig.Repeat.Mode.COUNT) {
        FieldLabel("Safety limit")
        IntSlider(config.maxIterations, 10..10_000, { onChange(config.copy(maxIterations = it)) }, { "at most $it rounds" }, step = 10)
    }
    InfoNote("Inside the loop, {loop.index} is the round number, starting at 1.")
}

/** Trigger to wait for, plus its settings and a time limit. */
@Composable
fun WaitForTriggerForm(config: ActionConfig.WaitForTrigger, onChange: (ActionConfig) -> Unit, data: FormData) {
    var choosing by remember { mutableStateOf(false) }
    FieldLabel("Wait until")
    OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
        Text(Catalog.entryFor(config.trigger)?.title ?: "Choose a trigger")
    }
    TriggerForm(config.trigger, { onChange(config.copy(trigger = it)) }, data)
    FieldLabel("Give up after")
    IntSlider(
        value = (config.timeoutSeconds / 60).coerceAtLeast(1),
        range = 1..240,
        onChange = { onChange(config.copy(timeoutSeconds = it * 60)) },
        valueLabel = { if (it < 60) "$it min" else "${it / 60} h ${it % 60} min" },
    )
    InfoNote("Afterwards {wait.timed_out} is true or false, and the trigger's details are available as {wait.…}.")
    if (choosing) {
        EntryPickerDialog(
            title = "Wait for",
            entries = TriggerCatalog.filter { it.type != TriggerConfig.Manual::class },
            onDismiss = { choosing = false },
            onPick = { picked ->
                choosing = false
                onChange(config.copy(trigger = picked))
            },
        )
    }
}

@Composable
fun RunMacroForm(config: ActionConfig.RunMacro, onChange: (ActionConfig) -> Unit, data: FormData) {
    FieldLabel("Macro")
    MacroPickerField(config.macroId.takeIf { it > 0 }, data, allowThisMacro = false) { onChange(config.copy(macroId = it ?: 0)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("Only if its constraints hold", modifier = Modifier.weight(1f))
        Switch(checked = config.checkConstraints, onCheckedChange = { onChange(config.copy(checkConstraints = it)) })
    }
    InfoNote("Its actions run now, and this macro continues after they finish. Its triggers are ignored.")
}

@Composable
fun SetMacroEnabledForm(config: ActionConfig.SetMacroEnabled, onChange: (ActionConfig) -> Unit, data: FormData) {
    FieldLabel("Macro")
    MacroPickerField(config.macroId, data, allowThisMacro = true) { onChange(config.copy(macroId = it)) }
    FieldLabel("Set it to")
    SegmentedChoice(
        options = ToggleState.entries,
        selected = config.state,
        label = {
            when (it) {
                ToggleState.ON -> "Enabled"
                ToggleState.OFF -> "Disabled"
                ToggleState.TOGGLE -> "Toggle"
            }
        },
        onSelect = { onChange(config.copy(state = it)) },
    )
}

@Composable
fun MessageForm(text: String, onText: (String) -> Unit, label: String, data: FormData) {
    TemplateTextField(text, onText, label, data.globalVariables, singleLine = false, minLines = 2)
    InfoNote(PLACEHOLDER_HINT)
}

/** Forms for constraints that refer to macros or other constraints. */
@Composable
fun MacroConstraintForm(config: ConstraintConfig, onChange: (ConstraintConfig) -> Unit, data: FormData) {
    when (config) {
        is ConstraintConfig.Group -> {
            FieldLabel("Holds when")
            SegmentedChoice(
                options = ConstraintConfig.Group.Operator.entries,
                selected = config.operator,
                label = {
                    when (it) {
                        ConstraintConfig.Group.Operator.ALL -> "All hold"
                        ConstraintConfig.Group.Operator.ANY -> "Any holds"
                        ConstraintConfig.Group.Operator.NONE -> "None holds"
                    }
                },
                onSelect = { onChange(config.copy(operator = it)) },
            )
            ConditionsEditor(
                conditions = config.constraints,
                match = null,
                onChange = { conditions, _ -> onChange(config.copy(constraints = conditions)) },
                data = data,
            )
        }
        is ConstraintConfig.MacroRunning -> {
            FieldLabel("Macro")
            MacroPickerField(config.macroId.takeIf { it > 0 }, data, allowThisMacro = false) { onChange(config.copy(macroId = it ?: 0)) }
            SegmentedChoice(listOf(true, false), config.running, { if (it) "Is running" else "Isn't running" }, { onChange(config.copy(running = it)) })
        }
        is ConstraintConfig.MacroEnabled -> {
            FieldLabel("Macro")
            MacroPickerField(config.macroId.takeIf { it > 0 }, data, allowThisMacro = false) { onChange(config.copy(macroId = it ?: 0)) }
            SegmentedChoice(listOf(true, false), config.enabled, { if (it) "Is enabled" else "Is disabled" }, { onChange(config.copy(enabled = it)) })
        }
        is ConstraintConfig.TimeSinceLastRun -> {
            FieldLabel("Macro")
            MacroPickerField(config.macroId, data, allowThisMacro = true) { onChange(config.copy(macroId = it)) }
            FieldLabel("Hasn't run in the last")
            IntSlider(config.minutes, 1..1_440, { onChange(config.copy(minutes = it)) }, { ConfigText.interval(it) })
            InfoNote("Use this as a cooldown so a macro doesn't run again too soon. Skipped runs don't count.")
        }
        else -> Unit
    }
}

/**
 * Editable list of conditions. [match] = `null` hides the all/any choice (groups choose their own
 * operator).
 */
@Composable
fun ConditionsEditor(
    conditions: List<ConstraintConfig>,
    match: ConditionMatch?,
    onChange: (List<ConstraintConfig>, ConditionMatch) -> Unit,
    data: FormData,
) {
    val text = data.text()
    var editing by remember { mutableStateOf<Pair<Int?, ConstraintConfig>?>(null) }
    var choosing by remember { mutableStateOf(false) }
    val currentMatch = match ?: ConditionMatch.ALL

    if (match != null && conditions.size > 1) {
        SegmentedChoice(
            options = ConditionMatch.entries,
            selected = match,
            label = { if (it == ConditionMatch.ALL) "All must hold" else "Any may hold" },
            onSelect = { onChange(conditions, it) },
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
    if (conditions.isEmpty()) {
        Text(
            if (match != null) "No conditions: always runs." else "No conditions yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    conditions.forEachIndexed { index, condition ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.constraint(condition),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).clickable { editing = index to condition }.padding(vertical = 8.dp),
            )
            IconButton(onClick = { onChange(conditions.filterIndexed { i, _ -> i != index }, currentMatch) }) {
                Icon(Icons.Filled.Close, contentDescription = "Remove condition")
            }
        }
    }
    TextButton(onClick = { choosing = true }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("Add condition", modifier = Modifier.padding(start = 4.dp))
    }

    if (choosing) {
        EntryPickerDialog(
            title = "Add condition",
            entries = ConstraintCatalog,
            onDismiss = { choosing = false },
            onPick = { picked ->
                choosing = false
                if (ConfigRules.hasSettings(picked)) editing = null to picked else onChange(conditions + picked, currentMatch)
            },
        )
    }
    editing?.let { (index, condition) ->
        var draft by remember(index, condition) { mutableStateOf(condition) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(Catalog.entryFor(draft)?.title ?: "Condition") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { ConstraintForm(draft, { draft = it }, data) } },
            confirmButton = {
                TextButton(enabled = ConfigRules.isComplete(draft), onClick = {
                    onChange(if (index == null) conditions + draft else conditions.replacedAt(index, draft), currentMatch)
                    editing = null
                }) { Text(if (index == null) "Add" else "Done") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

/** Simple list of catalog entries, for pickers nested inside a config dialog. */
@Composable
private fun <C : Any> EntryPickerDialog(
    title: String,
    entries: List<CatalogEntry<C>>,
    onDismiss: () -> Unit,
    onPick: (C) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(entries, key = { it.type.qualifiedName ?: it.title }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.description, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(entry.icon, contentDescription = null) },
                        modifier = Modifier.clickable { onPick(entry.create()) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks one of the user's other macros, or (optionally) "this macro" (`null`). */
@Composable
private fun MacroPickerField(selected: Long?, data: FormData, allowThisMacro: Boolean, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when {
        selected == null && allowThisMacro -> "This macro"
        selected == null -> "Choose a macro"
        else -> data.otherMacros.firstOrNull { it.id == selected }?.name ?: "Deleted macro"
    }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Choose a macro") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (allowThisMacro) {
                        item {
                            ListItem(headlineContent = { Text("This macro") }, modifier = Modifier.clickable { onSelect(null); open = false })
                        }
                    }
                    items(data.otherMacros, key = { it.id }) { macro ->
                        ListItem(headlineContent = { Text(macro.name) }, modifier = Modifier.clickable { onSelect(macro.id); open = false })
                    }
                    if (data.otherMacros.isEmpty() && !allowThisMacro) {
                        item { Text("Create another macro first.", modifier = Modifier.padding(8.dp)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

private fun <T> List<T>.replacedAt(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
