package com.cadence.presentation.variables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.domain.variable.GlobalVariable
import com.cadence.domain.variable.GlobalVariableRepository
import com.cadence.domain.variable.VariableNames
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VariablesUiState(val isLoading: Boolean = true, val variables: List<GlobalVariable> = emptyList())

/** Lists and edits global variables. */
@HiltViewModel
class VariablesViewModel @Inject constructor(
    private val repository: GlobalVariableRepository,
) : ViewModel() {

    val uiState: StateFlow<VariablesUiState> = repository.observeAll()
        .map { VariablesUiState(isLoading = false, variables = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VariablesUiState())

    /** Returns an error message, or `null` if saved. */
    fun save(name: String, value: String, isNew: Boolean): String? {
        val trimmed = name.trim()
        if (!VariableNames.isValidUserName(trimmed)) return "Use letters, digits and _; don't start with a digit"
        if (isNew && uiState.value.variables.any { it.name == trimmed }) return "A variable with this name already exists"
        viewModelScope.launch { repository.set(trimmed, value) }
        return null
    }

    fun delete(name: String) {
        viewModelScope.launch { repository.delete(name) }
    }
}
