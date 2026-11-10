package com.cadence.domain.repository

/**
 * Export and import of macros as a JSON document. The Settings screen handles picking the file
 * (Storage Access Framework); this works purely on the file contents.
 */
interface MacroBackupRepository {

    /** Returns every macro as a backup document. */
    suspend fun exportAll(): String

    /**
     * Adds the macros in [document] as new, **disabled** macros. They start disabled so the
     * permission explanation flow runs when the user enables each one on this device.
     */
    suspend fun import(document: String): ImportResult
}

/** Outcome of [MacroBackupRepository.import]. */
sealed interface ImportResult {

    /**
     * @property imported macros added.
     * @property skipped macros in the file that this version could not read.
     */
    data class Success(val imported: Int, val skipped: Int) : ImportResult

    /** The document isn't a readable Cadence backup. [reason] is shown to the user. */
    data class InvalidFile(val reason: String) : ImportResult
}
