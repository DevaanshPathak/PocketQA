package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class TreeFormatterTest {
    @Test
    fun `renders Flutter semantics labels and actions as a readable tree`() {
        val tree = SemanticNode(
            className = "android.view.View",
            children = listOf(
                SemanticNode("android.widget.Button", "Add item", clickable = true),
                SemanticNode("android.widget.ScrollView", "Items", scrollable = true)
            )
        )

        assertEquals(
            "android.view.View\n  android.widget.Button label=\"Add item\" [click]\n  android.widget.ScrollView label=\"Items\" [scroll]",
            TreeFormatter.format(tree)
        )
    }

    @Test
    fun `plans safe scripted actions through the real grocery app`() {
        val catalog = SemanticNode("View", children = listOf(
            SemanticNode("View", "PocketQA Testbed (Buggy)"),
            SemanticNode("Button", "Shopping cart", clickable = true),
            SemanticNode("GridView", scrollable = true)
        ))
        val cart = SemanticNode("View", children = listOf(
            SemanticNode("View", "Your Cart"),
            SemanticNode("Button", "ORDER NOW", clickable = true)
        ))
        val checkout = SemanticNode("View", children = listOf(
            SemanticNode("View", "Checkout"),
            SemanticNode("android.widget.EditText", clickable = true),
            SemanticNode("ScrollView", scrollable = true)
        ))

        assertEquals(DemoAction.Click("Shopping cart"), DemoPlanner.next(catalog, DemoStep.OPEN_CART))
        assertEquals(true, DemoPlanner.isCatalog(catalog))
        assertEquals(DemoAction.Click("ORDER NOW"), DemoPlanner.next(cart, DemoStep.OPEN_CHECKOUT))
        assertEquals(DemoAction.ClickFirstEditable, DemoPlanner.next(checkout, DemoStep.FOCUS_NAME))
        assertEquals(DemoAction.Scroll, DemoPlanner.next(checkout, DemoStep.SCROLL_CHECKOUT))
    }

    @Test
    fun `falls back to a gesture when checkout exposes no semantic scroll action`() {
        val shortCheckout = SemanticNode("View", children = listOf(
            SemanticNode("View", "Checkout"),
            SemanticNode("android.widget.EditText", clickable = true)
        ))

        assertEquals(DemoAction.Scroll, DemoPlanner.next(shortCheckout, DemoStep.SCROLL_CHECKOUT))
    }
}
