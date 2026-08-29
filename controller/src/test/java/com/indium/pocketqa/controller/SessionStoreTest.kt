package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreTest {
    @Test
    fun `a run exposes its selected goal live actions and findings`() {
        val store = SessionStore()
        store.start(TestGoal.CART_AND_CHECKOUT)
        store.record("tap", "Add Organic Bananas")
        store.recordFinding(BugFinding("Cart quantity goes below zero", "Observed -1"))

        val snapshot = store.snapshot()
        assertEquals(RunStatus.RUNNING, snapshot.status)
        assertEquals(TestGoal.CART_AND_CHECKOUT, snapshot.goal)
        assertEquals("Add Organic Bananas", snapshot.actions.single().detail)
        assertEquals("Cart quantity goes below zero", snapshot.findings.single().title)
    }

    @Test
    fun `stopping a run preserves its action trace`() {
        val store = SessionStore()
        store.start(TestGoal.CATALOG)
        store.record("wait", "Waiting for catalog")
        store.stop()

        val snapshot = store.snapshot()
        assertEquals(RunStatus.STOPPED, snapshot.status)
        assertTrue(snapshot.actions.any { it.detail == "Waiting for catalog" })
    }

    @Test
    fun `a run preserves its selected exploration mode`() {
        val store = SessionStore()
        store.start(TestGoal.FULL_SCAN, ExplorationMode.GEMMA_ASSISTED)

        assertEquals(ExplorationMode.GEMMA_ASSISTED, store.snapshot().explorationMode)
    }

    @Test
    fun `an autonomous run is represented without deterministic fallback`() {
        val store = SessionStore()
        store.start(TestGoal.FULL_SCAN, ExplorationMode.GEMMA_AUTONOMOUS)

        assertEquals(ExplorationMode.GEMMA_AUTONOMOUS, store.snapshot().explorationMode)
    }

    @Test
    fun `a finding retains the screenshot captured at its evidence point`() {
        val store = SessionStore()
        store.start(TestGoal.FULL_SCAN, ExplorationMode.GEMMA_ASSISTED)
        store.recordFinding(BugFinding("Quantity zero boundary failure", "Observed -1"))

        store.attachFindingScreenshot("Quantity zero boundary failure", "/cache/bug-05.png")

        assertEquals("/cache/bug-05.png", store.snapshot().findings.single().screenshotPath)
    }
}
