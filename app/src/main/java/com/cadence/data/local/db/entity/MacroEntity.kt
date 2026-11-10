package com.cadence.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stored macro. Trigger, action, and constraint lists are JSON arrays written by
 * [com.cadence.data.serialization.ConfigJsonCodec], so new config types need no schema change.
 * Timestamps are epoch milliseconds.
 */
@Entity(
    tableName = "macros",
    indices = [Index("enabled"), Index("category")],
)
data class MacroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val category: String,
    @ColumnInfo(name = "triggers_json") val triggersJson: String,
    @ColumnInfo(name = "actions_json") val actionsJson: String,
    @ColumnInfo(name = "constraints_json") val constraintsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
