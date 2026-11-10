package com.cadence.domain.plugin

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.permission.PermissionRequirement
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Executes one device action type. Implement this and register it with Hilt to add an action.
 *
 * Control-flow actions ([ActionConfig.ControlFlowAction]) are run by the engine and have no handler.
 *
 * @param C the config type this handler owns.
 */
interface ActionHandler<C : ActionConfig> {

    /** The config type this handler owns. Each type can have only one registered handler. */
    val configType: KClass<C>

    /** Permissions [config] needs. If any is missing the action fails without being executed. */
    fun requiredPermissions(config: C): Set<PermissionRequirement> = emptySet()

    /**
     * How long [config] may run, if it needs more than the engine's default action timeout
     * (e.g. an HTTP request with a long timeout). `null` uses the default.
     */
    fun timeout(config: C): Duration? = null

    /**
     * Performs the action. Return [ActionResult.Fallback] when Android forces an alternative
     * (such as opening a settings panel) and [ActionResult.Failure] for expected errors.
     * Unexpected exceptions are caught by the engine and recorded as failures. Execution is
     * cancelled after the engine's action timeout.
     */
    suspend fun execute(config: C, context: ExecutionContext): ActionResult
}
