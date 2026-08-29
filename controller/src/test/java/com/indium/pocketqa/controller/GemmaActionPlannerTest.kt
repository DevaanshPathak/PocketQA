package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GemmaActionPlannerTest {
    @Test
    fun `planner accepts a model-selected visible action label`() {
        val choice = GemmaActionPlanner.chooseLabel(
            response = "TAP: Add Organic Bananas",
            candidates = listOf("Add Organic Bananas", "Shopping cart"),
        )

        assertEquals("Add Organic Bananas", choice)
    }

    @Test
    fun `planner rejects an action that is not visible`() {
        val choice = GemmaActionPlanner.chooseLabel(
            response = "TAP: Delete account",
            candidates = listOf("Add Organic Bananas", "Shopping cart"),
        )

        assertNull(choice)
    }

    @Test
    fun `planner accepts a quoted model action`() {
        val choice = GemmaActionPlanner.chooseLabel(
            response = "I choose TAP: `Add Organic Bananas`",
            candidates = listOf("Add Organic Bananas", "Shopping cart"),
        )

        assertEquals("Add Organic Bananas", choice)
    }

    @Test
    fun `screen assessment retains a model-reported issue and action`() {
        val assessment = GemmaActionPlanner.assess(
            response = "ISSUE: Cart quantity can be negative | The displayed quantity is -1\nTAP: ORDER NOW",
            candidates = listOf("ORDER NOW", "Decrease quantity"),
        )

        assertEquals("ORDER NOW", assessment.actionLabel)
        assertEquals("Cart quantity can be negative", assessment.issueTitle)
        assertEquals("The displayed quantity is -1", assessment.issueEvidence)
    }

    @Test
    fun `planner charter requires boundary exploration before completion`() {
        val prompt = GemmaActionPlanner.prompt(
            screenSummary = "Cart quantity 0",
            candidates = listOf("Increase quantity", "Decrease quantity"),
        )

        assertEquals(true, prompt.contains("numeric boundaries"))
        assertEquals(true, prompt.contains("below its minimum"))
    }
}
