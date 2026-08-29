package com.indium.pocketqa.controller

object RepoSelection {
    fun isSafeHttpsRepoUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && value.length <= 2_048
    }.getOrDefault(false)

    fun isSafeSubfolder(value: String): Boolean {
        if (value.isBlank()) return true
        val normalized = value.replace('\\', '/')
        return !normalized.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(normalized) &&
            normalized.split('/').none { it == ".." || it.isBlank() }
    }
}
