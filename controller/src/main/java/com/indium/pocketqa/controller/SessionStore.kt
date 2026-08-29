package com.indium.pocketqa.controller

enum class TestGoal(val title: String, val description: String) {
    CATALOG("Catalog rendering", "Check product cards and catalog semantics"),
    CART_AND_CHECKOUT("Cart and checkout", "Check cart quantities and checkout validation"),
    FULL_SCAN("Full bug scan", "Run every deterministic PocketQA check"),
}

enum class ExplorationMode(val label: String) {
    DETERMINISTIC("Deterministic (fast fallback)"),
    GEMMA_ASSISTED("Guided Gemma QA (local GPU)"),
    GEMMA_AUTONOMOUS("Experimental vision explorer");

    override fun toString(): String = label
}

enum class RunStatus { IDLE, RUNNING, COMPLETE, STOPPED, ERROR }

data class ActionEvent(
    val timestampMs: Long,
    val kind: String,
    val detail: String,
)

data class SessionSnapshot(
    val status: RunStatus = RunStatus.IDLE,
    val goal: TestGoal? = null,
    val explorationMode: ExplorationMode = ExplorationMode.DETERMINISTIC,
    val actions: List<ActionEvent> = emptyList(),
    val findings: List<BugFinding> = emptyList(),
    val error: String? = null,
    val screenshotPath: String? = null,
    val visualFallbackActive: Boolean = false,
)

/** Thread-safe in-memory state for the visible current PocketQA run. */
open class SessionStore {
    private var current = SessionSnapshot()
    private val listeners = linkedSetOf<(SessionSnapshot) -> Unit>()

    @Synchronized
    fun snapshot(): SessionSnapshot = current

    @Synchronized
    fun subscribe(listener: (SessionSnapshot) -> Unit): () -> Unit {
        listeners += listener
        listener(current)
        return { unsubscribe(listener) }
    }

    fun start(goal: TestGoal, mode: ExplorationMode = ExplorationMode.DETERMINISTIC) = update {
        SessionSnapshot(status = RunStatus.RUNNING, goal = goal, explorationMode = mode)
    }

    fun record(kind: String, detail: String) = update { snapshot ->
        snapshot.copy(actions = (snapshot.actions + ActionEvent(System.currentTimeMillis(), kind, detail)).takeLast(80))
    }

    fun recordFinding(finding: BugFinding) = update { snapshot ->
        val findings = if (snapshot.findings.any { it.title == finding.title }) snapshot.findings else snapshot.findings + finding
        snapshot.copy(findings = findings)
    }

    fun complete() = update { it.copy(status = RunStatus.COMPLETE) }
    fun stop() = update { it.copy(status = RunStatus.STOPPED) }
    fun fail(message: String) = update { it.copy(status = RunStatus.ERROR, error = message) }
    fun recordScreenshot(path: String) = update { it.copy(screenshotPath = path) }
    fun setVisualFallback(active: Boolean) = update { it.copy(visualFallbackActive = active) }

    private fun unsubscribe(listener: (SessionSnapshot) -> Unit) = synchronized(this) { listeners -= listener }

    private fun update(transform: (SessionSnapshot) -> SessionSnapshot) {
        val listenersToNotify: List<(SessionSnapshot) -> Unit>
        val changed: SessionSnapshot
        synchronized(this) {
            changed = transform(current)
            current = changed
            listenersToNotify = listeners.toList()
        }
        listenersToNotify.forEach { it(changed) }
    }
}

object PocketQaSessionStore : SessionStore()
