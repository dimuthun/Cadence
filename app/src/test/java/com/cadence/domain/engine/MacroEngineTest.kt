package com.cadence.domain.engine

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.OutcomeStatus
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.ActionHandler
import com.cadence.domain.plugin.ActionRegistry
import com.cadence.domain.plugin.ConstraintRegistry
import com.cadence.domain.plugin.TriggerHandler
import com.cadence.domain.plugin.TriggerRegistry
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.testutil.FailingTriggerHandler
import com.cadence.testutil.FakeConstraintEvaluator
import com.cadence.testutil.FakeExecutionLogRepository
import com.cadence.testutil.FakeMacroRepository
import com.cadence.testutil.FakeTriggerHandler
import com.cadence.testutil.RecordingActionHandler
import com.cadence.testutil.RecordingDiagnostics
import com.cadence.testutil.TEST_INSTANT
import com.cadence.testutil.testMacro
import java.time.Clock
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MacroEngineTest {

    private val clock = Clock.fixed(TEST_INSTANT, ZoneOffset.UTC)

    private val screenOn = TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.ON)
    private val notify = ActionConfig.ShowNotification(title = "Hello", text = "World")
    private val vibrate = ActionConfig.Vibrate(durationMillis = 200)
    private val whileCharging = ConstraintConfig.ChargingState(charging = true)

    private val executed = mutableListOf<ActionConfig>()
    private val screenHandler = FakeTriggerHandler(TriggerConfig.ScreenState::class)
    private val notifyHandler = RecordingActionHandler(ActionConfig.ShowNotification::class, executed)
    private val vibrateHandler = RecordingActionHandler(ActionConfig.Vibrate::class, executed)
    private val chargingEvaluator = FakeConstraintEvaluator(ConstraintConfig.ChargingState::class)

    private val macros = FakeMacroRepository()
    private val logs = FakeExecutionLogRepository()
    private val diagnostics = RecordingDiagnostics()
    private val granted = mutableSetOf<PermissionRequirement>()

    private fun createEngine(
        triggerHandlers: Set<TriggerHandler<*>> = setOf(screenHandler),
        actionHandlers: Set<ActionHandler<*>> = setOf(notifyHandler, vibrateHandler),
        actionTimeout: Duration = 30.seconds,
    ) = MacroEngine(
        macroRepository = macros,
        executionLogRepository = logs,
        triggerRegistry = TriggerRegistry(triggerHandlers),
        actionRegistry = ActionRegistry(actionHandlers),
        constraintRegistry = ConstraintRegistry(setOf(chargingEvaluator)),
        permissionStatus = { it in granted },
        clock = clock,
        diagnostics = diagnostics,
        actionTimeout = actionTimeout,
    )

    private fun screenMacro(
        id: Long = 1,
        actions: List<ActionConfig> = listOf(notify, vibrate),
        constraints: List<ConstraintConfig> = emptyList(),
    ) = testMacro(id = id, triggers = listOf(screenOn), actions = actions, constraints = constraints)

    @Test
    fun `trigger runs actions in order and records success`() = runTest {
        macros.set(screenMacro())
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn, data = mapOf("reason" to "test"))
        runCurrent()

        assertEquals(listOf(notify, vibrate), executed)
        val log = logs.recorded.single()
        assertEquals(ExecutionStatus.SUCCESS, log.status)
        assertEquals(screenOn, log.trigger)
        assertEquals(mapOf("reason" to "test"), log.triggerData)
        assertEquals(listOf(OutcomeStatus.SUCCESS, OutcomeStatus.SUCCESS), log.actionOutcomes.map { it.status })
        assertEquals(listOf(0, 1), log.actionOutcomes.map { it.index })
    }

    @Test
    fun `throwing action is recorded as failure and later actions still run`() = runTest {
        notifyHandler.behavior = { throw IllegalStateException("boom") }
        macros.set(screenMacro())
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()

        val log = logs.recorded.single()
        assertEquals(ExecutionStatus.PARTIAL_FAILURE, log.status)
        assertEquals(OutcomeStatus.FAILURE, log.actionOutcomes[0].status)
        assertEquals("boom", log.actionOutcomes[0].message)
        assertEquals(OutcomeStatus.SUCCESS, log.actionOutcomes[1].status)

        // The engine keeps working after the failure.
        screenHandler.fire(screenOn)
        runCurrent()
        assertEquals(2, logs.recorded.size)
    }

    @Test
    fun `fallback result counts as success and keeps its message`() = runTest {
        notifyHandler.behavior = { ActionResult.Fallback("Opened settings instead") }
        macros.set(screenMacro())
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()

        val log = logs.recorded.single()
        assertEquals(ExecutionStatus.SUCCESS, log.status)
        assertEquals(OutcomeStatus.FALLBACK, log.actionOutcomes[0].status)
        assertEquals("Opened settings instead", log.actionOutcomes[0].message)
    }

    @Test
    fun `unmet constraint skips all actions`() = runTest {
        chargingEvaluator.result = false
        macros.set(screenMacro(constraints = listOf(whileCharging)))
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()

        assertTrue(executed.isEmpty())
        val log = logs.recorded.single()
        assertEquals(ExecutionStatus.CONSTRAINTS_NOT_MET, log.status)
        assertEquals(whileCharging, log.failedConstraint)
    }

    @Test
    fun `throwing constraint is treated as not met`() = runTest {
        chargingEvaluator.error = IllegalStateException("sensor unavailable")
        macros.set(screenMacro(constraints = listOf(whileCharging)))
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()

        assertTrue(executed.isEmpty())
        assertEquals(ExecutionStatus.CONSTRAINTS_NOT_MET, logs.recorded.single().status)
        assertTrue(diagnostics.errors.any { "threw" in it })
    }

    @Test
    fun `met constraint lets actions run`() = runTest {
        chargingEvaluator.result = true
        macros.set(screenMacro(constraints = listOf(whileCharging)))
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()

        assertEquals(listOf(notify, vibrate), executed)
    }

    @Test
    fun `disabling a macro unregisters its triggers`() = runTest {
        val engine = createEngine()
        macros.set(screenMacro())
        engine.start(backgroundScope)
        runCurrent()
        assertEquals(1, screenHandler.activeRegistrations)
        assertEquals(setOf(1L), engine.activeMacroIds.value)

        macros.set(screenMacro().copy(enabled = false))
        runCurrent()

        assertEquals(0, screenHandler.activeRegistrations)
        assertEquals(emptySet<Long>(), engine.activeMacroIds.value)
        screenHandler.fire(screenOn)
        runCurrent()
        assertTrue(logs.recorded.isEmpty())
    }

    @Test
    fun `stopping the engine unregisters every trigger`() = runTest {
        val engine = createEngine()
        macros.set(screenMacro(id = 1), screenMacro(id = 2))
        engine.start(backgroundScope)
        runCurrent()
        assertEquals(2, screenHandler.activeRegistrations)
        assertTrue(engine.isRunning)

        engine.stop()
        runCurrent()

        assertEquals(0, screenHandler.activeRegistrations)
        assertFalse(engine.isRunning)
    }

    @Test
    fun `editing a macro re-registers it once and uses the new definition`() = runTest {
        macros.set(screenMacro())
        createEngine().start(backgroundScope)
        runCurrent()

        macros.set(screenMacro().copy(name = "Renamed", actions = listOf(vibrate)))
        runCurrent()
        assertEquals(1, screenHandler.activeRegistrations)

        screenHandler.fire(screenOn)
        runCurrent()
        assertEquals(listOf<ActionConfig>(vibrate), executed)
        assertEquals("Renamed", logs.recorded.single().macroName)
    }

    @Test
    fun `wait action delays the following actions`() = runTest {
        macros.set(screenMacro(actions = listOf(notify, ActionConfig.Wait(5_000), vibrate)))
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()
        assertEquals(listOf<ActionConfig>(notify), executed)

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(listOf<ActionConfig>(notify), executed)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(notify, vibrate), executed)
        assertEquals(ExecutionStatus.SUCCESS, logs.recorded.single().status)
    }

    @Test
    fun `disabling a macro mid-run cancels it and records cancellation`() = runTest {
        macros.set(screenMacro(actions = listOf(ActionConfig.Wait(10_000), notify)))
        createEngine().start(backgroundScope)
        runCurrent()
        screenHandler.fire(screenOn)
        runCurrent()

        macros.set(screenMacro(actions = listOf(ActionConfig.Wait(10_000), notify)).copy(enabled = false))
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()

        assertTrue(executed.isEmpty())
        val log = logs.recorded.single()
        assertEquals(ExecutionStatus.CANCELLED, log.status)
        assertTrue(log.actionOutcomes.isEmpty())
    }

    @Test
    fun `trigger firing while the macro is running is skipped`() = runTest {
        macros.set(screenMacro(actions = listOf(ActionConfig.Wait(10_000), notify)))
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()
        screenHandler.fire(screenOn)
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(1, logs.recorded.size)
        assertEquals(listOf<ActionConfig>(notify), executed)

        // Once the run finishes, the next event starts a new run.
        screenHandler.fire(screenOn)
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(2, logs.recorded.size)
    }

    @Test
    fun `slow action times out and the run continues`() = runTest {
        notifyHandler.behavior = {
            delay(60_000)
            ActionResult.Success()
        }
        macros.set(screenMacro())
        createEngine(actionTimeout = 1.seconds).start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()

        val log = logs.recorded.single()
        assertEquals(OutcomeStatus.FAILURE, log.actionOutcomes[0].status)
        assertTrue(log.actionOutcomes[0].message!!.startsWith("Timed out"))
        assertEquals(OutcomeStatus.SUCCESS, log.actionOutcomes[1].status)
    }

    @Test
    fun `action with missing permission fails without executing`() = runTest {
        val guardedNotify = RecordingActionHandler(
            ActionConfig.ShowNotification::class,
            executed,
            permissions = setOf(PermissionRequirement.POST_NOTIFICATIONS),
        )
        macros.set(screenMacro())
        createEngine(actionHandlers = setOf(guardedNotify, vibrateHandler)).start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()

        assertEquals(listOf<ActionConfig>(vibrate), executed)
        val outcome = logs.recorded.single().actionOutcomes[0]
        assertEquals(OutcomeStatus.FAILURE, outcome.status)
        assertTrue(outcome.message!!.contains("POST_NOTIFICATIONS"))
    }

    @Test
    fun `trigger with missing permission is registered after refresh once granted`() = runTest {
        val guardedScreen = FakeTriggerHandler(
            TriggerConfig.ScreenState::class,
            permissions = setOf(PermissionRequirement.USAGE_ACCESS),
        )
        val engine = createEngine(triggerHandlers = setOf(guardedScreen))
        macros.set(screenMacro())
        engine.start(backgroundScope)
        runCurrent()
        assertEquals(0, guardedScreen.activeRegistrations)
        assertTrue(diagnostics.errors.any { "missing permissions" in it })

        granted += PermissionRequirement.USAGE_ACCESS
        engine.refresh()
        runCurrent()

        assertEquals(1, guardedScreen.activeRegistrations)
    }

    @Test
    fun `action without a registered handler fails`() = runTest {
        macros.set(screenMacro(actions = listOf(ActionConfig.SpeakText("Hi"))))
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()

        val log = logs.recorded.single()
        assertEquals(ExecutionStatus.FAILURE, log.status)
        assertEquals("No handler registered for this action", log.actionOutcomes.single().message)
    }

    @Test
    fun `failing trigger only stops that trigger`() = runTest {
        val bootHandler = FailingTriggerHandler(TriggerConfig.BootCompleted::class)
        macros.set(
            testMacro(id = 1, triggers = listOf(TriggerConfig.BootCompleted, screenOn), actions = listOf(notify)),
            testMacro(id = 2, triggers = listOf(screenOn), actions = listOf(vibrate)),
        )
        createEngine(triggerHandlers = setOf(screenHandler, bootHandler)).start(backgroundScope)
        runCurrent()
        assertTrue(diagnostics.errors.any { "failed and was unregistered" in it })

        screenHandler.fire(screenOn)
        runCurrent()

        assertEquals(setOf(1L, 2L), logs.recorded.map { it.macroId }.toSet())
    }

    @Test
    fun `log write failure does not stop the engine`() = runTest {
        logs.failWrites = true
        macros.set(screenMacro())
        createEngine().start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn)
        runCurrent()
        screenHandler.fire(screenOn)
        runCurrent()

        assertEquals(listOf(notify, vibrate, notify, vibrate), executed)
        assertTrue(diagnostics.errors.any { "Failed to write execution log" in it })
    }

    @Test
    fun `manual execution can skip constraints and needs no trigger handler`() = runTest {
        chargingEvaluator.result = false
        val engine = createEngine()
        val macro = testMacro(actions = listOf(notify), constraints = listOf(whileCharging))

        val result = engine.execute(macro, TriggerEvent(TriggerConfig.Manual, TEST_INSTANT), checkConstraints = false)

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertEquals(1L, result.id)
        assertEquals(TriggerConfig.Manual, result.trigger)
        assertEquals(listOf<ActionConfig>(notify), executed)
    }

    @Test
    fun `replayed event with the same id runs a macro only once`() = runTest {
        macros.set(screenMacro(id = 1), screenMacro(id = 2))
        val engine = createEngine()
        engine.start(backgroundScope)
        runCurrent()

        screenHandler.fire(screenOn, id = "boot-42")
        runCurrent()
        // Re-registration (e.g. after a refresh) replays the same event.
        engine.refresh()
        runCurrent()
        screenHandler.fire(screenOn, id = "boot-42")
        runCurrent()

        // Each macro ran once; a new id runs again.
        assertEquals(listOf(1L, 2L), logs.recorded.map { it.macroId }.sorted())
        screenHandler.fire(screenOn, id = "boot-43")
        runCurrent()
        assertEquals(4, logs.recorded.size)
    }

    @Test
    fun `manual trigger in a macro does not report a missing handler`() = runTest {
        macros.set(testMacro(triggers = listOf(TriggerConfig.Manual)))
        createEngine().start(backgroundScope)
        runCurrent()

        assertTrue(diagnostics.errors.isEmpty())
    }
}
