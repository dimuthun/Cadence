package com.cadence.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cadence.data.local.db.entity.MacroEntity
import kotlinx.coroutines.flow.Flow

/** Queries on the `macros` table. */
@Dao
interface MacroDao {

    @Query("SELECT * FROM macros ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE enabled = 1 ORDER BY name COLLATE NOCASE, id")
    fun observeEnabled(): Flow<List<MacroEntity>>

    @Query("SELECT DISTINCT category FROM macros ORDER BY category COLLATE NOCASE")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM macros ORDER BY name COLLATE NOCASE, id")
    suspend fun getAll(): List<MacroEntity>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getById(id: Long): MacroEntity?

    /** Returns the new row id. */
    @Insert
    suspend fun insert(macro: MacroEntity): Long

    @Insert
    suspend fun insertAll(macros: List<MacroEntity>): List<Long>

    /** Returns the number of rows updated (0 if [macro] doesn't exist). */
    @Update
    suspend fun update(macro: MacroEntity): Int

    @Query("UPDATE macros SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
