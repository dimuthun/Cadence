package com.cadence.domain.repository

import com.cadence.domain.model.Macro
import kotlinx.coroutines.flow.Flow

/** Storage of macros. Implemented with Room in the data layer. */
interface MacroRepository {

    /** All macros, ordered by name. */
    fun observeMacros(): Flow<List<Macro>>

    /** Enabled macros only. The engine registers triggers for exactly this set. */
    fun observeEnabledMacros(): Flow<List<Macro>>

    /** Distinct categories in use, for the Home screen filter. */
    fun observeCategories(): Flow<List<String>>

    /** The macro with [id], or `null` if it doesn't exist. */
    suspend fun getMacro(id: Long): Macro?

    /** Inserts ([Macro.id] == 0) or updates a macro. Returns its id. */
    suspend fun saveMacro(macro: Macro): Long

    /** Enables or disables the macro with [id]. */
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Deletes the macro with [id]. Its log entries are kept. */
    suspend fun deleteMacro(id: Long)
}
