package com.cadence.data.backup

import com.cadence.core.coroutines.DefaultDispatcher
import com.cadence.data.local.db.dao.MacroDao
import com.cadence.data.mapper.MacroEntityMapper
import com.cadence.domain.repository.ImportResult
import com.cadence.domain.repository.MacroBackupRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [MacroBackupRepository] reading and writing the Room `macros` table. */
@Singleton
class RoomMacroBackupRepository @Inject constructor(
    private val dao: MacroDao,
    private val mapper: MacroEntityMapper,
    private val codec: MacroBackupCodec,
    private val clock: Clock,
    @param:DefaultDispatcher private val mappingDispatcher: CoroutineDispatcher,
) : MacroBackupRepository {

    /** Macros with unreadable items are exported with only their readable items. */
    override suspend fun exportAll(): String {
        val rows = dao.getAll()
        return withContext(mappingDispatcher) {
            codec.encode(rows.map(mapper::toDomain), clock.instant())
        }
    }

    override suspend fun import(document: String): ImportResult {
        val decoded = withContext(mappingDispatcher) { codec.decode(document) }
        return when (decoded) {
            is MacroBackupCodec.DecodeResult.Invalid -> ImportResult.InvalidFile(decoded.reason)
            is MacroBackupCodec.DecodeResult.Decoded -> {
                val now = clock.instant()
                val entities = withContext(mappingDispatcher) {
                    decoded.macros.map { mapper.toEntity(it.copy(id = 0, enabled = false, updatedAt = now)) }
                }
                if (entities.isNotEmpty()) dao.insertAll(entities)
                ImportResult.Success(imported = entities.size, skipped = decoded.skipped)
            }
        }
    }
}
