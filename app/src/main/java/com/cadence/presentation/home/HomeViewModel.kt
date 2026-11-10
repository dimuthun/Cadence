package com.cadence.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.domain.engine.EngineHost
import com.cadence.domain.engine.MacroEngine
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.Macro
import com.cadence.domain.permission.PermissionStatusProvider
import com.cadence.domain.plugin.RequirementResolver
import com.cadence.domain.repository.MacroRepository
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.platform.device.InstalledAppsProvider
import com.cadence.platform.permission.SpecialAccess
import com.cadence.platform.permission.SpecialAccessChecker
import com.cadence.presentation.catalog.ConfigText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row on the Home screen. */
data class MacroListItem(
    val id: Long,
    val name: String,
    val category: String,
    val enabled: Boolean,
    val summary: String,
    val firstTrigger: TriggerConfig?,
    val missingPermissions: Int,
    val hasUnsupportedItems: Boolean,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val items: List<MacroListItem> = emptyList(),
    val totalMacros: Int = 0,
    val categories: List<String> = emptyList(),
    val query: String = "",
    val selectedCategory: String? = null,
    val showBatteryWarning: Boolean = false,
)

/** One-off messages for the snackbar. [undoMacro] offers to restore a deleted macro. */
data class HomeMessage(val text: String, val undoMacro: Macro? = null)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val macroRepository: MacroRepository,
    private val engine: MacroEngine,
    private val engineHost: EngineHost,
    private val requirements: RequirementResolver,
    private val permissionStatus: PermissionStatusProvider,
    private val specialAccessChecker: SpecialAccessChecker,
    private val installedApps: InstalledAppsProvider,
    private val clock: Clock,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val refreshTick = MutableStateFlow(0)
    private val appLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val messages = Channel<HomeMessage>(Channel.BUFFERED)

    val events: Flow<HomeMessage> = messages.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        macroRepository.observeMacros(),
        macroRepository.observeCategories(),
        combine(query, selectedCategory, ::Pair),
        appLabels,
        refreshTick,
    ) { macros, categories, (query, category), labels, _ ->
        val text = ConfigText { labels[it] ?: it }
        val filtered = macros
            .filter { category == null || it.category == category }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        HomeUiState(
            isLoading = false,
            items = filtered.map { it.toListItem(text) },
            totalMacros = macros.size,
            categories = categories,
            query = query,
            selectedCategory = category?.takeIf { it in categories },
            showBatteryWarning = macros.any { it.isRunnable } &&
                !specialAccessChecker.isGranted(SpecialAccess.UNRESTRICTED_BATTERY),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            appLabels.value = installedApps.launchableApps().associate { it.packageName to it.label }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategorySelected(category: String?) {
        selectedCategory.value = category
    }

    /** Re-checks permissions and battery status, e.g. after returning from Settings. */
    fun refresh() {
        refreshTick.update { it + 1 }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            macroRepository.setEnabled(id, enabled)
            if (enabled) engineHost.startIfNeeded()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val macro = macroRepository.getMacro(id) ?: return@launch
            macroRepository.deleteMacro(id)
            messages.send(HomeMessage("Deleted \"${macro.name}\"", undoMacro = macro))
        }
    }

    fun undoDelete(macro: Macro) {
        viewModelScope.launch {
            macroRepository.saveMacro(macro)
            if (macro.enabled) engineHost.startIfNeeded()
        }
    }

    /**
     * Runs a macro now, checking its constraints. Runs in the application scope so a macro with
     * waits finishes even if the user leaves the screen.
     */
    fun runNow(id: Long) {
        applicationScope.launch {
            val macro = macroRepository.getMacro(id) ?: return@launch
            val result = engine.execute(macro, TriggerEvent(TriggerConfig.Manual, clock.instant()))
            val outcome = when (result.status) {
                ExecutionStatus.SUCCESS -> "finished"
                ExecutionStatus.PARTIAL_FAILURE -> "finished with some failures"
                ExecutionStatus.FAILURE -> "failed"
                ExecutionStatus.CONSTRAINTS_NOT_MET -> "skipped: a constraint wasn't met"
                ExecutionStatus.CANCELLED -> "was cancelled"
            }
            messages.send(HomeMessage("\"${macro.name}\" $outcome. See the log for details."))
        }
    }

    /** Debug builds only: adds the [SampleMacros]. */
    fun addSampleMacros() {
        viewModelScope.launch {
            SampleMacros.create(clock.instant(), LocalTime.now(clock)).forEach { macroRepository.saveMacro(it) }
            engineHost.startIfNeeded()
            messages.send(HomeMessage("Added sample macros"))
        }
    }

    private fun Macro.toListItem(text: ConfigText): MacroListItem {
        val first = triggers.firstOrNull()?.let(text::trigger) ?: "No trigger"
        val action = actions.firstOrNull()?.let(text::action) ?: "no action"
        val more = (triggers.size - 1 + actions.size - 1).coerceAtLeast(0)
        return MacroListItem(
            id = id,
            name = name,
            category = category,
            enabled = enabled,
            summary = "$first → $action" + if (more > 0) " (+$more more)" else "",
            firstTrigger = triggers.firstOrNull(),
            missingPermissions = requirements.forMacro(this).count { !permissionStatus.isGranted(it) },
            hasUnsupportedItems = unsupportedItemCount > 0,
        )
    }
}
