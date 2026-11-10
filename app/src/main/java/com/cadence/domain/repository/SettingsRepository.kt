package com.cadence.domain.repository

import com.cadence.domain.model.AppSettings
import com.cadence.domain.model.ThemeMode
import com.cadence.domain.permission.PermissionRequirement
import kotlinx.coroutines.flow.Flow

/** Storage of [AppSettings]. Implemented with DataStore in the data layer. */
interface SettingsRepository {

    /** Current settings. Emits defaults if nothing is stored or the file can't be read. */
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    /** Sets log retention; values outside 1..365 are clamped. */
    suspend fun setLogRetentionDays(days: Int)

    suspend fun setOnboardingCompleted(completed: Boolean)

    /** Remembers that the explanation screen for [requirement] was shown. */
    suspend fun markPermissionExplained(requirement: PermissionRequirement)
}
