package com.cadence.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A global variable. Added in database version 2. */
@Entity(tableName = "variables")
data class GlobalVariableEntity(
    @PrimaryKey val name: String,
    val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
