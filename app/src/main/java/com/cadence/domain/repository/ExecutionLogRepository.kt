package com.cadence.domain.repository

import com.cadence.domain.model.MacroExecution
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Storage of the macro execution log. Implemented with Room in the data layer. */
interface ExecutionLogRepository {

    /** The most recent executions, newest first. */
    fun observeExecutions(limit: Int = DEFAULT_PAGE_SIZE): Flow<List<MacroExecution>>

    /** The most recent executions of one macro, newest first. */
    fun observeExecutionsForMacro(macroId: Long, limit: Int = DEFAULT_PAGE_SIZE): Flow<List<MacroExecution>>

    /** Stores [execution] and returns its id. */
    suspend fun record(execution: MacroExecution): Long

    /** Deletes entries that started before [cutoff]. Returns the number deleted. */
    suspend fun deleteOlderThan(cutoff: Instant): Int

    /** Deletes every log entry. */
    suspend fun clear()

    companion object {
        const val DEFAULT_PAGE_SIZE = 200
    }
}
