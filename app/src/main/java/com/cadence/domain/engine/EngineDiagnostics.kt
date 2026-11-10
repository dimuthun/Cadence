package com.cadence.domain.engine

/**
 * Receives problems that happen outside a recorded run: a trigger handler crashing, a missing
 * handler or permission, or a log write failing. The platform implementation writes to Logcat.
 */
interface EngineDiagnostics {

    /**
     * @param macroId the affected macro, or `null` for engine-wide problems.
     */
    fun onError(macroId: Long?, message: String, cause: Throwable? = null)

    companion object {
        /** Discards everything. */
        val None: EngineDiagnostics = object : EngineDiagnostics {
            override fun onError(macroId: Long?, message: String, cause: Throwable?) = Unit
        }
    }
}
