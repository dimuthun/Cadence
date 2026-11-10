package com.cadence.presentation.editor

import com.cadence.domain.action.ActionConfig

/**
 * One step into a nested block: the control-flow action at [index] in the current list, and which
 * of its child blocks ([slot]: an if's branch number, the if's else = branch count, a repeat's body = 0).
 */
data class Slot(val index: Int, val slot: Int)

/** Address of an action list inside the macro: empty for the top-level actions. */
typealias BlockPath = List<Slot>

/** The nested action lists of a control-flow action, in slot order. */
fun ActionConfig.childBlocks(): List<List<ActionConfig>> = when (this) {
    is ActionConfig.IfElse -> branches.map { it.actions } + listOf(elseActions)
    is ActionConfig.Repeat -> listOf(actions)
    else -> emptyList()
}

/** A copy of this action with child block [slot] replaced. */
fun ActionConfig.withChildBlock(slot: Int, block: List<ActionConfig>): ActionConfig = when (this) {
    is ActionConfig.IfElse ->
        if (slot < branches.size) {
            copy(branches = branches.mapIndexed { i, branch -> if (i == slot) branch.copy(actions = block) else branch })
        } else {
            copy(elseActions = block)
        }
    is ActionConfig.Repeat -> copy(actions = block)
    else -> this
}

/** The action list at [path]. */
fun List<ActionConfig>.blockAt(path: BlockPath): List<ActionConfig> =
    path.fold(this) { block, step -> block[step.index].childBlocks()[step.slot] }

/** Replaces the action list at [path] with [transform] of it, rebuilding every parent on the way up. */
fun List<ActionConfig>.updateBlock(path: BlockPath, transform: (List<ActionConfig>) -> List<ActionConfig>): List<ActionConfig> {
    if (path.isEmpty()) return transform(this)
    val step = path.first()
    val owner = this[step.index]
    val child = owner.childBlocks()[step.slot].updateBlock(path.drop(1), transform)
    return toMutableList().also { it[step.index] = owner.withChildBlock(step.slot, child) }
}

/** Rows of the action tree as the editor shows it. */
sealed interface ActionRow {
    val depth: Int

    /** An action at [index] in the block at [path]. */
    data class Item(
        val path: BlockPath,
        val index: Int,
        val action: ActionConfig,
        val isFirst: Boolean,
        val isLast: Boolean,
        override val depth: Int,
    ) : ActionRow

    /** Heading of a nested block, e.g. "Else if …" or "Else". */
    data class Header(val title: String, override val depth: Int) : ActionRow

    /** "Add action" button for the block at [path]. */
    data class Add(val path: BlockPath, override val depth: Int) : ActionRow
}

/**
 * Flattens the action tree into rows. [describeConditions] renders a branch's or loop's
 * conditions for its header.
 */
fun List<ActionConfig>.toRows(
    describeConditions: (ActionConfig, Int) -> String,
    path: BlockPath = emptyList(),
    depth: Int = 0,
): List<ActionRow> = buildList {
    this@toRows.forEachIndexed { index, action ->
        add(ActionRow.Item(path, index, action, index == 0, index == this@toRows.lastIndex, depth))
        val children = action.childBlocks()
        children.forEachIndexed { slot, block ->
            val title = when (action) {
                is ActionConfig.IfElse -> when {
                    slot == 0 -> "If ${describeConditions(action, slot)}"
                    slot < action.branches.size -> "Else if ${describeConditions(action, slot)}"
                    else -> "Else"
                }
                is ActionConfig.Repeat -> "Repeat: ${describeConditions(action, slot)}"
                else -> ""
            }
            add(ActionRow.Header(title, depth + 1))
            val childPath = path + Slot(index, slot)
            addAll(block.toRows(describeConditions, childPath, depth + 1))
            add(ActionRow.Add(childPath, depth + 1))
        }
    }
    if (depth == 0) add(ActionRow.Add(path, depth))
}
