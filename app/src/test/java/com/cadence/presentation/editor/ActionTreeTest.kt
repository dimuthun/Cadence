package com.cadence.presentation.editor

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ConditionalBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionTreeTest {

    private val a = ActionConfig.Vibrate(100)
    private val b = ActionConfig.Vibrate(200)
    private val c = ActionConfig.Vibrate(300)

    private val tree = listOf(
        a,
        ActionConfig.IfElse(
            branches = listOf(ConditionalBlock(actions = listOf(b)), ConditionalBlock()),
            elseActions = listOf(ActionConfig.Repeat(actions = listOf(c))),
        ),
    )

    @Test
    fun `blockAt walks into branches and else`() {
        assertEquals(listOf<ActionConfig>(b), tree.blockAt(listOf(Slot(1, 0))))
        assertEquals(emptyList<ActionConfig>(), tree.blockAt(listOf(Slot(1, 1))))
        assertEquals(listOf<ActionConfig>(c), tree.blockAt(listOf(Slot(1, 2), Slot(0, 0))))
    }

    @Test
    fun `updateBlock changes only the addressed nested list`() {
        val path = listOf(Slot(1, 2), Slot(0, 0))

        val updated = tree.updateBlock(path) { it + a }

        assertEquals(listOf<ActionConfig>(c, a), updated.blockAt(path))
        assertEquals(listOf<ActionConfig>(b), updated.blockAt(listOf(Slot(1, 0))))
        assertEquals(a, updated[0])
    }

    @Test
    fun `rows show headers and add buttons for every block`() {
        val rows = tree.toRows(describeConditions = { _, _ -> "x" })

        val headers = rows.filterIsInstance<ActionRow.Header>().map { it.title }
        assertEquals(listOf("If x", "Else if x", "Else", "Repeat: x"), headers)
        val adds = rows.filterIsInstance<ActionRow.Add>().map { it.path }
        assertTrue(emptyList<Slot>() in adds)
        assertTrue(listOf(Slot(1, 2), Slot(0, 0)) in adds)
        val deepest = rows.filterIsInstance<ActionRow.Item>().single { it.action == c }
        assertEquals(2, deepest.depth)
    }
}
