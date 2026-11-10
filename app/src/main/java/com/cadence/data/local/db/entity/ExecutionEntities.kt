package com.cadence.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One macro run in the log. There's deliberately no foreign key to `macros`: the history stays
 * when a macro is deleted, and [macroName] keeps it readable.
 *
 * @property status an [com.cadence.domain.model.ExecutionStatus] name.
 */
@Entity(
    tableName = "executions",
    indices = [Index("macro_id"), Index("started_at")],
)
data class ExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "macro_id") val macroId: Long,
    @ColumnInfo(name = "macro_name") val macroName: String,
    @ColumnInfo(name = "trigger_json") val triggerJson: String,
    @ColumnInfo(name = "trigger_data_json") val triggerDataJson: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    val status: String,
    @ColumnInfo(name = "failed_constraint_json") val failedConstraintJson: String?,
)

/**
 * Result of one action within an [ExecutionEntity]. Deleted together with its execution.
 *
 * @property position order of the action within the run (`index` is an SQL keyword).
 * @property status an [com.cadence.domain.model.OutcomeStatus] name.
 */
@Entity(
    tableName = "action_outcomes",
    foreignKeys = [
        ForeignKey(
            entity = ExecutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["execution_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("execution_id")],
)
data class ActionOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "execution_id") val executionId: Long = 0,
    val position: Int,
    @ColumnInfo(name = "action_json") val actionJson: String,
    val status: String,
    val message: String?,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
)

/** An execution with its action outcomes, loaded by Room in one transaction. */
data class ExecutionWithOutcomes(
    @Embedded val execution: ExecutionEntity,
    @Relation(parentColumn = "id", entityColumn = "execution_id")
    val outcomes: List<ActionOutcomeEntity>,
)
