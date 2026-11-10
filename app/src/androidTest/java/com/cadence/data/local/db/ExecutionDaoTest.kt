package com.cadence.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadence.data.local.db.dao.ExecutionDao
import com.cadence.data.local.db.entity.ActionOutcomeEntity
import com.cadence.data.local.db.entity.ExecutionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutionDaoTest {

    private lateinit var db: CadenceDatabase
    private lateinit var dao: ExecutionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CadenceDatabase::class.java)
            .build()
        dao = db.executionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun execution(macroId: Long = 1, startedAt: Long) = ExecutionEntity(
        macroId = macroId,
        macroName = "Macro $macroId",
        triggerJson = """{"type":"trigger.manual"}""",
        triggerDataJson = "{}",
        startedAt = startedAt,
        finishedAt = startedAt + 10,
        status = "SUCCESS",
        failedConstraintJson = null,
    )

    private fun outcome(position: Int) = ActionOutcomeEntity(
        position = position,
        actionJson = """{"type":"action.vibrate","durationMillis":100}""",
        status = "SUCCESS",
        message = null,
        durationMillis = 5,
    )

    private fun outcomeRowCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM action_outcomes").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun insertStoresExecutionWithItsOutcomes() = runTest {
        val id = dao.insert(execution(startedAt = 100), listOf(outcome(0), outcome(1)))

        val row = dao.observeRecent(limit = 10).first().single()
        assertEquals(id, row.execution.id)
        assertEquals(listOf(0, 1), row.outcomes.map { it.position }.sorted())
        assertTrue(row.outcomes.all { it.executionId == id })
    }

    @Test
    fun recentIsNewestFirstAndLimited() = runTest {
        dao.insert(execution(startedAt = 100), emptyList())
        dao.insert(execution(startedAt = 300), emptyList())
        dao.insert(execution(startedAt = 200), emptyList())

        assertEquals(listOf(300L, 200L), dao.observeRecent(limit = 2).first().map { it.execution.startedAt })
    }

    @Test
    fun forMacroOnlyReturnsThatMacro() = runTest {
        dao.insert(execution(macroId = 1, startedAt = 100), emptyList())
        dao.insert(execution(macroId = 2, startedAt = 200), emptyList())

        assertEquals(listOf(2L), dao.observeForMacro(macroId = 2, limit = 10).first().map { it.execution.macroId })
    }

    @Test
    fun deleteOlderThanRemovesOldRowsAndCascadesToOutcomes() = runTest {
        dao.insert(execution(startedAt = 100), listOf(outcome(0), outcome(1)))
        dao.insert(execution(startedAt = 500), listOf(outcome(0)))

        val deleted = dao.deleteOlderThan(cutoffMillis = 500)

        assertEquals(1, deleted)
        assertEquals(listOf(500L), dao.observeRecent(limit = 10).first().map { it.execution.startedAt })
        assertEquals(1, outcomeRowCount())
    }

    @Test
    fun clearRemovesEverything() = runTest {
        dao.insert(execution(startedAt = 100), listOf(outcome(0)))

        dao.clear()

        assertTrue(dao.observeRecent(limit = 10).first().isEmpty())
        assertEquals(0, outcomeRowCount())
    }
}
