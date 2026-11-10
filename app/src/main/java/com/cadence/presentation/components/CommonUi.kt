package com.cadence.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.OutcomeStatus

/** Centered illustration-free empty state. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Tinted card for warnings with an optional action button. */
@Composable
fun WarningCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            if (actionLabel != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(actionLabel) }
            }
        }
    }
}

/** Section heading inside scrolling screens. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Icon and color for an execution status. */
data class StatusStyle(val icon: ImageVector, val color: Color, val label: String)

@Composable
fun ExecutionStatus.style(): StatusStyle = when (this) {
    ExecutionStatus.SUCCESS -> StatusStyle(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, "Succeeded")
    ExecutionStatus.PARTIAL_FAILURE -> StatusStyle(Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary, "Partly failed")
    ExecutionStatus.FAILURE -> StatusStyle(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Failed")
    ExecutionStatus.CONSTRAINTS_NOT_MET -> StatusStyle(Icons.Filled.RemoveCircle, MaterialTheme.colorScheme.outline, "Skipped")
    ExecutionStatus.CANCELLED -> StatusStyle(Icons.Filled.RemoveCircle, MaterialTheme.colorScheme.outline, "Cancelled")
}

@Composable
fun OutcomeStatus.style(): StatusStyle = when (this) {
    OutcomeStatus.SUCCESS -> StatusStyle(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, "Done")
    OutcomeStatus.FALLBACK -> StatusStyle(Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary, "Needs you")
    OutcomeStatus.FAILURE -> StatusStyle(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Failed")
}
