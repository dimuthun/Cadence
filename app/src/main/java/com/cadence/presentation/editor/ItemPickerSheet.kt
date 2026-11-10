@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.cadence.presentation.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.presentation.catalog.CatalogEntry

/** Bottom sheet listing catalog entries grouped by category, with search. */
@Composable
fun <C : Any> ItemPickerSheet(
    title: String,
    entries: List<CatalogEntry<C>>,
    onPick: (C) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val grouped = remember(entries, query) {
        entries
            .filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            LazyColumn {
                grouped.forEach { (category, items) ->
                    stickyHeader(key = category.name) {
                        Text(
                            category.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    items(items, key = { it.type.qualifiedName ?: it.title }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.title) },
                            supportingContent = { Text(entry.description) },
                            leadingContent = { Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { onPick(entry.create()) },
                        )
                    }
                }
                if (grouped.isEmpty()) {
                    item {
                        Text("Nothing matches \"$query\"", modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }
}
