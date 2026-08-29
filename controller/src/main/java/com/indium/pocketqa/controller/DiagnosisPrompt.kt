package com.indium.pocketqa.controller

object DiagnosisPrompt {
    fun build(finding: BugFinding, sourceKey: String, sourceExcerpt: String, trace: List<ActionEvent>): String = """
        You are PocketQA, an offline Android QA assistant.
        Analyze this reproducible test finding. Return only three short labeled sections:
        Cause:, Reproduce:, Patch rationale:. Do not reveal chain-of-thought.

        Finding: ${finding.title}
        Evidence: ${finding.evidence}
        Source file: $sourceKey
        Recent actions:
        ${trace.takeLast(10).joinToString("\n") { "- ${it.kind}: ${it.detail}" }}
        Source excerpt:
        ${sourceExcerpt.take(900)}
    """.trimIndent()
}
