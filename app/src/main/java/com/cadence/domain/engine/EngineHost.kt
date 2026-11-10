package com.cadence.domain.engine

/**
 * Hosts the running [MacroEngine] (a foreground service on Android). Presentation code uses it
 * after saving or enabling a macro, without depending on platform classes.
 */
interface EngineHost {

    /** Makes sure the engine is running if any macro can run. Returns `true` if a start was requested. */
    suspend fun startIfNeeded(): Boolean
}
