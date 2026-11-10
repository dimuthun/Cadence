package com.cadence.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.domain.engine.EngineHost
import com.cadence.domain.model.AppSettings
import com.cadence.domain.model.ThemeMode
import com.cadence.domain.repository.ExecutionLogRepository
import com.cadence.domain.repository.ImportResult
import com.cadence.domain.repository.MacroBackupRepository
import com.cadence.domain.repository.SettingsRepository
import com.cadence.platform.permission.SpecialAccess
import com.cadence.platform.permission.SpecialAccessChecker
import com.cadence.platform.storage.DocumentStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
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

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val batteryUnrestricted: Boolean = true,
    val isBusy: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: MacroBackupRepository,
    private val logRepository: ExecutionLogRepository,
    private val documentStorage: DocumentStorage,
    private val accessChecker: SpecialAccessChecker,
    private val engineHost: EngineHost,
    private val clock: Clock,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)
    private val busy = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.BUFFERED)

    val events: Flow<String> = messages.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, refreshTick, busy) { settings, _, isBusy ->
        SettingsUiState(
            settings = settings,
            batteryUnrestricted = accessChecker.isGranted(SpecialAccess.UNRESTRICTED_BATTERY),
            isBusy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refresh() = refreshTick.update { it + 1 }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    /** Saves the setting and immediately applies it to the existing log. */
    fun setLogRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.setLogRetentionDays(days)
            logRepository.deleteOlderThan(clock.instant().minus(Duration.ofDays(days.toLong())))
        }
    }

    fun suggestedExportName(): String =
        "cadence-macros-${LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)}.json"

    fun exportTo(uri: Uri) = runBusy {
        documentStorage.writeText(uri, backupRepository.exportAll())
        messages.send("Macros exported")
    }

    fun importFrom(uri: Uri) = runBusy {
        when (val result = backupRepository.import(documentStorage.readText(uri))) {
            is ImportResult.Success -> {
                val skipped = if (result.skipped > 0) " (${result.skipped} couldn't be read and were skipped)" else ""
                messages.send("Imported ${result.imported} macro(s) as disabled$skipped. Enable them on the Macros screen.")
                engineHost.startIfNeeded()
            }
            is ImportResult.InvalidFile -> messages.send(result.reason)
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } catch (e: IOException) {
                messages.send(e.message ?: "Couldn't access the file")
            } finally {
                busy.value = false
            }
        }
    }
}
