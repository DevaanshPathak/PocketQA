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

    fun patch(finding: BugFinding, chunks: List<SourceChunk>, trace: List<ActionEvent>): String = """
        You are PocketQA patch generation. Return only a valid unified diff, with no markdown fences.
        Change only files present in the supplied source chunks. Make the smallest safe fix.
        If evidence is insufficient, return exactly ABSTAIN: <reason>.
        Finding: ${finding.title}
        Evidence: ${finding.evidence}
        Trace: ${trace.takeLast(12).joinToString(" | ") { "${it.kind}:${it.detail}" }}
        Source chunks:
        ${boundedContext(chunks)}
    """.trimIndent()

    private fun boundedContext(chunks: List<SourceChunk>, maxChars: Int = 72_000): String {
        val result = StringBuilder()
        chunks.forEach { chunk ->
            val header = "FILE ${chunk.sourceKey} LINES ${chunk.startLine}-${chunk.endLine}\n"
            val remaining = maxChars - result.length - header.length
            if (remaining <= 0) return@forEach
            result.append(header).append(chunk.text.take(remaining)).append("\n\n")
        }
        return result.toString()
    }
}
