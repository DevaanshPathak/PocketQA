package com.indium.pocketqa.controller

data class SemanticNode(
    val className: String,
    val label: String? = null,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val children: List<SemanticNode> = emptyList()
)

object TreeFormatter {
    fun format(root: SemanticNode): String = buildString { appendNode(root, 0) }.trimEnd()

    private fun StringBuilder.appendNode(node: SemanticNode, depth: Int) {
        append("  ".repeat(depth)).append(node.className)
        node.label?.takeIf { it.isNotBlank() }?.let { append(" label=\"").append(it).append('"') }
        if (node.clickable) append(" [click]")
        if (node.scrollable) append(" [scroll]")
        appendLine()
        node.children.forEach { appendNode(it, depth + 1) }
    }
}

enum class DemoStep { OPEN_CART, OPEN_CHECKOUT, FOCUS_NAME, SCROLL_CHECKOUT, COMPLETE }

sealed interface DemoAction {
    data class Click(val label: String) : DemoAction
    data object ClickFirstEditable : DemoAction
    data object Scroll : DemoAction
}

object DemoPlanner {
    fun isCatalog(root: SemanticNode): Boolean = root.hasLabel("PocketQA Testbed (Buggy)")

    fun next(root: SemanticNode, step: DemoStep): DemoAction? = when (step) {
        DemoStep.OPEN_CART -> if (isCatalog(root)) clickIfPresent(root, "Shopping cart") else null
        DemoStep.OPEN_CHECKOUT -> if (root.hasLabel("Your Cart")) clickIfPresent(root, "ORDER NOW") else null
        DemoStep.FOCUS_NAME -> if (root.hasLabel("Checkout") && root.any { it.clickable && it.className.endsWith("EditText") }) DemoAction.ClickFirstEditable else null
        DemoStep.SCROLL_CHECKOUT -> if (root.hasLabel("Checkout")) DemoAction.Scroll else null
        DemoStep.COMPLETE -> null
    }

    private fun clickIfPresent(root: SemanticNode, label: String) =
        if (root.any { it.clickable && it.label.equals(label, ignoreCase = true) }) DemoAction.Click(label) else null

    private fun SemanticNode.any(predicate: (SemanticNode) -> Boolean): Boolean =
        predicate(this) || children.any { it.any(predicate) }

    private fun SemanticNode.hasLabel(label: String): Boolean = any { it.label == label }
}
