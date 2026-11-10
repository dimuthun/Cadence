package com.cadence.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.cadence.domain.model.AppSettings
import com.cadence.domain.model.ThemeMode
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] backed by Preferences DataStore. Values are stored as enum names, and
 * unknown values (e.g. from a newer version) fall back to defaults.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSettings)
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setLogRetentionDays(days: Int) {
        dataStore.edit { it[Keys.LOG_RETENTION_DAYS] = days.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS) }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun markPermissionExplained(requirement: PermissionRequirement) {
        dataStore.edit { prefs ->
            prefs[Keys.EXPLAINED_PERMISSIONS] = prefs[Keys.EXPLAINED_PERMISSIONS].orEmpty() + requirement.name
        }
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: defaults.themeMode,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            logRetentionDays = prefs[Keys.LOG_RETENTION_DAYS] ?: defaults.logRetentionDays,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            explainedPermissions = prefs[Keys.EXPLAINED_PERMISSIONS].orEmpty()
                .mapNotNullTo(mutableSetOf()) { stored -> PermissionRequirement.entries.firstOrNull { it.name == stored } },
        )
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val EXPLAINED_PERMISSIONS = stringSetPreferencesKey("explained_permissions")
    }

    companion object {
        const val FILE_NAME = "settings"
        const val MIN_RETENTION_DAYS = 1
        const val MAX_RETENTION_DAYS = 365
    }
}
