@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cadence.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.domain.model.Weekday
import com.cadence.presentation.catalog.ConfigText
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop

/** Small label above a form control. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

/** Single choice among 2–4 short options. */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

/** Single choice among many options, as wrapping chips. */
@Composable
fun <T> ChipChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

/** Multi-select weekday chips. */
@Composable
fun WeekdayChips(selected: Set<Weekday>, onChange: (Set<Weekday>) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Weekday.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onChange(if (day in selected) selected - day else selected + day) },
                label = { Text(ConfigText.shortDay(day)) },
            )
        }
    }
}

/** Integer slider with its current value shown. */
@Composable
fun IntSlider(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    valueLabel: (Int) -> String,
    modifier: Modifier = Modifier,
    step: Int = 1,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(valueLabel(value), style = MaterialTheme.typography.titleMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { raw -> onChange(((raw / step).roundToInt() * step).coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
        )
    }
}

/** 24-hour time entry. Reports every change. */
@Composable
fun TimeField(hour: Int, minute: Int, onChange: (hour: Int, minute: Int) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    val latestOnChange by rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        snapshotFlow { state.hour to state.minute }
            .drop(1)
            .collect { (h, m) -> latestOnChange(h, m) }
    }
    TimeInput(state = state, modifier = modifier)
}

/** Informational note shown inside forms, e.g. about Android restrictions. */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
