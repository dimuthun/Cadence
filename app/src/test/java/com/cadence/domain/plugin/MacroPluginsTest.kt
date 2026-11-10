package com.cadence.domain.plugin

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ToggleState
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.MacroExecution
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.testutil.FakeExecutionLogRepository
import com.cadence.testutil.FakeMacroRepository
import com.cadence.testutil.TEST_INSTANT
import com.cadence.testutil.testContext
import com.cadence.testutil.testMacro
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroPluginsTest {

    private val macros = FakeMacroRepository()

    @Test
    fun `set macro enabled toggles and notifies the host when enabling`() = runTest {
        macros.set(testMacro(id = 1, enabled = true), testMacro(id = 2, enabled = false))
        var hostCalls = 0
        val handler = SetMacroEnabledActionHandler(macros) { hostCalls++ }

        handler.execute(ActionConfig.SetMacroEnabled(2, ToggleState.TOGGLE), testContext())
        handler.execute(ActionConfig.SetMacroEnabled(null, ToggleState.OFF), testContext(testMacro(id = 1)))

        assertTrue(macros.getMacro(2)!!.enabled)
        assertFalse(macros.getMacro(1)!!.enabled)
        assertEquals(1, hostCalls)
    }

    @Test
    fun `macro enabled constraint treats deleted macros as disabled`() = runTest {
        macros.set(testMacro(id = 1, enabled = true))
        val evaluator = MacroEnabledEvaluator(macros)

        assertTrue(evaluator.evaluate(ConstraintConfig.MacroEnabled(1), testContext()))
        assertTrue(evaluator.evaluate(ConstraintConfig.MacroEnabled(99, enabled = false), testContext()))
    }

    @Test
    fun `cooldown ignores skipped runs and holds after the interval`() = runTest {
        val log = FakeExecutionLogRepository()
        fun run(minutesAgo: Long, status: ExecutionStatus) = MacroExecution(
            macroId = 1, macroName = "m", trigger = TriggerConfig.Manual,
            startedAt = TEST_INSTANT.minusSeconds(minutesAgo * 60), finishedAt = TEST_INSTANT, status = status,
        )
        val evaluator = TimeSinceLastRunEvaluator(log, Clock.fixed(TEST_INSTANT, ZoneOffset.UTC))
        val cooldown = ConstraintConfig.TimeSinceLastRun(minutes = 10)
        val context = testContext(testMacro(id = 1))

        assertTrue(evaluator.evaluate(cooldown, context)) // never ran

        log.record(run(minutesAgo = 5, status = ExecutionStatus.SUCCESS))
        assertFalse(evaluator.evaluate(cooldown, context))

        log.clear()
        log.record(run(minutesAgo = 20, status = ExecutionStatus.SUCCESS))
        log.record(run(minutesAgo = 1, status = ExecutionStatus.CONSTRAINTS_NOT_MET))
        assertTrue(evaluator.evaluate(cooldown, context))
    }
}
