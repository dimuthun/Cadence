package com.cadence.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.cadence.domain.variable.SystemVariables

/**
 * Text field whose value may contain `{variable}` placeholders. The trailing button inserts one
 * at the cursor, chosen from built-in variables and the user's global variables.
 */
@Composable
fun TemplateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    globalVariables: List<String>,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    // Keep the cursor position so inserted placeholders land where the user is typing.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // If the value changed from outside, show it with the cursor at the end.
    val current = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    var picking by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = current,
        onValueChange = {
            field = it
            if (it.text != value) onValueChange(it.text)
        },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        trailingIcon = {
            IconButton(onClick = { picking = true }) { Icon(Icons.Filled.DataObject, contentDescription = "Insert variable") }
        },
        modifier = modifier.fillMaxWidth(),
    )

    if (picking) {
        VariablePickerDialog(
            globalVariables = globalVariables,
            onDismiss = { picking = false },
            onPick = { name ->
                picking = false
                val token = "{$name}"
                val text = current.text.replaceRange(current.selection.min, current.selection.max, token)
                field = TextFieldValue(text, TextRange(current.selection.min + token.length))
                onValueChange(text)
            },
        )
    }
}

/**
 * Searchable list of variables. [allowBuiltIns] = `false` lists only user variables, for fields
 * that write a variable.
 */
@Composable
fun VariablePickerDialog(
    globalVariables: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    allowBuiltIns: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    val entries = remember(globalVariables, query, allowBuiltIns) {
        val user = globalVariables.map { Triple(it, "Your variable", "Your variables") }
        val builtIn = if (allowBuiltIns) SystemVariables.all.map { Triple(it.key, it.description, it.group) } else emptyList()
        (user + builtIn).filter {
            query.isBlank() || it.first.contains(query, ignoreCase = true) || it.second.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert variable") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 400.dp).padding(top = 8.dp)) {
                    var lastGroup: String? = null
                    entries.forEach { (name, description, group) ->
                        if (group != lastGroup) {
                            lastGroup = group
                            item(key = "header:$group") {
                                Text(
                                    group,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                        }
                        item(key = "var:$name") {
                            ListItem(
                                headlineContent = { Text("{$name}") },
                                supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.clickable { onPick(name) },
                            )
                        }
                    }
                    if (entries.isEmpty()) {
                        item { Text("No matching variables", modifier = Modifier.padding(top = 12.dp)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Hint shown under forms that accept placeholders. */
const val PLACEHOLDER_HINT = "Tap { } to insert a variable such as {battery.level} or {location.latitude}."
