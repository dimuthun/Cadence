package com.cadence.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Macro list. The start destination. */
@Serializable
data object HomeRoute : NavKey

/** Execution log across all macros. */
@Serializable
data object LogRoute : NavKey

/** Permissions center. */
@Serializable
data object PermissionsRoute : NavKey

/** App settings. */
@Serializable
data object SettingsRoute : NavKey

/** Create (`macroId == null`) or edit a macro. */
@Serializable
data class EditorRoute(val macroId: Long? = null) : NavKey

/** Execution log filtered to one macro. */
@Serializable
data class MacroLogRoute(val macroId: Long, val macroName: String) : NavKey

/** Global variables. */
@Serializable
data object VariablesRoute : NavKey

/** Routes shown in the navigation bar. */
val TopLevelRoutes: List<NavKey> = listOf(HomeRoute, LogRoute, PermissionsRoute, SettingsRoute)
