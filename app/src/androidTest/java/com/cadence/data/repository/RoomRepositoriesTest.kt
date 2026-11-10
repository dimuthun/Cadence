package com.cadence.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadence.data.backup.MacroBackupCodec
import com.cadence.data.backup.RoomMacroBackupRepository
import com.cadence.data.local.db.CadenceDatabase
import com.cadence.data.mapper.ExecutionEntityMapper
import com.cadence.data.mapper.MacroEntityMapper
import com.cadence.data.serialization.CadenceJson
import com.cadence.data.serialization.ConfigJsonCodec
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.ActionOutcome
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.Macro
import com.cadence.domain.model.MacroExecution
import com.cadence.domain.model.OutcomeStatus
import com.cadence.domain.repository.ImportResult
import com.cadence.domain.trigger.TriggerConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Repositories against a real (in-memory) Room database and the real JSON codecs. */
@RunWith(AndroidJUnit4::class)
class RoomRepositoriesTest {

    private val now = Instant.parse("2026-11-10T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var db: CadenceDatabase
    private lateinit var macros: RoomMacroRepository
    private lateinit var logs: RoomExecutionLogRepository
    private lateinit var backup: RoomMacroBackupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CadenceDatabase::class.java)
            .build()
        val codec = ConfigJsonCodec(CadenceJson.instance)
        val macroMapper = MacroEntityMapper(codec)
        macros = RoomMacroRepository(db.macroDao(), macroMapper, clock, Dispatchers.Default)
        logs = RoomExecutionLogRepository(db.executionDao(), ExecutionEntityMapper(codec), Dispatchers.Default)
        backup = RoomMacroBackupRepository(
            db.macroDao(),
            macroMapper,
            MacroBackupCodec(CadenceJson.instance),
            clock,
            Dispatchers.Default,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newMacro(name: String = "Screen on") = Macro(
        name = name,
        triggers = listOf(TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.ON)),
        actions = listOf(ActionConfig.Vibrate(200), ActionConfig.Wait(1_000)),
        constraints = listOf(ConstraintConfig.BatteryLevelRange(20, 100)),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun saveNewMacroAssignsIdAndTimestamps() = runTest {
        val id = macros.saveMacro(newMacro())

        val stored = macros.getMacro(id)!!
        assertEquals(newMacro().copy(id = id, createdAt = now, updatedAt = now), stored)
    }

    @Test
    fun saveExistingMacroUpdatesIt() = runTest {
        val id = macros.saveMacro(newMacro())

        macros.saveMacro(macros.getMacro(id)!!.copy(name = "Renamed"))

        assertEquals(listOf("Renamed"), macros.observeMacros().first().map { it.name })
    }

    @Test
    fun enabledMacrosExcludeDisabledAndUnreadable() = runTest {
        macros.saveMacro(newMacro("Runnable"))
        val disabled = macros.saveMacro(newMacro("Disabled"))
        macros.setEnabled(disabled, enabled = false)
        val broken = macros.saveMacro(newMacro("Broken"))
        db.openHelper.writableDatabase.execSQL(
            "UPDATE macros SET actions_json = '[{\"type\":\"action.from_the_future\"}]' WHERE id = $broken",
        )

        assertEquals(listOf("Runnable"), macros.observeEnabledMacros().first().map { it.name })
        val brokenMacro = macros.getMacro(broken)!!
        assertEquals(1, brokenMacro.unsupportedItemCount)
        assertFalse(brokenMacro.isRunnable)
    }

    @Test
    fun executionLogRoundTrips() = runTest {
        val execution = MacroExecution(
            macroId = 1,
            macroName = "Screen on",
            trigger = TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.ON),
            triggerData = mapOf("source" to "test"),
            startedAt = now,
            finishedAt = now.plusMillis(30),
            status = ExecutionStatus.PARTIAL_FAILURE,
            failedConstraint = null,
            actionOutcomes = listOf(
                ActionOutcome(0, ActionConfig.Vibrate(200), OutcomeStatus.SUCCESS, null, 10),
                ActionOutcome(1, ActionConfig.LaunchApp("com.missing"), OutcomeStatus.FAILURE, "Not installed", 2),
            ),
        )

        val id = logs.record(execution)

        assertEquals(listOf(execution.copy(id = id)), logs.observeExecutions().first())
        assertEquals(1, logs.deleteOlderThan(now.plusSeconds(1)))
        assertTrue(logs.observeExecutions().first().isEmpty())
    }

    @Test
    fun exportThenImportAddsDisabledCopies() = runTest {
        macros.saveMacro(newMacro("Original"))

        val document = backup.exportAll()
        val result = backup.import(document)

        assertEquals(ImportResult.Success(imported = 1, skipped = 0), result)
        val all = macros.observeMacros().first()
        assertEquals(2, all.size)
        val imported = all.single { it.id != all.minOf { m -> m.id } }
        assertEquals("Original", imported.name)
        assertFalse(imported.enabled)
        assertEquals(newMacro().actions, imported.actions)
    }

    @Test
    fun importOfInvalidDocumentChangesNothing() = runTest {
        val result = backup.import("not a backup")

        assertTrue(result is ImportResult.InvalidFile)
        assertTrue(macros.observeMacros().first().isEmpty())
    }
}
