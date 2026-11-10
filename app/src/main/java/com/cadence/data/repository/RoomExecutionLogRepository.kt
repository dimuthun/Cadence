package com.cadence.data.repository

import com.cadence.core.coroutines.DefaultDispatcher
import com.cadence.data.local.db.dao.ExecutionDao
import com.cadence.data.mapper.ExecutionEntityMapper
import com.cadence.domain.model.MacroExecution
import com.cadence.domain.repository.ExecutionLogRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Room-backed [ExecutionLogRepository]. Rows this version can't read are skipped. */
@Singleton
class RoomExecutionLogRepository @Inject constructor(
    private val dao: ExecutionDao,
    private val mapper: ExecutionEntityMapper,
    @param:DefaultDispatcher private val mappingDispatcher: CoroutineDispatcher,
) : ExecutionLogRepository {

    override fun observeExecutions(limit: Int): Flow<List<MacroExecution>> =
        dao.observeRecent(limit)
            .map { rows -> rows.mapNotNull(mapper::toDomain) }
            .flowOn(mappingDispatcher)

    override fun observeExecutionsForMacro(macroId: Long, limit: Int): Flow<List<MacroExecution>> =
        dao.observeForMacro(macroId, limit)
            .map { rows -> rows.mapNotNull(mapper::toDomain) }
            .flowOn(mappingDispatcher)

    override suspend fun record(execution: MacroExecution): Long {
        val (entity, outcomes) = withContext(mappingDispatcher) { mapper.toEntities(execution) }
        return dao.insert(entity, outcomes)
    }

    override suspend fun deleteOlderThan(cutoff: Instant): Int = dao.deleteOlderThan(cutoff.toEpochMilli())

    override suspend fun clear() {
        dao.clear()
    }
}
