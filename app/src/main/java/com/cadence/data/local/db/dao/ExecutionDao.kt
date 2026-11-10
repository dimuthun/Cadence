package com.cadence.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.cadence.data.local.db.entity.ActionOutcomeEntity
import com.cadence.data.local.db.entity.ExecutionEntity
import com.cadence.data.local.db.entity.ExecutionWithOutcomes
import kotlinx.coroutines.flow.Flow

/** Queries on the execution log tables. */
@Dao
abstract class ExecutionDao {

    /** Inserts an execution and its outcomes atomically. Returns the execution id. */
    @Transaction
    open suspend fun insert(execution: ExecutionEntity, outcomes: List<ActionOutcomeEntity>): Long {
        val id = insertExecution(execution)
        if (outcomes.isNotEmpty()) {
            insertOutcomes(outcomes.map { it.copy(executionId = id) })
        }
        return id
    }

    @Transaction
    @Query("SELECT * FROM executions ORDER BY started_at DESC, id DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<ExecutionWithOutcomes>>

    @Transaction
    @Query("SELECT * FROM executions WHERE macro_id = :macroId ORDER BY started_at DESC, id DESC LIMIT :limit")
    abstract fun observeForMacro(macroId: Long, limit: Int): Flow<List<ExecutionWithOutcomes>>

    /** Deletes executions (and, by cascade, their outcomes) started before [cutoffMillis]. */
    @Query("DELETE FROM executions WHERE started_at < :cutoffMillis")
    abstract suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM executions")
    abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insertExecution(execution: ExecutionEntity): Long

    @Insert
    protected abstract suspend fun insertOutcomes(outcomes: List<ActionOutcomeEntity>)
}
