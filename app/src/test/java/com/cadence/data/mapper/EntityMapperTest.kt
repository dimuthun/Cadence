package com.cadence.data.mapper

import com.cadence.data.local.db.entity.ActionOutcomeEntity
import com.cadence.data.local.db.entity.ExecutionWithOutcomes
import com.cadence.data.serialization.CadenceJson
import com.cadence.data.serialization.ConfigJsonCodec
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.ActionOutcome
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.MacroExecution
import com.cadence.domain.model.OutcomeStatus
import com.cadence.domain.trigger.ConnectionEvent
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.testutil.TEST_INSTANT
import com.cadence.testutil.testMacro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityMapperTest {

    private val codec = ConfigJsonCodec(CadenceJson.instance)
    private val macroMapper = MacroEntityMapper(codec)
    private val executionMapper = ExecutionEntityMapper(codec)

    private val macro = testMacro(
        id = 7,
        name = "Home Wi-Fi",
        triggers = listOf(TriggerConfig.WifiConnection(ConnectionEvent.CONNECTED)),
        actions = listOf(ActionConfig.SetRingerMode(com.cadence.domain.action.RingerMode.NORMAL)),
        constraints = listOf(ConstraintConfig.ChargingState(charging = false)),
    ).copy(category = "Home")

    @Test
    fun `macro round trips through its entity`() {
        val restored = macroMapper.toDomain(macroMapper.toEntity(macro))

        assertEquals(macro, restored)
        assertTrue(restored.isRunnable)
    }

    @Test
    fun `macro with an unreadable item is flagged and not runnable`() {
        val entity = macroMapper.toEntity(macro).copy(
            constraintsJson = """[{"type":"constraint.from_the_future"}]""",
        )

        val restored = macroMapper.toDomain(entity)

        assertEquals(1, restored.unsupportedItemCount)
        assertTrue(restored.constraints.isEmpty())
        assertFalse(restored.isRunnable)
    }

    private val execution = MacroExecution(
        id = 3,
        macroId = 7,
        macroName = "Home Wi-Fi",
        trigger = TriggerConfig.WifiConnection(ConnectionEvent.CONNECTED),
        triggerData = mapOf("network" to "wifi"),
        startedAt = TEST_INSTANT,
        finishedAt = TEST_INSTANT.plusMillis(250),
        status = ExecutionStatus.PARTIAL_FAILURE,
        actionOutcomes = listOf(
            ActionOutcome(0, ActionConfig.Vibrate(100), OutcomeStatus.SUCCESS, null, 120),
            ActionOutcome(1, ActionConfig.SetWifi(com.cadence.domain.action.ToggleState.OFF), OutcomeStatus.FALLBACK, "Opened panel", 5),
            ActionOutcome(2, ActionConfig.LaunchApp("com.example"), OutcomeStatus.FAILURE, "Not installed", 3),
        ),
    )

    @Test
    fun `execution round trips through its entities`() {
        val (entity, outcomes) = executionMapper.toEntities(execution)
        val row = ExecutionWithOutcomes(entity, outcomes.mapIndexed { i, o -> o.copy(id = i + 1L, executionId = entity.id) })

        assertEquals(execution, executionMapper.toDomain(row))
    }

    @Test
    fun `outcomes are restored in position order`() {
        val (entity, outcomes) = executionMapper.toEntities(execution)
        val row = ExecutionWithOutcomes(entity, outcomes.reversed())

        assertEquals(listOf(0, 1, 2), executionMapper.toDomain(row)!!.actionOutcomes.map { it.index })
    }

    @Test
    fun `execution with an unreadable trigger or status is dropped`() {
        val (entity, outcomes) = executionMapper.toEntities(execution)

        assertNull(executionMapper.toDomain(ExecutionWithOutcomes(entity.copy(triggerJson = "{}"), outcomes)))
        assertNull(executionMapper.toDomain(ExecutionWithOutcomes(entity.copy(status = "EXPLODED"), outcomes)))
    }

    @Test
    fun `unreadable outcome is dropped individually`() {
        val (entity, outcomes) = executionMapper.toEntities(execution)
        val broken = outcomes.toMutableList<ActionOutcomeEntity>().apply {
            this[1] = this[1].copy(actionJson = """{"type":"action.from_the_future"}""")
        }

        val restored = executionMapper.toDomain(ExecutionWithOutcomes(entity, broken))!!

        assertEquals(listOf(0, 2), restored.actionOutcomes.map { it.index })
    }
}
