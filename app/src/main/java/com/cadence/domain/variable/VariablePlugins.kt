package com.cadence.domain.variable

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.engine.render
import com.cadence.domain.plugin.ActionHandler
import com.cadence.domain.plugin.ConstraintEvaluator
import java.math.BigDecimal
import kotlin.reflect.KClass

/** [ActionConfig.SetVariable]: pure domain logic, no Android dependencies. */
class SetVariableActionHandler(
    private val globals: GlobalVariableRepository,
) : ActionHandler<ActionConfig.SetVariable> {

    override val configType: KClass<ActionConfig.SetVariable> = ActionConfig.SetVariable::class

    override suspend fun execute(config: ActionConfig.SetVariable, context: ExecutionContext): ActionResult {
        if (!VariableNames.isValidUserName(config.name)) return ActionResult.Failure("No variable name set")
        val input = context.render(config.value)
        val current = when (config.scope) {
            VariableScope.LOCAL -> context.locals[config.name]
            VariableScope.GLOBAL -> globals.get(config.name)
        }
        val newValue = when (config.operation) {
            ActionConfig.SetVariable.Operation.SET -> input
            ActionConfig.SetVariable.Operation.APPEND -> current.orEmpty() + input
            ActionConfig.SetVariable.Operation.ADD -> {
                val amount = input.trim().toBigDecimalOrNull()
                    ?: return ActionResult.Failure("\"$input\" isn't a number")
                val base = current?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                (base + amount).toPlainNumber()
            }
        }
        when (config.scope) {
            VariableScope.LOCAL -> context.locals[config.name] = newValue
            VariableScope.GLOBAL -> globals.set(config.name, newValue)
        }
        return ActionResult.Success("${config.name} = ${newValue.take(SUMMARY_LENGTH)}")
    }

    private companion object {
        const val SUMMARY_LENGTH = 100
    }
}

/** [ConstraintConfig.VariableCompare]. */
class VariableCompareEvaluator : ConstraintEvaluator<ConstraintConfig.VariableCompare> {

    override val configType: KClass<ConstraintConfig.VariableCompare> = ConstraintConfig.VariableCompare::class

    override suspend fun evaluate(config: ConstraintConfig.VariableCompare, context: ExecutionContext): Boolean {
        if (config.name.isEmpty()) return false
        val actual = context.variables.lookup(config.name, context)
        val expected = context.render(config.value)
        return when (config.operator) {
            ConstraintConfig.VariableCompare.Operator.IS_SET -> !actual.isNullOrEmpty()
            ConstraintConfig.VariableCompare.Operator.IS_NOT_SET -> actual.isNullOrEmpty()
            ConstraintConfig.VariableCompare.Operator.EQUALS -> actual != null && valuesEqual(actual, expected)
            ConstraintConfig.VariableCompare.Operator.NOT_EQUALS -> actual == null || !valuesEqual(actual, expected)
            ConstraintConfig.VariableCompare.Operator.CONTAINS -> actual != null && actual.contains(expected, ignoreCase = true)
            ConstraintConfig.VariableCompare.Operator.GREATER_THAN -> compareNumbers(actual, expected)?.let { it > 0 } ?: false
            ConstraintConfig.VariableCompare.Operator.LESS_THAN -> compareNumbers(actual, expected)?.let { it < 0 } ?: false
        }
    }

    private fun valuesEqual(actual: String, expected: String): Boolean =
        compareNumbers(actual, expected)?.let { it == 0 } ?: actual.equals(expected, ignoreCase = true)

    /** `null` unless both sides are numbers. */
    private fun compareNumbers(actual: String?, expected: String): Int? {
        val a = actual?.trim()?.toBigDecimalOrNull() ?: return null
        val b = expected.trim().toBigDecimalOrNull() ?: return null
        return a.compareTo(b)
    }
}

/** "3", "2.5", "-1" rather than "3.0" or "2.50". */
private fun BigDecimal.toPlainNumber(): String = stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }
