package com.cadence.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.cadence.presentation.editor.MacroEditorScreen
import com.cadence.presentation.editor.MacroEditorViewModel
import com.cadence.presentation.home.HomeScreen
import com.cadence.presentation.log.LogScreen
import com.cadence.presentation.log.LogViewModel
import com.cadence.presentation.permissions.PermissionsScreen
import com.cadence.presentation.settings.SettingsScreen
import com.cadence.presentation.variables.VariablesScreen

/**
 * Single back stack navigation: tabs replace everything above Home ("exit through home"), and
 * detail screens (editor, per-macro log) are pushed on top. The back stack survives process death.
 */
class AppNavigator(private val backStack: NavBackStack<NavKey>) {

    val current: NavKey get() = backStack.last()

    /** The tab highlighted in the navigation bar. */
    val currentTab: NavKey get() = backStack.lastOrNull { it in TopLevelRoutes } ?: HomeRoute

    fun selectTab(route: NavKey) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (route != HomeRoute) backStack.add(route)
    }

    fun open(route: NavKey) {
        backStack.add(route)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}

private data class TabItem(val route: NavKey, val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

private val Tabs = listOf(
    TabItem(HomeRoute, "Macros", Icons.Filled.Home, Icons.Outlined.Home),
    TabItem(LogRoute, "Log", Icons.Filled.History, Icons.Outlined.History),
    TabItem(PermissionsRoute, "Permissions", Icons.Filled.Shield, Icons.Outlined.Shield),
    TabItem(SettingsRoute, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/** App root: navigation bar plus [NavDisplay]. */
@Composable
fun CadenceNavRoot() {
    val backStack = rememberNavBackStack(HomeRoute)
    val navigator = remember(backStack) { AppNavigator(backStack) }
    val showNavigationBar = navigator.current in TopLevelRoutes

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showNavigationBar) {
                NavigationBar {
                    Tabs.forEach { tab ->
                        val selected = navigator.currentTab == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigator.selectTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.back() },
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeScreen(
                        onCreateMacro = { navigator.open(EditorRoute()) },
                        onEditMacro = { id -> navigator.open(EditorRoute(id)) },
                        onShowMacroLog = { id, name -> navigator.open(MacroLogRoute(id, name)) },
                        onOpenSettings = { navigator.selectTab(SettingsRoute) },
                        onOpenPermissions = { navigator.selectTab(PermissionsRoute) },
                        onOpenVariables = { navigator.open(VariablesRoute) },
                    )
                }
                entry<VariablesRoute> { VariablesScreen(onBack = { navigator.back() }) }
                entry<EditorRoute> { key ->
                    val viewModel = hiltViewModel<MacroEditorViewModel, MacroEditorViewModel.Factory>(
                        creationCallback = { factory -> factory.create(key.macroId) },
                    )
                    MacroEditorScreen(viewModel = viewModel, onClose = { navigator.back() })
                }
                entry<LogRoute> {
                    val viewModel = hiltViewModel<LogViewModel, LogViewModel.Factory>(
                        creationCallback = { factory -> factory.create(null) },
                    )
                    LogScreen(viewModel = viewModel, title = "Log", onBack = null)
                }
                entry<MacroLogRoute> { key ->
                    val viewModel = hiltViewModel<LogViewModel, LogViewModel.Factory>(
                        creationCallback = { factory -> factory.create(key.macroId) },
                    )
                    LogScreen(viewModel = viewModel, title = key.macroName, onBack = { navigator.back() })
                }
                entry<PermissionsRoute> { PermissionsScreen() }
                entry<SettingsRoute> { SettingsScreen(onOpenVariables = { navigator.open(VariablesRoute) }) }
            },
        )
    }
}
