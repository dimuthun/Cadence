package com.cadence.platform.diagnostics

import android.util.Log
import com.cadence.domain.engine.EngineDiagnostics
import javax.inject.Inject
import javax.inject.Singleton

/** Writes engine problems to Logcat under the `CadenceEngine` tag. */
@Singleton
class LogcatEngineDiagnostics @Inject constructor() : EngineDiagnostics {
    override fun onError(macroId: Long?, message: String, cause: Throwable?) {
        val prefix = macroId?.let { "[macro $it] " }.orEmpty()
        Log.w(TAG, prefix + message, cause)
    }

    private companion object {
        const val TAG = "CadenceEngine"
    }
}
