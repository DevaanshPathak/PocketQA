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
}
