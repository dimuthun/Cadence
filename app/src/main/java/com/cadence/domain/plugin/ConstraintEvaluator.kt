package com.cadence.domain.plugin

import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.permission.PermissionRequirement
import kotlin.reflect.KClass

/**
 * Evaluates one constraint type. Implement this and register it with Hilt to add a constraint.
 *
 * @param C the config type this evaluator owns.
 */
interface ConstraintEvaluator<C : ConstraintConfig> {

    /** The config type this evaluator owns. Each type can have only one registered evaluator. */
    val configType: KClass<C>

    /** Permissions [config] needs. If any is missing the constraint is treated as not met. */
    fun requiredPermissions(config: C): Set<PermissionRequirement> = emptySet()

    /**
     * Returns `true` when [config] holds right now. Exceptions are treated as "not met" by the
     * engine, so a broken constraint never lets a macro run unexpectedly.
     */
    suspend fun evaluate(config: C, context: ExecutionContext): Boolean
}
