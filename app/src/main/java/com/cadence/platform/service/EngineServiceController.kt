package com.cadence.platform.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.domain.engine.EngineDiagnostics
import com.cadence.domain.engine.EngineHost
import com.cadence.domain.engine.MacroEngine
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.repository.MacroRepository
import com.cadence.platform.permission.AndroidPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts the engine service when there's something to run, and re-registers triggers when the
 * environment changes (permissions granted, clock or time zone changed).
 *
 * Starting a foreground service from the background is only allowed in specific cases (app
 * visible, boot, exact alarm, time change, app update). Callers use it from exactly those places.
 */
@Singleton
class EngineServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val engine: MacroEngine,
    private val macroRepository: MacroRepository,
    private val permissionChecker: AndroidPermissionChecker,
    private val diagnostics: EngineDiagnostics,
    @param:ApplicationScope private val scope: CoroutineScope,
) : EngineHost {
    private val lastGranted = AtomicReference<Set<PermissionRequirement>?>(null)

    /** Starts the service if at least one macro can run. Returns `true` if a start was requested. */
    override suspend fun startIfNeeded(): Boolean {
        if (macroRepository.observeEnabledMacros().first().isEmpty()) return false
        return try {
            ContextCompat.startForegroundService(context, Intent(context, CadenceEngineService::class.java))
            true
        } catch (e: IllegalStateException) {
            diagnostics.onError(null, "Engine service start not allowed right now", e)
            false
        }
    }

    /**
     * Call whenever Cadence's UI becomes visible. Starts the engine and, if permissions changed
     * since last time (e.g. the user just granted usage access), re-registers triggers so
     * previously skipped ones start working.
     */
    fun onAppVisible() {
        val granted = permissionChecker.grantedRequirements()
        val previous = lastGranted.getAndSet(granted)
        if (previous != null && previous != granted) engine.refresh()
        scope.launch { startIfNeeded() }
    }

    /** The wall clock, time zone, or exact-alarm access changed: alarms must be recomputed. */
    fun onScheduleEnvironmentChanged() {
        engine.refresh()
    }
}
