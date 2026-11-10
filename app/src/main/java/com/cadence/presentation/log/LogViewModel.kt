package com.cadence.presentation.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.MacroExecution
import com.cadence.domain.model.OutcomeStatus
import com.cadence.domain.repository.ExecutionLogRepository
import com.cadence.platform.device.InstalledAppsProvider
import com.cadence.presentation.catalog.ConfigText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One action result, ready to display. */
data class OutcomeRow(val position: Int, val summary: String, val status: OutcomeStatus, val message: String?, val durationMillis: Long)

/** One log entry, ready to display. */
data class LogEntry(
    val id: Long,
    val macroName: String,
    val startedAt: Instant,
    val status: ExecutionStatus,
    val triggerSummary: String,
    val skippedBecause: String?,
    val outcomes: List<OutcomeRow>,
)

data class LogUiState(
    val isLoading: Boolean = true,
    val entries: List<LogEntry> = emptyList(),
    val problemsOnly: Boolean = false,
)

/** Execution history, for all macros or for one ([macroId]). */
@HiltViewModel(assistedFactory = LogViewModel.Factory::class)
class LogViewModel @AssistedInject constructor(
    @Assisted private val macroId: Long?,
    private val logRepository: ExecutionLogRepository,
    installedApps: InstalledAppsProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(macroId: Long?): LogViewModel
    }

    private val problemsOnly = MutableStateFlow(false)
    private val appLabels = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState: StateFlow<LogUiState> = combine(
        if (macroId == null) logRepository.observeExecutions() else logRepository.observeExecutionsForMacro(macroId),
        problemsOnly,
        appLabels,
    ) { executions, onlyProblems, labels ->
        val text = ConfigText { labels[it] ?: it }
        LogUiState(
            isLoading = false,
            entries = executions
                .filter { !onlyProblems || it.status != ExecutionStatus.SUCCESS }
                .map { it.toEntry(text) },
            problemsOnly = onlyProblems,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    init {
        viewModelScope.launch {
            appLabels.value = installedApps.launchableApps().associate { it.packageName to it.label }
        }
    }

    fun setProblemsOnly(value: Boolean) {
        problemsOnly.value = value
    }

    /** Clears the whole log (all macros). */
    fun clear() {
        viewModelScope.launch { logRepository.clear() }
    }

    private fun MacroExecution.toEntry(text: ConfigText) = LogEntry(
        id = id,
        macroName = macroName,
        startedAt = startedAt,
        status = status,
        triggerSummary = text.trigger(trigger),
        skippedBecause = failedConstraint?.let { "Not ${text.constraint(it).replaceFirstChar(Char::lowercase)}" },
        outcomes = actionOutcomes.map { outcome ->
            OutcomeRow(outcome.index, text.action(outcome.action), outcome.status, outcome.message, outcome.durationMillis)
        },
    )
}
