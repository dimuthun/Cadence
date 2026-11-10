package com.cadence.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.device.Located
import com.cadence.domain.engine.EngineHost
import com.cadence.domain.engine.MacroEngine
import com.cadence.domain.model.Macro
import com.cadence.domain.model.OutcomeStatus
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.permission.PermissionStatusProvider
import com.cadence.domain.plugin.ActionRegistry
import com.cadence.domain.plugin.ConstraintRegistry
import com.cadence.domain.plugin.RequirementResolver
import com.cadence.domain.variable.GlobalVariableRepository
import com.cadence.domain.plugin.TriggerRegistry
import com.cadence.domain.repository.MacroRepository
import com.cadence.domain.repository.SettingsRepository
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.platform.device.InstalledApp
import com.cadence.platform.device.InstalledAppsProvider
import com.cadence.platform.device.PairedDevice
import com.cadence.platform.device.PairedDevicesProvider
import com.cadence.platform.location.FusedLocationSource
import com.cadence.platform.location.PlayServicesAvailability
import com.cadence.platform.permission.SpecialAccess
import com.cadence.presentation.catalog.ActionCatalog
import com.cadence.presentation.catalog.CatalogEntry
import com.cadence.presentation.catalog.ConfigRules
import com.cadence.presentation.catalog.ConfigText
import com.cadence.presentation.catalog.ConstraintCatalog
import com.cadence.presentation.catalog.TriggerCatalog
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Steps of the editor flow, in order. */
enum class EditorStep(val title: String) {
    TRIGGERS("Triggers"),
    ACTIONS("Actions"),
    CONSTRAINTS("Constraints"),
    DETAILS("Details"),
}

/** Which list an item belongs to. */
enum class ItemKind(val noun: String) { TRIGGER("trigger"), ACTION("action"), CONSTRAINT("constraint") }

/**
 * An item open in the config dialog.
 *
 * @property index position being edited, or `null` for a new item.
 * @property block for actions, the (possibly nested) action list the item belongs to.
 */
data class EditingItem(val kind: ItemKind, val index: Int?, val config: Any, val block: BlockPath = emptyList())

/** Another macro that actions and constraints can refer to. */
data class MacroRef(val id: Long, val name: String)

data class EditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val step: EditorStep = EditorStep.TRIGGERS,
    val name: String = "",
    val category: String = Macro.DEFAULT_CATEGORY,
    val enabled: Boolean = true,
    val triggers: List<TriggerConfig> = emptyList(),
    val actions: List<ActionConfig> = emptyList(),
    val constraints: List<ConstraintConfig> = emptyList(),
    val unsupportedItemCount: Int = 0,
    val existingCategories: List<String> = emptyList(),
    val missingAccess: List<SpecialAccess> = emptyList(),
    val picker: ItemKind? = null,
    /** Where a picked action goes. */
    val pickerBlock: BlockPath = emptyList(),
    val editing: EditingItem? = null,
    /** Other macros, for "Run macro", "Macro enabled" and similar items. */
    val otherMacros: List<MacroRef> = emptyList(),
    val rationale: SpecialAccess? = null,
    val apps: List<InstalledApp> = emptyList(),
    val pairedDevices: List<PairedDevice> = emptyList(),
    val isSaving: Boolean = false,
    val isTestRunning: Boolean = false,
    val isLocating: Boolean = false,
    /** Incremented when a location is filled in for the user, so the form refreshes its fields. */
    val locationVersion: Int = 0,
    val playServicesAvailable: Boolean = true,
    val globalVariableNames: List<String> = emptyList(),
)

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data class Message(val text: String) : EditorEvent

    /** The UI should ask for [access] (with its disclosure where required). */
    data class RequestAccess(val access: SpecialAccess) : EditorEvent
}

/**
 * State and logic for creating or editing a macro step by step. Changes stay in memory until
 * [save] writes them.
 */
@HiltViewModel(assistedFactory = MacroEditorViewModel.Factory::class)
class MacroEditorViewModel @AssistedInject constructor(
    @Assisted private val macroId: Long?,
    private val macroRepository: MacroRepository,
    private val settingsRepository: SettingsRepository,
    private val triggerRegistry: TriggerRegistry,
    private val actionRegistry: ActionRegistry,
    private val constraintRegistry: ConstraintRegistry,
    private val permissionStatus: PermissionStatusProvider,
    private val requirements: RequirementResolver,
    private val globalVariables: GlobalVariableRepository,
    private val engine: MacroEngine,
    private val engineHost: EngineHost,
    private val installedApps: InstalledAppsProvider,
    private val pairedDevices: PairedDevicesProvider,
    private val locationSource: FusedLocationSource,
    playServices: PlayServicesAvailability,
    private val clock: Clock,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(macroId: Long?): MacroEditorViewModel
    }

    private val _uiState = MutableStateFlow(
        EditorUiState(isNew = macroId == null, playServicesAvailable = playServices.isAvailable()),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EditorEvent> = events.receiveAsFlow()

    private var original: Macro? = null

    /** Catalog entries that have a registered plugin on this build. */
    val triggerEntries: List<CatalogEntry<TriggerConfig>> = TriggerCatalog.filter { triggerRegistry.supports(it.type) }
    val actionEntries: List<CatalogEntry<ActionConfig>> = ActionCatalog.filter {
        ActionConfig.ControlFlowAction::class.java.isAssignableFrom(it.type.java) || actionRegistry.supports(it.type)
    }
    val constraintEntries: List<CatalogEntry<ConstraintConfig>> = ConstraintCatalog.filter {
        ConstraintConfig.EngineConstraint::class.java.isAssignableFrom(it.type.java) || constraintRegistry.supports(it.type)
    }

    init {
        viewModelScope.launch {
            val macro = macroId?.let { macroRepository.getMacro(it) }
            original = macro
            val categories = macroRepository.observeCategories().first()
            _uiState.update { state ->
                if (macro == null) {
                    state.copy(isLoading = false, isNew = true, existingCategories = categories)
                } else {
                    state.copy(
                        isLoading = false,
                        isNew = false,
                        name = macro.name,
                        category = macro.category,
                        enabled = macro.enabled,
                        triggers = macro.triggers,
                        actions = macro.actions,
                        constraints = macro.constraints,
                        unsupportedItemCount = macro.unsupportedItemCount,
                        existingCategories = categories,
                    )
                }
            }
            refreshPermissions()
        }
        viewModelScope.launch {
            val apps = installedApps.launchableApps()
            _uiState.update { it.copy(apps = apps) }
        }
        viewModelScope.launch {
            macroRepository.observeMacros().collect { macros ->
                _uiState.update { state ->
                    state.copy(otherMacros = macros.filter { it.id != macroId }.map { MacroRef(it.id, it.name) })
                }
            }
        }
        viewModelScope.launch {
            globalVariables.observeAll().collect { variables ->
                _uiState.update { state -> state.copy(globalVariableNames = variables.map { it.name }) }
            }
        }
        refreshPairedDevices()
    }

    // region Navigation between steps

    fun goToStep(step: EditorStep) = _uiState.update { it.copy(step = step) }

    fun nextStep() = _uiState.update { state ->
        state.copy(step = EditorStep.entries.getOrElse(state.step.ordinal + 1) { state.step })
    }

    fun previousStep() = _uiState.update { state ->
        state.copy(step = EditorStep.entries.getOrElse(state.step.ordinal - 1) { state.step })
    }

    // endregion

    // region Item editing

    /** Opens the picker; for actions, [block] is the nested list the new action goes into. */
    fun openPicker(kind: ItemKind, block: BlockPath = emptyList()) =
        _uiState.update { it.copy(picker = kind, pickerBlock = block) }

    fun closePicker() = _uiState.update { it.copy(picker = null) }

    /** Called with the default config of the picked catalog entry. */
    fun onItemPicked(kind: ItemKind, config: Any) {
        val block = _uiState.value.pickerBlock
        _uiState.update { it.copy(picker = null) }
        val item = EditingItem(kind, index = null, config = config, block = if (kind == ItemKind.ACTION) block else emptyList())
        if (ConfigRules.hasSettings(config)) {
            _uiState.update { it.copy(editing = item) }
        } else {
            commit(item)
        }
    }

    fun editItem(kind: ItemKind, index: Int, block: BlockPath = emptyList()) {
        val state = _uiState.value
        val config: Any = when (kind) {
            ItemKind.TRIGGER -> state.triggers[index]
            ItemKind.ACTION -> state.actions.blockAt(block)[index]
            ItemKind.CONSTRAINT -> state.constraints[index]
        }
        _uiState.update { it.copy(editing = EditingItem(kind, index, config, block)) }
    }

    fun updateEditing(config: Any) = _uiState.update { state ->
        state.copy(editing = state.editing?.copy(config = config))
    }

    fun cancelEditing() = _uiState.update { it.copy(editing = null) }

    /** Fills the area or place being edited (location area, sunrise/sunset) with the phone's current position. */
    fun useCurrentLocation() {
        val config = _uiState.value.editing?.config
        if (config !is TriggerConfig.LocationArea && config !is Located) return
        if (!permissionStatus.isGranted(PermissionRequirement.FINE_LOCATION)) {
            events.trySend(EditorEvent.RequestAccess(SpecialAccess.LOCATION))
            return
        }
        _uiState.update { it.copy(isLocating = true) }
        viewModelScope.launch {
            val fix = locationSource.currentFix()
            _uiState.update { state ->
                val editing = state.editing
                // The user may have closed or switched the form while locating.
                val current = editing?.config
                val located: Any? = when {
                    fix == null -> null
                    current is TriggerConfig.LocationArea -> current.copy(latitude = fix.latitude, longitude = fix.longitude)
                    current is Located -> current.at(fix.latitude, fix.longitude)
                    else -> null
                }
                if (editing == null || located == null) {
                    state.copy(isLocating = false)
                } else {
                    state.copy(
                        isLocating = false,
                        editing = editing.copy(config = located),
                        locationVersion = state.locationVersion + 1,
                    )
                }
            }
            if (fix == null) events.send(EditorEvent.Message("Couldn't get your location. Is location turned on?"))
        }
    }

    fun confirmEditing() {
        val item = _uiState.value.editing ?: return
        if (!ConfigRules.isComplete(item.config)) return
        _uiState.update { it.copy(editing = null) }
        commit(item)
    }

    fun removeItem(kind: ItemKind, index: Int, block: BlockPath = emptyList()) {
        _uiState.update { state ->
            when (kind) {
                ItemKind.TRIGGER -> state.copy(triggers = state.triggers.filterIndexed { i, _ -> i != index })
                ItemKind.ACTION -> state.copy(
                    actions = state.actions.updateBlock(block) { list -> list.filterIndexed { i, _ -> i != index } },
                )
                ItemKind.CONSTRAINT -> state.copy(constraints = state.constraints.filterIndexed { i, _ -> i != index })
            }
        }
        refreshPermissions()
    }

    /** Moves an action up (-1) or down (+1) within its block; actions run in list order. */
    fun moveAction(index: Int, delta: Int, block: BlockPath = emptyList()) {
        _uiState.update { state ->
            state.copy(
                actions = state.actions.updateBlock(block) { list ->
                    val target = index + delta
                    if (target !in list.indices) list else list.toMutableList().apply { add(target, removeAt(index)) }
                },
            )
        }
    }

    private fun commit(item: EditingItem) {
        _uiState.update { state ->
            when (item.kind) {
                ItemKind.TRIGGER -> state.copy(triggers = state.triggers.upsert(item.index, item.config as TriggerConfig))
                ItemKind.ACTION -> state.copy(
                    actions = state.actions.updateBlock(item.block) { it.upsert(item.index, item.config as ActionConfig) },
                )
                ItemKind.CONSTRAINT -> state.copy(constraints = state.constraints.upsert(item.index, item.config as ConstraintConfig))
            }
        }
        refreshPermissions()
        offerRationaleFor(item.config)
    }

    private fun <T> List<T>.upsert(index: Int?, value: T): List<T> =
        if (index == null) this + value else toMutableList().also { it[index] = value }

    // endregion

    // region Details

    fun setName(name: String) = _uiState.update { it.copy(name = name) }

    fun setCategory(category: String) = _uiState.update { it.copy(category = category) }

    fun setEnabled(enabled: Boolean) = _uiState.update { it.copy(enabled = enabled) }

    // endregion

    // region Permissions

    /** Re-checks permissions, e.g. when the screen resumes after Settings. */
    fun refreshPermissions() {
        val required = requiredPermissions(_uiState.value)
        val missing = required.filterNot(permissionStatus::isGranted).map(SpecialAccess::of)
        _uiState.update { it.copy(missingAccess = missing) }
        refreshPairedDevices()
    }

    /** The explanation dialog was answered; don't show it again for this permission. */
    fun onRationaleHandled() {
        val access = _uiState.value.rationale ?: return
        _uiState.update { it.copy(rationale = null) }
        access.requirement?.let { requirement ->
            viewModelScope.launch { settingsRepository.markPermissionExplained(requirement) }
        }
    }

    /** First time a newly added item needs a missing permission, explain it before asking. */
    private fun offerRationaleFor(config: Any) {
        val needed = permissionsOf(config).filterNot(permissionStatus::isGranted)
        if (needed.isEmpty()) return
        viewModelScope.launch {
            val explained = settingsRepository.settings.first().explainedPermissions
            val first = needed.firstOrNull { it !in explained } ?: return@launch
            _uiState.update { it.copy(rationale = SpecialAccess.of(first)) }
        }
    }

    private fun requiredPermissions(state: EditorUiState): Set<PermissionRequirement> = buildSet {
        (state.triggers + state.actions + state.constraints).forEach { addAll(permissionsOf(it)) }
    }

    private fun permissionsOf(config: Any): Set<PermissionRequirement> = requirements.forConfig(config)

    private fun refreshPairedDevices() {
        viewModelScope.launch {
            val devices = pairedDevices.pairedDevices()
            _uiState.update { it.copy(pairedDevices = devices) }
        }
    }

    // endregion

    // region Save and test

    /** Validates, saves, and starts the engine if needed. Shows the step with a problem otherwise. */
    fun save() {
        val state = _uiState.value
        when {
            state.triggers.isEmpty() -> {
                _uiState.update { it.copy(step = EditorStep.TRIGGERS) }
                events.trySend(EditorEvent.Message("Add at least one trigger"))
                return
            }
            state.actions.isEmpty() -> {
                _uiState.update { it.copy(step = EditorStep.ACTIONS) }
                events.trySend(EditorEvent.Message("Add at least one action"))
                return
            }
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            macroRepository.saveMacro(draft(state))
            engineHost.startIfNeeded()
            _uiState.update { it.copy(isSaving = false) }
            events.send(EditorEvent.Saved)
        }
    }

    /**
     * Runs the current actions once, ignoring triggers and constraints. Runs in the application
     * scope so waits complete even if the user leaves the editor.
     */
    fun testRun() {
        val state = _uiState.value
        if (state.actions.isEmpty()) {
            events.trySend(EditorEvent.Message("Add an action to test"))
            return
        }
        _uiState.update { it.copy(isTestRunning = true) }
        applicationScope.launch {
            val result = engine.execute(
                macro = draft(state),
                event = TriggerEvent(TriggerConfig.Manual, clock.instant()),
                checkConstraints = false,
            )
            val failed = result.actionOutcomes.count { it.status == OutcomeStatus.FAILURE }
            val fallback = result.actionOutcomes.count { it.status == OutcomeStatus.FALLBACK }
            val text = buildString {
                append("Test run done")
                if (failed > 0) append(": $failed failed")
                if (fallback > 0) append(if (failed > 0) ", " else ": ").append("$fallback need you")
                if (failed + fallback > 0) append(". See the log for details.")
            }
            _uiState.update { it.copy(isTestRunning = false) }
            events.send(EditorEvent.Message(text))
        }
    }

    private fun draft(state: EditorUiState): Macro {
        val now = clock.instant()
        val labels = state.apps.associate { it.packageName to it.label }
        val text = ConfigText { labels[it] ?: it }
        val name = state.name.trim().ifBlank {
            "${state.triggers.firstOrNull()?.let(text::trigger) ?: "Macro"} → ${state.actions.firstOrNull()?.let(text::action) ?: ""}"
        }
        return Macro(
            id = original?.id ?: 0,
            name = name,
            enabled = state.enabled,
            category = state.category.trim().ifBlank { Macro.DEFAULT_CATEGORY },
            triggers = state.triggers,
            actions = state.actions,
            constraints = state.constraints,
            createdAt = original?.createdAt ?: now,
            updatedAt = now,
        )
    }

    // endregion
}
