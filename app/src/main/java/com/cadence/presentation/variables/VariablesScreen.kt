@file:OptIn(ExperimentalMaterial3Api::class)

package com.cadence.presentation.variables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.domain.variable.SystemVariables
import com.cadence.presentation.components.SectionHeader
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Global variables plus a reference of built-in ones. */
@Composable
fun VariablesScreen(onBack: () -> Unit, viewModel: VariablesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var showBuiltIns by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Variables") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isNew = true; editing = "" to "" },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New variable") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "Saved variables keep their value between runs and are shared by all macros. " +
                        "Use them in any text field as {name}, change them with the \"Set variable\" action, " +
                        "and check them with the \"Variable value\" constraint.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { SectionHeader("Your variables") }
            if (!state.isLoading && state.variables.isEmpty()) {
                item {
                    Text("None yet.", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(state.variables, key = { it.name }) { variable ->
                ListItem(
                    headlineContent = { Text("{${variable.name}}") },
                    supportingContent = {
                        Column {
                            Text(variable.value.ifEmpty { "(empty)" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Changed " + updatedFormatter.format(variable.updatedAt.atZone(ZoneId.systemDefault())),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(variable.name) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${variable.name}")
                        }
                    },
                    modifier = Modifier.clickable { isNew = false; editing = variable.name to variable.value },
                )
            }
            item {
                TextButton(onClick = { showBuiltIns = !showBuiltIns }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(if (showBuiltIns) "Hide built-in variables" else "Show built-in variables")
                }
            }
            if (showBuiltIns) {
                SystemVariables.all.groupBy { it.group }.forEach { (group, definitions) ->
                    item(key = "group:$group") { SectionHeader(group) }
                    items(definitions, key = { it.key }) { definition ->
                        ListItem(
                            headlineContent = { Text("{${definition.key}}") },
                            supportingContent = { Text(definition.description) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { (name, value) ->
        VariableDialog(
            initialName = name,
            initialValue = value,
            isNew = isNew,
            onSave = { newName, newValue -> viewModel.save(newName, newValue, isNew) },
            onDismiss = { editing = null },
        )
    }
}

private val updatedFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

@Composable
private fun VariableDialog(
    initialName: String,
    initialValue: String,
    isNew: Boolean,
    onSave: (String, String) -> String?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New variable" else "Edit {$initialName}") },
        text = {
            Column {
                if (isNew) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = onSave(name, value)
                if (problem == null) onDismiss() else error = problem
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
