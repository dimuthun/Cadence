package com.cadence.data.local.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.cadence.data.local.db.dao.ExecutionDao
import com.cadence.data.local.db.dao.GlobalVariableDao
import com.cadence.data.local.db.dao.MacroDao
import com.cadence.data.local.db.entity.ActionOutcomeEntity
import com.cadence.data.local.db.entity.ExecutionEntity
import com.cadence.data.local.db.entity.GlobalVariableEntity
import com.cadence.data.local.db.entity.MacroEntity

/**
 * Cadence's Room database.
 *
 * Schemas are exported to `app/schemas`. Every version bump needs a migration (prefer
 * `@AutoMigration`). There is intentionally no destructive fallback because this holds user data.
 *
 * - v1: macros, execution log.
 * - v2: global variables.
 */
@Database(
    entities = [MacroEntity::class, ExecutionEntity::class, ActionOutcomeEntity::class, GlobalVariableEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class CadenceDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    abstract fun executionDao(): ExecutionDao
    abstract fun globalVariableDao(): GlobalVariableDao

    companion object {
        const val NAME = "cadence.db"
    }
}
