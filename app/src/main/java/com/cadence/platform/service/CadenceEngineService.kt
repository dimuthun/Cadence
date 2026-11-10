package com.cadence.platform.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cadence.domain.engine.EngineDiagnostics
import com.cadence.domain.engine.MacroEngine
import com.cadence.domain.repository.MacroRepository
import com.cadence.platform.notification.CadenceNotifications
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps [MacroEngine] running for always-on triggers.
 *
 * Uses the `specialUse` type (declared with a justification in the manifest) and an ongoing,
 * minimum-priority notification. It stops itself when no macro can run, so it never sits idle
 * in the background.
 */
@AndroidEntryPoint
class CadenceEngineService : LifecycleService() {

    @Inject lateinit var engine: MacroEngine
    @Inject lateinit var macroRepository: MacroRepository
    @Inject lateinit var notifications: CadenceNotifications
    @Inject lateinit var diagnostics: EngineDiagnostics

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (!enterForeground(activeMacros = 0)) return
        engine.start(engineScope)
        lifecycleScope.launch {
            macroRepository.observeEnabledMacros()
                .map { macros -> macros.size }
                .distinctUntilChanged()
                .collect { count ->
                    if (count == 0) {
                        stopSelf()
                    } else {
                        notifications.updateEngineStatus(count)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Each startForegroundService() call must be answered with startForeground().
        enterForeground(activeMacros = engine.activeMacroIds.value.size)
        return START_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        engineScope.cancel()
        super.onDestroy()
    }

    /** Returns `false` (and stops) if Android refuses foreground state, e.g. after a sticky restart. */
    private fun enterForeground(activeMacros: Int): Boolean = try {
        ServiceCompat.startForeground(
            this,
            CadenceNotifications.ENGINE_STATUS_ID,
            notifications.engineStatus(activeMacros),
            foregroundServiceType(),
        )
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (API 31+) extends IllegalStateException.
        diagnostics.onError(null, "Foreground start not allowed; stopping engine service", e)
        stopSelf()
        false
    }

    private fun foregroundServiceType(): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        else -> 0
    }
}
