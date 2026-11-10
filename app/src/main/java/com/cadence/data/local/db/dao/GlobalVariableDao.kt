package com.cadence.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cadence.data.local.db.entity.GlobalVariableEntity
import kotlinx.coroutines.flow.Flow

/** Queries on the `variables` table. */
@Dao
interface GlobalVariableDao {

    @Query("SELECT * FROM variables ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<GlobalVariableEntity>>

    @Query("SELECT value FROM variables WHERE name = :name")
    suspend fun getValue(name: String): String?

    @Upsert
    suspend fun upsert(variable: GlobalVariableEntity)

    @Query("DELETE FROM variables WHERE name = :name")
    suspend fun delete(name: String): Int
}
