package com.cadence.domain.action

/** What happened when an action ran. */
sealed interface ActionResult {

    /** The action did what it was configured to do. */
    data class Success(val message: String? = null) : ActionResult

    /**
     * Android blocks the direct action for third-party apps, so a fallback ran instead,
     * e.g. the Wi-Fi settings panel opened. [message] is shown to the user in the log.
     */
    data class Fallback(val message: String) : ActionResult

    /** The action could not run. The engine logs it and continues with the next action. */
    data class Failure(val reason: String, val cause: Throwable? = null) : ActionResult
}
