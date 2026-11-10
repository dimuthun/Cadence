package com.cadence.domain.engine

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ConditionMatch
import com.cadence.domain.action.ConditionalBlock
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.MacroExecution
import com.cadence.domain.plugin.ActionRegistry
import com.cadence.domain.plugin.ConstraintRegistry
import com.cadence.domain.plugin.TriggerRegistry
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.testutil.FakeConstraintEvaluator
import com.cadence.testutil.FakeExecutionLogRepository
import com.cadence.testutil.FakeMacroRepository
import com.cadence.testutil.FakeTriggerHandler
import com.cadence.testutil.RecordingActionHandler
import com.cadence.testutil.TEST_INSTANT
import com.cadence.testutil.testMacro
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** If/else, loops, stop, wait-for-trigger, macro calls and engine constraints. */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowControlTest {

    private val executed = mutableListOf<ActionConfig>()
    private val notify = ActionConfig.ShowNotification("n", "")
    private val vibrate = ActionConfig.Vibrate(100)
    private val charging = ConstraintConfig.ChargingState(true)
    private val screenOn = TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.ON)

    private val chargingEvaluator = FakeConstraintEvaluator(ConstraintConfig.ChargingState::class)
    private val screenHandler = FakeTriggerHandler(TriggerConfig.ScreenState::class)
    private val macros = FakeMacroRepository()
    private val logs = FakeExecutionLogRepository()

    private val engine = MacroEngine(
        macroRepository = macros,
        executionLogRepository = logs,
        triggerRegistry = TriggerRegistry(setOf(screenHandler)),
        actionRegistry = ActionRegistry(
            setOf(
                RecordingActionHandler(ActionConfig.ShowNotification::class, executed),
                RecordingActionHandler(ActionConfig.Vibrate::class, executed),
            ),
        ),
        constraintRegistry = ConstraintRegistry(setOf(chargingEvaluator)),
        permissionStatus = { true },
        clock = Clock.fixed(TEST_INSTANT, ZoneOffset.UTC),
    )

    private suspend fun run(vararg actions: ActionConfig, constraints: List<ConstraintConfig> = emptyList()): MacroExecution =
        engine.execute(testMacro(actions = actions.toList(), constraints = constraints), TriggerEvent(TriggerConfig.Manual, TEST_INSTANT))

    private fun ifCharging(then: List<ActionConfig>, otherwise: List<ActionConfig> = emptyList()) =
        ActionConfig.IfElse(listOf(ConditionalBlock(listOf(charging), actions = then)), otherwise)

    // region If / else

    @Test
    fun `if runs the matching branch or else`() = runTest {
        chargingEvaluator.result = false
        run(ifCharging(listOf(notify), listOf(vibrate)))
        assertEquals(listOf<ActionConfig>(vibrate), executed)

        executed.clear()
        chargingEvaluator.result = true
        run(ifCharging(listOf(notify), listOf(vibrate)))
        assertEquals(listOf<ActionConfig>(notify), executed)
    }

    @Test
    fun `else-if picks the first branch that holds and empty conditions always hold`() = runTest {
        chargingEvaluator.result = false
        val action = ActionConfig.IfElse(
            listOf(
                ConditionalBlock(listOf(charging), actions = listOf(notify)),
                ConditionalBlock(emptyList(), actions = listOf(vibrate)),
            ),
        )

        val result = run(action)

        assertEquals(listOf<ActionConfig>(vibrate), executed)
        assertEquals("Else-if 1 met", result.actionOutcomes.first().message)
    }

    @Test
    fun `any-match conditions need only one`() = runTest {
        chargingEvaluator.result = false
        val conditions = listOf(charging, ConstraintConfig.Group(ConstraintConfig.Group.Operator.NONE, listOf(charging)))

        run(ActionConfig.IfElse(listOf(ConditionalBlock(conditions, ConditionMatch.ANY, listOf(notify)))))

        assertEquals(listOf<ActionConfig>(notify), executed)
    }

    // endregion

    // region Repeat

    @Test
    fun `repeat count runs the body n times and logs the count first`() = runTest {
        val result = run(ActionConfig.Repeat(count = 3, actions = listOf(notify)))

        assertEquals(3, executed.size)
        assertEquals("Repeated 3 times", result.actionOutcomes.first().message)
        assertEquals(4, result.actionOutcomes.size)
    }

    @Test
    fun `while loop checks conditions before each round`() = runTest {
        chargingEvaluator.result = false

        run(ActionConfig.Repeat(ActionConfig.Repeat.Mode.WHILE, conditions = listOf(charging), actions = listOf(notify)))

        assertTrue(executed.isEmpty())
    }

    @Test
    fun `exit loop leaves the innermost loop`() = runTest {
        chargingEvaluator.result = true
        run(
            ActionConfig.Repeat(
                ActionConfig.Repeat.Mode.UNTIL_EXIT,
                actions = listOf(notify, ifCharging(listOf(ActionConfig.ExitLoop)), vibrate),
            ),
            vibrate,
        )

        assertEquals(listOf(notify, vibrate), executed)
    }

    @Test
    fun `until-exit loops stop at the safety limit`() = runTest {
        val result = run(ActionConfig.Repeat(ActionConfig.Repeat.Mode.UNTIL_EXIT, actions = listOf(notify), maxIterations = 5))

        assertEquals(5, executed.size)
        assertTrue(result.actionOutcomes.first().message!!.contains("safety limit"))
    }

    @Test
    fun `next iteration skips the rest of the body`() = runTest {
        run(ActionConfig.Repeat(count = 2, actions = listOf(vibrate, ActionConfig.NextIteration, notify)))

        assertEquals(listOf<ActionConfig>(vibrate, vibrate), executed)
    }

    @Test
    fun `loop index is available and nested loops restore it`() = runTest {
        val seen = mutableListOf<String?>()
        val engine = MacroEngine(
            macros, logs, TriggerRegistry(emptySet()),
            ActionRegistry(setOf(object : com.cadence.domain.plugin.ActionHandler<ActionConfig.Vibrate> {
                override val configType = ActionConfig.Vibrate::class
                override suspend fun execute(config: ActionConfig.Vibrate, context: ExecutionContext) =
                    com.cadence.domain.action.ActionResult.Success().also { seen += context.locals["loop.index"] }
            })),
            ConstraintRegistry(emptySet()), { true }, Clock.fixed(TEST_INSTANT, ZoneOffset.UTC),
        )
        val inner = ActionConfig.Repeat(count = 2, actions = listOf(vibrate))
        engine.execute(
            testMacro(actions = listOf(ActionConfig.Repeat(count = 2, actions = listOf(inner, vibrate)))),
            TriggerEvent(TriggerConfig.Manual, TEST_INSTANT),
        )

        assertEquals(listOf("1", "2", "1", "1", "2", "2"), seen)
    }

    // endregion

    // region Stop

    @Test
    fun `stop run ends the run successfully, even from inside a loop`() = runTest {
        val result = run(ActionConfig.Repeat(count = 3, actions = listOf(notify, ActionConfig.StopRun)), vibrate)

        assertEquals(listOf<ActionConfig>(notify), executed)
        assertEquals(ExecutionStatus.SUCCESS, result.status)
    }

    // endregion

    // region Wait for trigger

    private fun TestScope.startRun(vararg actions: ActionConfig) = backgroundScope.async { run(*actions) }

    @Test
    fun `wait for trigger continues when the trigger fires`() = runTest {
        val running = startRun(ActionConfig.WaitForTrigger(screenOn, timeoutSeconds = 60), notify)
        runCurrent()
        assertTrue(executed.isEmpty())

        screenHandler.fire(screenOn, data = mapOf("source" to "test"))
        runCurrent()

        assertEquals(listOf<ActionConfig>(notify), executed)
        assertEquals("Trigger fired", running.await().actionOutcomes.first().message)
        assertEquals(0, screenHandler.activeRegistrations)
    }

    @Test
    fun `wait for trigger gives up after the timeout`() = runTest {
        val running = startRun(ActionConfig.WaitForTrigger(screenOn, timeoutSeconds = 60), notify)
        runCurrent()
        advanceTimeBy(60_001)
        runCurrent()

        assertEquals(listOf<ActionConfig>(notify), executed)
        assertTrue(running.await().actionOutcomes.first().message!!.startsWith("Gave up"))
    }

    // endregion

    // region Macros calling macros

    @Test
    fun `run macro executes the other macro inline with its own log entry`() = runTest {
        macros.set(testMacro(id = 2, name = "Helper", actions = listOf(vibrate)))

        run(ActionConfig.RunMacro(2), notify)

        assertEquals(listOf(vibrate, notify), executed)
        assertEquals(setOf(1L, 2L), logs.recorded.map { it.macroId }.toSet())
    }

    @Test
    fun `macros calling themselves stop at the depth limit`() = runTest {
        val selfCaller = testMacro(id = 1, actions = listOf(ActionConfig.RunMacro(1)))
        macros.set(selfCaller)

        engine.execute(selfCaller, TriggerEvent(TriggerConfig.Manual, TEST_INSTANT))

        assertEquals(MacroEngine.MAX_CALL_DEPTH + 1, logs.recorded.size)
        assertTrue(logs.recorded.first().actionOutcomes.single().message!!.contains("nested"))
    }

    @Test
    fun `macro running constraint sees runs in progress`() = runTest {
        macros.set(testMacro(id = 2, actions = listOf(ActionConfig.Wait(10_000))))
        val helper = backgroundScope.async { engine.execute(macros.getMacro(2)!!, TriggerEvent(TriggerConfig.Manual, TEST_INSTANT)) }
        runCurrent()

        val whileRunning = run(notify, constraints = listOf(ConstraintConfig.MacroRunning(2, running = true)))
        assertEquals(ExecutionStatus.SUCCESS, whileRunning.status)

        advanceTimeBy(10_001)
        helper.await()
        val afterwards = run(notify, constraints = listOf(ConstraintConfig.MacroRunning(2, running = true)))
        assertEquals(ExecutionStatus.CONSTRAINTS_NOT_MET, afterwards.status)
    }

    @Test
    fun `group constraints combine with all, any, none`() = runTest {
        chargingEvaluator.result = true
        val group = { op: ConstraintConfig.Group.Operator -> ConstraintConfig.Group(op, listOf(charging)) }

        assertEquals(ExecutionStatus.SUCCESS, run(notify, constraints = listOf(group(ConstraintConfig.Group.Operator.ALL))).status)
        assertEquals(ExecutionStatus.SUCCESS, run(notify, constraints = listOf(group(ConstraintConfig.Group.Operator.ANY))).status)
        assertEquals(ExecutionStatus.CONSTRAINTS_NOT_MET, run(notify, constraints = listOf(group(ConstraintConfig.Group.Operator.NONE))).status)
    }

    // endregion
}
