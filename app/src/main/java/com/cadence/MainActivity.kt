package com.cadence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.domain.model.AppSettings
import com.cadence.domain.model.ThemeMode
import com.cadence.domain.repository.SettingsRepository
import com.cadence.platform.service.EngineServiceController
import com.cadence.presentation.navigation.CadenceNavRoot
import com.cadence.presentation.theme.CadenceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single activity hosting the Compose UI. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var engineServiceController: EngineServiceController

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Keeps the splash screen up until settings are loaded, so the theme doesn't flash. */
    private var settingsLoaded = false

    override fun onStart() {
        super.onStart()
        // Visible activity: starting the foreground service is allowed, and permissions may
        // have just been granted in system settings.
        engineServiceController.onAppVisible()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !settingsLoaded }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(settings) { if (settings != null) settingsLoaded = true }

            val current = settings ?: AppSettings()
            val darkTheme = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CadenceTheme(darkTheme = darkTheme, dynamicColor = current.dynamicColor) {
                CadenceNavRoot()
            }
        }
    }
}
