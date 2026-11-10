package com.cadence.domain.plugin

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.action.ToggleState
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.engine.render
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.repository.ExecutionLogRepository
import com.cadence.domain.repository.MacroRepository
import java.time.Clock
import java.time.Duration
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.first

// Plugins that act on macros themselves. Pure domain code: no Android dependencies.

/**
 * [ActionConfig.SetMacroEnabled].
 *
 * @param onEnabled called after a macro is enabled, so the engine host can start if it was idle.
 */
class SetMacroEnabledActionHandler(
    private val macros: MacroRepository,
    private val onEnabled: suspend () -> Unit,
) : ActionHandler<ActionConfig.SetMacroEnabled> {

    override val configType: KClass<ActionConfig.SetMacroEnabled> = ActionConfig.SetMacroEnabled::class

    override suspend fun execute(config: ActionConfig.SetMacroEnabled, context: ExecutionContext): ActionResult {
        val id = config.macroId ?: context.macro.id
        val macro = macros.getMacro(id) ?: return ActionResult.Failure("The macro no longer exists")
        val enable = when (config.state) {
            ToggleState.ON -> true
            ToggleState.OFF -> false
            ToggleState.TOGGLE -> !macro.enabled
        }
        if (macro.enabled != enable) macros.setEnabled(id, enable)
        if (enable) onEnabled()
        return ActionResult.Success("\"${macro.name}\" ${if (enable) "enabled" else "disabled"}")
    }
}

/** [ActionConfig.LogMessage]: the rendered text becomes this action's message in the log. */
class LogMessageActionHandler : ActionHandler<ActionConfig.LogMessage> {

    override val configType: KClass<ActionConfig.LogMessage> = ActionConfig.LogMessage::class

    override suspend fun execute(config: ActionConfig.LogMessage, context: ExecutionContext): ActionResult =
        ActionResult.Success(context.render(config.text).take(MAX_LENGTH))

    private companion object {
        const val MAX_LENGTH = 2_000
    }
}

/** [ConstraintConfig.MacroEnabled]. A deleted macro counts as disabled. */
class MacroEnabledEvaluator(
    private val macros: MacroRepository,
) : ConstraintEvaluator<ConstraintConfig.MacroEnabled> {

    override val configType: KClass<ConstraintConfig.MacroEnabled> = ConstraintConfig.MacroEnabled::class

    override suspend fun evaluate(config: ConstraintConfig.MacroEnabled, context: ExecutionContext): Boolean =
        (macros.getMacro(config.macroId)?.enabled ?: false) == config.enabled
}

/**
 * [ConstraintConfig.TimeSinceLastRun]: a cooldown. Runs skipped by constraints don't count, and a
 * macro with no runs in the log holds. Clearing the log resets the cooldown.
 */
class TimeSinceLastRunEvaluator(
    private val log: ExecutionLogRepository,
    private val clock: Clock,
) : ConstraintEvaluator<ConstraintConfig.TimeSinceLastRun> {

    override val configType: KClass<ConstraintConfig.TimeSinceLastRun> = ConstraintConfig.TimeSinceLastRun::class

    override suspend fun evaluate(config: ConstraintConfig.TimeSinceLastRun, context: ExecutionContext): Boolean {
        val id = config.macroId ?: context.macro.id
        val lastRun = log.observeExecutionsForMacro(id, limit = RECENT_RUNS).first()
            .firstOrNull { it.status != ExecutionStatus.CONSTRAINTS_NOT_MET }
            ?: return true
        return Duration.between(lastRun.startedAt, clock.instant()) >= Duration.ofMinutes(config.minutes.toLong())
    }

    private companion object {
        const val RECENT_RUNS = 50
    }
}
