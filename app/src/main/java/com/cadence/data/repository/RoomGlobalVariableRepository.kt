package com.cadence.data.repository

import com.cadence.data.local.db.dao.GlobalVariableDao
import com.cadence.data.local.db.entity.GlobalVariableEntity
import com.cadence.domain.variable.GlobalVariable
import com.cadence.domain.variable.GlobalVariableRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [GlobalVariableRepository]. */
@Singleton
class RoomGlobalVariableRepository @Inject constructor(
    private val dao: GlobalVariableDao,
    private val clock: Clock,
) : GlobalVariableRepository {

    override fun observeAll(): Flow<List<GlobalVariable>> = dao.observeAll().map { rows ->
        rows.map { GlobalVariable(it.name, it.value, Instant.ofEpochMilli(it.updatedAt)) }
    }

    override suspend fun get(name: String): String? = dao.getValue(name)

    override suspend fun set(name: String, value: String) {
        dao.upsert(GlobalVariableEntity(name, value, clock.millis()))
    }

    override suspend fun delete(name: String) {
        dao.delete(name)
    }
}
