package com.indium.pocketqa.controller

data class SourceChunk(val sourceKey: String, val startLine: Int, val endLine: Int, val text: String)

class SourceRagIndex(private val chunks: List<SourceChunk>) {
    fun search(query: String, limit: Int = 6): List<SourceChunk> {
        val terms = query.lowercase().split(Regex("[^a-z0-9_]+"))
            .filter { it.length > 2 }.toSet()
        return chunks.map { chunk ->
            val body = (chunk.sourceKey + " " + chunk.text).lowercase()
            chunk to terms.sumOf { term -> Regex("\\b${Regex.escape(term)}").findAll(body).count() }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}
