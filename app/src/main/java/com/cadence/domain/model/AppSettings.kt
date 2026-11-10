package com.cadence.domain.model

import com.cadence.domain.permission.PermissionRequirement

/**
 * User preferences, stored in DataStore.
 *
 * @property logRetentionDays execution log entries older than this are deleted.
 * @property explainedPermissions permissions whose explanation screen the user has already seen,
 * so it is not shown again before the system prompt.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val logRetentionDays: Int = DEFAULT_LOG_RETENTION_DAYS,
    val onboardingCompleted: Boolean = false,
    val explainedPermissions: Set<PermissionRequirement> = emptySet(),
) {
    companion object {
        const val DEFAULT_LOG_RETENTION_DAYS = 14

        /** Choices offered in Settings. */
        val LOG_RETENTION_OPTIONS: List<Int> = listOf(1, 7, 14, 30, 90)
    }
}

/** App theme preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
