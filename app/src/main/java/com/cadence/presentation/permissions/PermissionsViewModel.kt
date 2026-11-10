package com.cadence.presentation.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.domain.model.Macro
import com.cadence.domain.plugin.RequirementResolver
import com.cadence.domain.repository.MacroRepository
import com.cadence.platform.permission.SpecialAccess
import com.cadence.platform.permission.SpecialAccessChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One row in the Permissions center. */
data class AccessRow(val access: SpecialAccess, val granted: Boolean, val neededBy: List<String>)

data class PermissionsUiState(
    val needed: List<AccessRow> = emptyList(),
    val other: List<AccessRow> = emptyList(),
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    macroRepository: MacroRepository,
    private val checker: SpecialAccessChecker,
    private val requirements: RequirementResolver,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    val uiState: StateFlow<PermissionsUiState> = combine(macroRepository.observeMacros(), refreshTick) { macros, _ ->
        val rows = SpecialAccess.entries.map { access ->
            val neededBy = access.requirement?.let { requirement ->
                macros.filter { requirement in requirements.forMacro(it) }.map { it.name }
            }.orEmpty()
            AccessRow(access, checker.isGranted(access), neededBy)
        }
        val (needed, other) = rows.partition {
            it.neededBy.isNotEmpty() || (it.access == SpecialAccess.UNRESTRICTED_BATTERY && macros.any(Macro::isRunnable))
        }
        PermissionsUiState(needed = needed, other = other)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PermissionsUiState())

    /** Re-checks every status, e.g. after returning from Settings. */
    fun refresh() = refreshTick.update { it + 1 }
}
