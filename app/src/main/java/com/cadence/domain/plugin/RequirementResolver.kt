package com.cadence.domain.plugin

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.Macro
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.variable.SystemVariables
import com.cadence.domain.variable.Templates

/**
 * Works out every permission a config or macro needs: its plugin's own requirements plus those
 * of built-in variables it references (e.g. `{location.latitude}` needs location access).
 * Used by the editor, Home, and the Permissions center so they always agree.
 */
class RequirementResolver(
    private val triggerRegistry: TriggerRegistry,
    private val actionRegistry: ActionRegistry,
    private val constraintRegistry: ConstraintRegistry,
) {
    fun forMacro(macro: Macro): Set<PermissionRequirement> = buildSet {
        (macro.triggers + macro.actions + macro.constraints).forEach { addAll(forConfig(it)) }
    }

    fun forConfig(config: Any): Set<PermissionRequirement> = buildSet {
        nestedConfigs(config).forEach { addAll(forConfig(it)) }
        when (config) {
            is TriggerConfig -> triggerRegistry.handlerFor(config)?.requiredPermissions(config)?.let(::addAll)
            is ActionConfig -> actionRegistry.handlerFor(config)?.requiredPermissions(config)?.let(::addAll)
            is ConstraintConfig -> constraintRegistry.evaluatorFor(config)?.requiredPermissions(config)?.let(::addAll)
        }
        referencedVariables(config).forEach { addAll(SystemVariables.requirementsFor(it)) }
    }

    /** Variable names a config reads, from `{placeholders}` in its text fields and compared names. */
    fun referencedVariables(config: Any): Set<String> {
        val texts: List<String> = when (config) {
            is ActionConfig.ShowNotification -> listOf(config.title, config.text)
            is ActionConfig.SpeakText -> listOf(config.text)
            is ActionConfig.OpenUrl -> listOf(config.url)
            is ActionConfig.HttpRequest -> listOfNotNull(config.url, config.body) +
                config.headers.flatMap { listOf(it.name, it.value) } +
                config.queryParameters.flatMap { listOf(it.name, it.value) }
            is ActionConfig.SetVariable -> listOf(config.value)
            is ActionConfig.LogMessage -> listOf(config.text)
            is ActionConfig.ShowToast -> listOf(config.text)
            is ConstraintConfig.VariableCompare -> listOf(config.value)
            else -> emptyList()
        }
        val names = texts.flatMapTo(mutableSetOf()) { Templates.placeholders(it) }
        if (config is ConstraintConfig.VariableCompare && config.name.isNotEmpty()) names += config.name
        return names
    }

    /** Configs held inside control-flow actions and constraint groups. */
    private fun nestedConfigs(config: Any): List<Any> = when (config) {
        is ActionConfig.IfElse -> config.branches.flatMap { it.conditions + it.actions } + config.elseActions
        is ActionConfig.Repeat -> config.conditions + config.actions
        is ActionConfig.WaitForTrigger -> listOf(config.trigger)
        is ConstraintConfig.Group -> config.constraints
        else -> emptyList()
    }
}
