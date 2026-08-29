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
}
