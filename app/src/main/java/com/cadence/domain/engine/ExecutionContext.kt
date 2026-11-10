package com.cadence.domain.engine

import com.cadence.domain.model.Macro
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.domain.variable.VariableResolver
import java.time.Instant
import java.util.Collections

/**
 * Per-run state passed to action handlers and constraint evaluators.
 *
 * @property macro the macro being run, as it was when the run started.
 * @property trigger the event that started this run.
 * @property variables resolves variable names and `{placeholders}`; see [render].
 * @property locals variables local to this run (written by Set variable and HTTP request).
 * @property runCache values computed once per run, such as device readings, so every
 * placeholder in a run sees the same value.
 * @property callDepth number of "Run macro" calls that led to this run (0 for a direct run).
 */
data class ExecutionContext(
    val macro: Macro,
    val trigger: TriggerEvent,
    val startedAt: Instant,
    val variables: VariableResolver = VariableResolver.Empty,
    val locals: MutableMap<String, String> = Collections.synchronizedMap(LinkedHashMap()),
    val runCache: MutableMap<String, Any?> = Collections.synchronizedMap(HashMap()),
    val callDepth: Int = 0,
)

/** Fills `{name}` placeholders in [template] with the current values. */
suspend fun ExecutionContext.render(template: String): String = variables.render(template, this)
