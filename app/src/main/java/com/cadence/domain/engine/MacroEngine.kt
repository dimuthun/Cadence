package com.cadence.domain.engine

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.action.ConditionMatch
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.ActionOutcome
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.model.Macro
import com.cadence.domain.model.MacroExecution
import com.cadence.domain.model.OutcomeStatus
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.permission.PermissionStatusProvider
import com.cadence.domain.plugin.ActionRegistry
import com.cadence.domain.plugin.ConstraintRegistry
import com.cadence.domain.plugin.TriggerRegistry
import com.cadence.domain.repository.ExecutionLogRepository
import com.cadence.domain.repository.MacroRepository
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.domain.variable.VariableResolver
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * Runs macros: keeps triggers registered for every enabled macro, and when one fires,
 * evaluates constraints and then runs the actions in order.
 *
 * **Lifecycle.** [start] begins watching [MacroRepository.observeEnabledMacros]. Each enabled
 * macro gets its own coroutine that collects its trigger flows. When a macro is disabled,
 * edited, or deleted, only that macro's coroutine is cancelled, which unregisters its triggers
 * (see [com.cadence.domain.plugin.TriggerHandler]). [stop] cancels everything.
 *
 * **Failure isolation.** A throwing action becomes a failed [ActionOutcome] and the next action
 * still runs. A failing trigger flow ends only that trigger. A failing constraint counts as
 * "not met". Nothing a plugin does can crash the engine.
 *
 * **Concurrency.** If a trigger fires while the same macro is still running (e.g. during a
 * Wait), the new event is skipped. Different macros run in parallel.
 *
 * **Extensibility.** The engine only knows the plugin interfaces and registries. New device
 * triggers, actions, and constraints never need engine changes. Only control-flow actions
 * ([ActionConfig.ControlFlowAction]) are interpreted here. The recursive [runBlock] is the
 * hook for Phase 2's if/else and loop blocks.
 *
 * @param actionTimeout maximum time a single handler action may take before it is marked failed.
 */
class MacroEngine(
    private val macroRepository: MacroRepository,
    private val executionLogRepository: ExecutionLogRepository,
    private val triggerRegistry: TriggerRegistry,
    private val actionRegistry: ActionRegistry,
    private val constraintRegistry: ConstraintRegistry,
    private val permissionStatus: PermissionStatusProvider,
    private val clock: Clock,
    private val diagnostics: EngineDiagnostics = EngineDiagnostics.None,
    private val actionTimeout: Duration = DEFAULT_ACTION_TIMEOUT,
    private val variables: VariableResolver = VariableResolver.Empty,
) {
    private val lock = Any()
    private var engineJob: Job? = null
    private val refreshGeneration = MutableStateFlow(0)
    private val _activeMacroIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Recently handled `macroId:eventId` pairs, so replayed events run a macro only once. */
    private val handledEventKeys = object : LinkedHashMap<String, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > HANDLED_EVENT_CAPACITY
    }

    /** Ids of macros whose triggers are currently registered. */
    val activeMacroIds: StateFlow<Set<Long>> = _activeMacroIds.asStateFlow()

    private val runningCounts = HashMap<Long, Int>()
    private val _runningMacroIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Ids of macros with a run in progress (triggered, manual, or called by another macro). */
    val runningMacroIds: StateFlow<Set<Long>> = _runningMacroIds.asStateFlow()

    /** `true` between [start] and [stop]. */
    val isRunning: Boolean
        get() = synchronized(lock) { engineJob?.isActive == true }

    /**
     * Starts the engine in [scope], typically the foreground service's scope. Calling it again
     * while running does nothing.
     */
    fun start(scope: CoroutineScope) {
        synchronized(lock) {
            if (engineJob?.isActive == true) return
            engineJob = scope.launch(crashGuard(macroId = null)) { superviseMacros() }
        }
    }

    /** Stops the engine, cancelling in-flight runs and unregistering every trigger. */
    fun stop() {
        synchronized(lock) {
            engineJob?.cancel()
            engineJob = null
        }
    }

    /**
     * Re-registers every macro's triggers. Call this when permissions may have changed (e.g.
     * after the user returns from a system settings screen), since triggers skipped for missing
     * permissions are only retried on re-registration.
     */
    fun refresh() {
        refreshGeneration.update { it + 1 }
    }

    /**
     * Runs [macro] once for [event] and records the result in the log. Used for triggered runs
     * and for manual runs, for which [event] uses [TriggerConfig.Manual].
     *
     * @param checkConstraints set to `false` to skip constraints, e.g. for an editor "test run".
     * @param callDepth how many "Run macro" calls led here; limits macros calling each other.
     */
    suspend fun execute(
        macro: Macro,
        event: TriggerEvent,
        checkConstraints: Boolean = true,
        callDepth: Int = 0,
    ): MacroExecution {
        markRunning(macro.id, +1)
        try {
            return executeTracked(macro, event, checkConstraints, callDepth)
        } finally {
            markRunning(macro.id, -1)
        }
    }

    private suspend fun executeTracked(
        macro: Macro,
        event: TriggerEvent,
        checkConstraints: Boolean,
        callDepth: Int,
    ): MacroExecution {
        val startedAt = clock.instant()
        val context = ExecutionContext(
            macro = macro,
            trigger = event,
            startedAt = startedAt,
            variables = variables,
            callDepth = callDepth,
        )

        fun result(
            status: ExecutionStatus,
            outcomes: List<ActionOutcome>,
            failedConstraint: ConstraintConfig? = null,
        ) = MacroExecution(
            macroId = macro.id,
            macroName = macro.name,
            trigger = event.source,
            triggerData = event.data,
            startedAt = startedAt,
            finishedAt = clock.instant(),
            status = status,
            failedConstraint = failedConstraint,
            actionOutcomes = outcomes,
        )

        if (checkConstraints) {
            val unmet = firstUnmetConstraint(macro, context)
            if (unmet != null) {
                return record(result(ExecutionStatus.CONSTRAINTS_NOT_MET, emptyList(), unmet))
            }
        }

        val outcomes = mutableListOf<ActionOutcome>()
        try {
            runBlock(macro.actions, context, outcomes)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { record(result(ExecutionStatus.CANCELLED, outcomes.toList())) }
            throw e
        }
        return record(result(statusFor(outcomes), outcomes.toList()))
    }

    // region Trigger supervision

    private suspend fun superviseMacros(): Unit = supervisorScope {
        val macroScope: CoroutineScope = this
        val active = HashMap<Long, ActiveMacro>()
        var lastGeneration: Int? = null
        try {
            combine(macroRepository.observeEnabledMacros(), refreshGeneration) { macros, generation ->
                macros to generation
            }.collect { (macros, generation) ->
                val restartAll = lastGeneration != null && generation != lastGeneration
                lastGeneration = generation
                val desired = macros.filter { it.isRunnable }.associateBy { it.id }

                val iterator = active.entries.iterator()
                while (iterator.hasNext()) {
                    val (id, running) = iterator.next()
                    if (restartAll || desired[id] != running.macro) {
                        running.job.cancel()
                        iterator.remove()
                    }
                }
                for (macro in desired.values) {
                    if (macro.id !in active) {
                        active[macro.id] = ActiveMacro(
                            macro = macro,
                            job = macroScope.launch(crashGuard(macro.id)) { activate(macro) },
                        )
                    }
                }
                _activeMacroIds.value = active.keys.toSet()
            }
        } finally {
            active.values.forEach { it.job.cancel() }
            _activeMacroIds.value = emptySet()
        }
    }

    /** Collects all triggers of [macro] for as long as the calling coroutine is active. */
    private suspend fun activate(macro: Macro): Unit = supervisorScope {
        val runScope: CoroutineScope = this
        val runInFlight = AtomicBoolean(false)
        macro.triggers
            .map { observeTrigger(macro, it) }
            .merge()
            .collect { event ->
                if (!markFirstDelivery(macro.id, event)) return@collect
                if (!runInFlight.compareAndSet(false, true)) return@collect
                runScope.launch(crashGuard(macro.id)) {
                    try {
                        execute(macro, event)
                    } finally {
                        runInFlight.set(false)
                    }
                }
            }
    }

    /** Returns `false` if [event] has an id this macro already handled. */
    private fun markFirstDelivery(macroId: Long, event: TriggerEvent): Boolean {
        val id = event.id ?: return true
        return synchronized(handledEventKeys) { handledEventKeys.put("$macroId:$id", Unit) == null }
    }

    private fun observeTrigger(macro: Macro, trigger: TriggerConfig): Flow<TriggerEvent> {
        if (trigger is TriggerConfig.Manual) return emptyFlow()

        val handler = triggerRegistry.handlerFor(trigger)
        if (handler == null) {
            diagnostics.onError(macro.id, "No handler registered for trigger $trigger")
            return emptyFlow()
        }
        val missing = missingPermissions(handler.requiredPermissions(trigger))
        if (missing.isNotEmpty()) {
            diagnostics.onError(macro.id, "Trigger $trigger not registered, missing permissions: $missing")
            return emptyFlow()
        }
        return flow { emitAll(handler.observe(trigger)) }
            .catch { e -> diagnostics.onError(macro.id, "Trigger $trigger failed and was unregistered", e) }
    }

    // endregion

    // region Constraints

    private suspend fun firstUnmetConstraint(macro: Macro, context: ExecutionContext): ConstraintConfig? =
        macro.constraints.firstOrNull { !isMet(it, context) }

    /** All of [conditions] hold ([ConditionMatch.ALL]) or at least one does ([ConditionMatch.ANY]); empty holds. */
    private suspend fun conditionsHold(
        conditions: List<ConstraintConfig>,
        match: ConditionMatch,
        context: ExecutionContext,
    ): Boolean = when {
        conditions.isEmpty() -> true
        match == ConditionMatch.ALL -> conditions.all { isMet(it, context) }
        else -> conditions.any { isMet(it, context) }
    }

    private suspend fun isMet(constraint: ConstraintConfig, context: ExecutionContext): Boolean {
        when (constraint) {
            is ConstraintConfig.Group -> return when (constraint.operator) {
                ConstraintConfig.Group.Operator.ALL -> constraint.constraints.all { isMet(it, context) }
                ConstraintConfig.Group.Operator.ANY -> constraint.constraints.any { isMet(it, context) }
                ConstraintConfig.Group.Operator.NONE -> constraint.constraints.none { isMet(it, context) }
            }
            is ConstraintConfig.MacroRunning ->
                return (constraint.macroId in _runningMacroIds.value) == constraint.running
            else -> Unit
        }
        val evaluator = constraintRegistry.evaluatorFor(constraint)
        if (evaluator == null) {
            diagnostics.onError(context.macro.id, "No evaluator registered for constraint $constraint")
            return false
        }
        if (missingPermissions(evaluator.requiredPermissions(constraint)).isNotEmpty()) return false
        return try {
            evaluator.evaluate(constraint, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnostics.onError(context.macro.id, "Constraint $constraint threw; treating as not met", e)
            false
        } catch (e: LinkageError) {
            // A plugin that fails to load (e.g. ExceptionInInitializerError) must not crash the app.
            diagnostics.onError(context.macro.id, "Constraint $constraint failed to load; treating as not met", e)
            false
        }
    }

    // endregion

    // region Actions

    /**
     * How a block finished. Loops consume [ActionConfig.ExitLoop] / [ActionConfig.NextIteration];
     * outside any loop they simply end the run, like [ActionConfig.StopRun].
     */
    private enum class Signal { NEXT, EXIT_LOOP, NEXT_ITERATION, STOP }

    /**
     * Runs [block] in order, recursing into nested blocks. Every action, including control flow,
     * appends one outcome; nested actions follow their parent's outcome in the log.
     */
    private suspend fun runBlock(
        block: List<ActionConfig>,
        context: ExecutionContext,
        outcomes: MutableList<ActionOutcome>,
    ): Signal {
        for (action in block) {
            currentCoroutineContext().ensureActive()
            val signal = if (action is ActionConfig.ControlFlowAction) {
                runControlFlow(action, context, outcomes)
            } else {
                val mark = TimeSource.Monotonic.markNow()
                outcomes += outcomeOf(action, runHandler(action, context), context, outcomes.size, mark)
                Signal.NEXT
            }
            if (signal != Signal.NEXT) return signal
        }
        return Signal.NEXT
    }

    private fun outcomeOf(
        action: ActionConfig,
        result: ActionResult,
        context: ExecutionContext,
        index: Int,
        mark: TimeSource.Monotonic.ValueTimeMark,
    ): ActionOutcome {
        if (result is ActionResult.Failure) {
            diagnostics.onError(context.macro.id, "Action $action failed: ${result.reason}", result.cause)
        }
        val (status, message) = when (result) {
            is ActionResult.Success -> OutcomeStatus.SUCCESS to result.message
            is ActionResult.Fallback -> OutcomeStatus.FALLBACK to result.message
            is ActionResult.Failure -> OutcomeStatus.FAILURE to result.reason
        }
        return ActionOutcome(
            index = index,
            action = action,
            status = status,
            message = message,
            durationMillis = mark.elapsedNow().inWholeMilliseconds,
        )
    }

    private suspend fun runControlFlow(
        action: ActionConfig.ControlFlowAction,
        context: ExecutionContext,
        outcomes: MutableList<ActionOutcome>,
    ): Signal {
        val mark = TimeSource.Monotonic.markNow()
        fun log(result: ActionResult) {
            outcomes += outcomeOf(action, result, context, outcomes.size, mark)
        }
        return when (action) {
            is ActionConfig.Wait -> {
                delay(action.durationMillis.milliseconds)
                log(ActionResult.Success())
                Signal.NEXT
            }
            is ActionConfig.IfElse -> {
                val branch = action.branches.indexOfFirst { conditionsHold(it.conditions, it.match, context) }
                val block = if (branch >= 0) action.branches[branch].actions else action.elseActions
                log(
                    ActionResult.Success(
                        when {
                            branch == 0 -> "Condition met"
                            branch > 0 -> "Else-if ${branch} met"
                            action.elseActions.isNotEmpty() -> "No condition met; running else"
                            else -> "No condition met"
                        },
                    ),
                )
                runBlock(block, context, outcomes)
            }
            is ActionConfig.Repeat -> runRepeat(action, context, outcomes, mark)
            ActionConfig.ExitLoop -> {
                log(ActionResult.Success("Leaving loop"))
                Signal.EXIT_LOOP
            }
            ActionConfig.NextIteration -> {
                log(ActionResult.Success("Skipping to next iteration"))
                Signal.NEXT_ITERATION
            }
            ActionConfig.StopRun -> {
                log(ActionResult.Success("Stopped"))
                Signal.STOP
            }
            is ActionConfig.WaitForTrigger -> {
                log(waitForTrigger(action, context))
                Signal.NEXT
            }
            is ActionConfig.RunMacro -> {
                log(runOtherMacro(action, context))
                Signal.NEXT
            }
        }
    }

    private suspend fun runRepeat(
        action: ActionConfig.Repeat,
        context: ExecutionContext,
        outcomes: MutableList<ActionOutcome>,
        mark: TimeSource.Monotonic.ValueTimeMark,
    ): Signal {
        // Reserve this action's log slot so it precedes the body; the final count is filled in after.
        val slot = outcomes.size
        outcomes += outcomeOf(action, ActionResult.Success(), context, slot, mark)
        val limit = if (action.mode == ActionConfig.Repeat.Mode.COUNT) action.count else action.maxIterations
        val outerIndex = context.locals[LOOP_INDEX]
        var iterations = 0
        var signal = Signal.NEXT
        try {
            while (iterations < limit) {
                if (action.mode == ActionConfig.Repeat.Mode.WHILE &&
                    !conditionsHold(action.conditions, action.match, context)
                ) {
                    break
                }
                context.locals[LOOP_INDEX] = (iterations + 1).toString()
                val bodySignal = runBlock(action.actions, context, outcomes)
                iterations++
                if (bodySignal == Signal.EXIT_LOOP) break
                if (bodySignal == Signal.STOP) {
                    signal = Signal.STOP
                    break
                }
                yield()
            }
        } finally {
            if (outerIndex != null) context.locals[LOOP_INDEX] = outerIndex else context.locals.remove(LOOP_INDEX)
        }
        val hitLimit = iterations >= limit && action.mode != ActionConfig.Repeat.Mode.COUNT
        outcomes[slot] = outcomes[slot].copy(
            message = "Repeated $iterations time${if (iterations == 1) "" else "s"}" +
                if (hitLimit) " (stopped at the safety limit)" else "",
            durationMillis = mark.elapsedNow().inWholeMilliseconds,
        )
        return signal
    }

    private suspend fun waitForTrigger(action: ActionConfig.WaitForTrigger, context: ExecutionContext): ActionResult {
        val trigger = action.trigger
        val handler = triggerRegistry.handlerFor(trigger)
            ?: return ActionResult.Failure("No handler registered for this trigger")
        val missing = missingPermissions(handler.requiredPermissions(trigger))
        if (missing.isNotEmpty()) {
            return ActionResult.Failure("Missing permission: ${missing.joinToString { it.name }}")
        }
        val since = clock.instant()
        val event = try {
            withTimeoutOrNull(action.timeoutSeconds.seconds) {
                // Ignore replayed events from before the wait began.
                flow { emitAll(handler.observe(trigger)) }.first { !it.occurredAt.isBefore(since) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ActionResult.Failure("Couldn't listen for the trigger: ${e.message}", e)
        }
        context.locals["wait.timed_out"] = (event == null).toString()
        if (event == null) return ActionResult.Success("Gave up after ${action.timeoutSeconds} s")
        event.data.forEach { (key, value) -> context.locals["wait.$key"] = value }
        return ActionResult.Success("Trigger fired")
    }

    private suspend fun runOtherMacro(action: ActionConfig.RunMacro, context: ExecutionContext): ActionResult {
        if (context.callDepth >= MAX_CALL_DEPTH) {
            return ActionResult.Failure("Too many nested macro calls (limit $MAX_CALL_DEPTH)")
        }
        val target = macroRepository.getMacro(action.macroId)
            ?: return ActionResult.Failure("The macro to run no longer exists")
        val event = TriggerEvent(TriggerConfig.Manual, clock.instant(), mapOf("caller" to context.macro.name))
        val result = execute(target, event, action.checkConstraints, context.callDepth + 1)
        val summary = "\"${target.name}\": ${result.status.name.lowercase().replace('_', ' ')}"
        return when (result.status) {
            ExecutionStatus.FAILURE, ExecutionStatus.PARTIAL_FAILURE -> ActionResult.Failure(summary)
            else -> ActionResult.Success(summary)
        }
    }

    private suspend fun runHandler(action: ActionConfig, context: ExecutionContext): ActionResult {
        val handler = actionRegistry.handlerFor(action)
            ?: return ActionResult.Failure("No handler registered for this action")
        val missing = missingPermissions(handler.requiredPermissions(action))
        if (missing.isNotEmpty()) {
            return ActionResult.Failure("Missing permission: ${missing.joinToString { it.name }}")
        }
        val timeout = handler.timeout(action)?.coerceAtLeast(actionTimeout) ?: actionTimeout
        return try {
            withTimeout(timeout) { handler.execute(action, context) }
        } catch (e: TimeoutCancellationException) {
            ActionResult.Failure("Timed out after $timeout", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: e::class.simpleName ?: "Unknown error", e)
        } catch (e: LinkageError) {
            // A plugin that fails to load (e.g. ExceptionInInitializerError) must not crash the app.
            ActionResult.Failure("Internal error: ${e.cause?.message ?: e::class.simpleName}", e)
        }
    }

    // endregion

    private fun markRunning(macroId: Long, delta: Int) {
        synchronized(runningCounts) {
            val count = (runningCounts[macroId] ?: 0) + delta
            if (count > 0) runningCounts[macroId] = count else runningCounts.remove(macroId)
            _runningMacroIds.value = runningCounts.keys.toSet()
        }
    }

    private fun missingPermissions(required: Set<PermissionRequirement>): Set<PermissionRequirement> =
        required.filterNotTo(mutableSetOf()) { permissionStatus.isGranted(it) }

    private suspend fun record(execution: MacroExecution): MacroExecution =
        try {
            execution.copy(id = executionLogRepository.record(execution))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnostics.onError(execution.macroId, "Failed to write execution log", e)
            execution
        }

    private fun statusFor(outcomes: List<ActionOutcome>): ExecutionStatus {
        val failures = outcomes.count { it.status == OutcomeStatus.FAILURE }
        return when {
            failures == 0 -> ExecutionStatus.SUCCESS
            failures == outcomes.size -> ExecutionStatus.FAILURE
            else -> ExecutionStatus.PARTIAL_FAILURE
        }
    }

    private fun crashGuard(macroId: Long?) = CoroutineExceptionHandler { _, throwable ->
        diagnostics.onError(macroId, "Unexpected engine error", throwable)
    }

    private class ActiveMacro(val macro: Macro, val job: Job)

    companion object {
        /** Default upper bound for a single handler action. */
        val DEFAULT_ACTION_TIMEOUT: Duration = 30.seconds

        private const val HANDLED_EVENT_CAPACITY = 512

        /** How deep "Run macro" calls may nest. */
        const val MAX_CALL_DEPTH = 5

        private const val LOOP_INDEX = "loop.index"
    }
}
