package com.cadence.domain.plugin

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.trigger.TriggerConfig
import kotlin.reflect.KClass

/**
 * Lookup of plugins by config type, built from a set of plugins (a Hilt `@IntoSet` multibinding
 * in the app). Keying by each plugin's own `configType` makes a key/handler mismatch impossible,
 * and duplicate registrations fail fast at startup.
 */
abstract class PluginRegistry<K : Any, P : Any> internal constructor(
    plugins: Set<P>,
    typeOf: (P) -> KClass<out K>,
) {
    private val byType: Map<KClass<out K>, P> = buildMap {
        for (plugin in plugins) {
            val type = typeOf(plugin)
            val existing = put(type, plugin)
            require(existing == null) {
                "Duplicate plugin for ${type.simpleName}: ${existing!!::class.simpleName} and ${plugin::class.simpleName}"
            }
        }
    }

    /** Config types that have a registered plugin. Used by the editor to list available items. */
    val registeredTypes: Set<KClass<out K>> get() = byType.keys

    /** Returns `true` when [type] has a registered plugin. */
    fun supports(type: KClass<out K>): Boolean = type in byType

    protected fun rawPluginFor(config: K): P? = byType[config::class]
}

/** Registry of [TriggerHandler]s. */
class TriggerRegistry(handlers: Set<TriggerHandler<*>>) :
    PluginRegistry<TriggerConfig, TriggerHandler<*>>(handlers, { it.configType }) {

    /** The handler for [config], or `null` when none is registered. */
    @Suppress("UNCHECKED_CAST")
    fun <C : TriggerConfig> handlerFor(config: C): TriggerHandler<C>? =
        rawPluginFor(config) as TriggerHandler<C>?
}

/** Registry of [ActionHandler]s. */
class ActionRegistry(handlers: Set<ActionHandler<*>>) :
    PluginRegistry<ActionConfig, ActionHandler<*>>(handlers, { it.configType }) {

    /** The handler for [config], or `null` when none is registered. */
    @Suppress("UNCHECKED_CAST")
    fun <C : ActionConfig> handlerFor(config: C): ActionHandler<C>? =
        rawPluginFor(config) as ActionHandler<C>?
}

/** Registry of [ConstraintEvaluator]s. */
class ConstraintRegistry(evaluators: Set<ConstraintEvaluator<*>>) :
    PluginRegistry<ConstraintConfig, ConstraintEvaluator<*>>(evaluators, { it.configType }) {

    /** The evaluator for [config], or `null` when none is registered. */
    @Suppress("UNCHECKED_CAST")
    fun <C : ConstraintConfig> evaluatorFor(config: C): ConstraintEvaluator<C>? =
        rawPluginFor(config) as ConstraintEvaluator<C>?
}
