package com.indium.pocketqa.controller

object RepoSelection {
    fun isSafePackageName(value: String): Boolean = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(value)

    fun bindingFileName(packageName: String): String {
        require(isSafePackageName(packageName)) { "Invalid target package" }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(packageName.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "target-$digest.json"
    }

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
