package com.cadence.data.repository

import com.cadence.core.coroutines.DefaultDispatcher
import com.cadence.data.local.db.dao.MacroDao
import com.cadence.data.mapper.MacroEntityMapper
import com.cadence.domain.model.Macro
import com.cadence.domain.repository.MacroRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [MacroRepository]. JSON mapping runs on [mappingDispatcher] so collectors on the
 * main thread never decode configs.
 */
@Singleton
class RoomMacroRepository @Inject constructor(
    private val dao: MacroDao,
    private val mapper: MacroEntityMapper,
    private val clock: Clock,
    @param:DefaultDispatcher private val mappingDispatcher: CoroutineDispatcher,
) : MacroRepository {

    override fun observeMacros(): Flow<List<Macro>> =
        dao.observeAll()
            .map { rows -> rows.map(mapper::toDomain) }
            .flowOn(mappingDispatcher)

    /** Excludes macros with unreadable items, see [Macro.isRunnable]. */
    override fun observeEnabledMacros(): Flow<List<Macro>> =
        dao.observeEnabled()
            .map { rows -> rows.map(mapper::toDomain).filter { it.isRunnable } }
            .flowOn(mappingDispatcher)

    override fun observeCategories(): Flow<List<String>> = dao.observeCategories()

    override suspend fun getMacro(id: Long): Macro? =
        dao.getById(id)?.let { withContext(mappingDispatcher) { mapper.toDomain(it) } }

    /**
     * Inserts when [Macro.id] is 0, otherwise updates. Sets [Macro.updatedAt] to now, and for new
     * macros [Macro.createdAt] too. Updating an id that no longer exists inserts it again.
     */
    override suspend fun saveMacro(macro: Macro): Long {
        val now = clock.instant()
        val entity = withContext(mappingDispatcher) {
            mapper.toEntity(
                if (macro.id == 0L) macro.copy(createdAt = now, updatedAt = now) else macro.copy(updatedAt = now),
            )
        }
        if (entity.id == 0L) return dao.insert(entity)
        return if (dao.update(entity) > 0) entity.id else dao.insert(entity)
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled, clock.millis())
    }

    override suspend fun deleteMacro(id: Long) {
        dao.deleteById(id)
    }
}
