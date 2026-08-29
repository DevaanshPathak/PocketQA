package com.indium.pocketqa.controller

data class PatchValidation(val valid: Boolean, val reason: String? = null)

object PatchPolicy {
    fun validate(diff: String, allowedSourceKeys: Set<String>, maxBytes: Int = 128_000): PatchValidation {
        if (diff.toByteArray().size > maxBytes) return PatchValidation(false, "Patch exceeds size limit")
        val paths = Regex("(?m)^(?:---|\\+\\+\\+) [ab]/(.+)$").findAll(diff).map { it.groupValues[1] }.toList()
        if (paths.isEmpty()) return PatchValidation(false, "Unified diff headers are missing")
        if (paths.any { it.contains("..") || it.startsWith('/') || it !in allowedSourceKeys }) {
            return PatchValidation(false, "Patch targets a file outside the indexed source corpus")
        }
        return PatchValidation(true)
    }
}
