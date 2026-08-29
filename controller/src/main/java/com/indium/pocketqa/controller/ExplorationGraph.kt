package com.indium.pocketqa.controller

/** Local, session-scoped map of observed UI states and untried action edges. */
class ExplorationGraph {
    private val tried = mutableMapOf<String, MutableSet<String>>()

    fun fingerprint(snapshot: SemanticNode, actions: List<String>): String =
        (labels(snapshot).sorted() + actions.sorted()).joinToString("|").hashCode().toString()

    fun untried(state: String, actions: List<String>): List<String> =
        actions.filterNot { it in tried[state].orEmpty() }

    fun record(state: String, action: String) {
        tried.getOrPut(state) { linkedSetOf() } += action
    }

    fun clear() = tried.clear()

    private fun labels(node: SemanticNode): List<String> =
        listOfNotNull(node.label?.takeIf { it.isNotBlank() }) + node.children.flatMap(::labels)
}
