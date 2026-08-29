package com.indium.pocketqa.controller

import android.content.Context
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class RepoRequest(val url: String, val ref: String = "main", val subfolder: String = "", val token: String = "")
data class RepoCorpus(val root: File, val revision: String, val chunks: List<SourceChunk>)

class RepoCloneManager(context: Context) {
    private val reposDir = File(context.filesDir, "repos").also { it.mkdirs() }
    private val allowedExtensions = setOf("kt", "java", "dart", "ts", "tsx", "js", "py", "go", "swift", "xml", "json")

    fun cloneAndIndex(request: RepoRequest): RepoCorpus {
        require(RepoSelection.isSafeHttpsRepoUrl(request.url)) { "Enter a credential-free HTTPS repository URL" }
        require(RepoSelection.isSafeSubfolder(request.subfolder)) { "Invalid source subfolder" }
        val id = sha256(request.url).take(16)
        val target = File(reposDir, id)
        require(target.canonicalFile.parentFile == reposDir.canonicalFile) { "Invalid repository destination" }
        if (target.exists()) target.deleteRecursively()
        val command = Git.cloneRepository().setURI(request.url).setDirectory(target).setCloneAllBranches(false)
            .setBranch(request.ref).setDepth(1)
        if (request.token.isNotBlank()) command.setCredentialsProvider(UsernamePasswordCredentialsProvider("oauth2", request.token))
        command.call().use { git ->
            val revision = git.repository.resolve("HEAD").name
            val selected = File(target, request.subfolder).canonicalFile
            require(selected.toPath().startsWith(target.canonicalFile.toPath())) { "Subfolder escapes repository" }
            require(selected.isDirectory) { "Selected source subfolder does not exist" }
            val corpus = RepoCorpus(target, revision, index(selected, target))
            persistSelection(request.copy(token = ""), id, revision)
            return corpus
        }
    }

    fun loadLast(): Pair<RepoRequest, RepoCorpus>? {
        val state = File(reposDir, "current.json")
        if (!state.isFile) return null
        return runCatching {
            val json = JSONObject(state.readText())
            val request = RepoRequest(json.getString("url"), json.getString("ref"), json.getString("subfolder"))
            val target = File(reposDir, json.getString("id")).canonicalFile
            require(target.parentFile == reposDir.canonicalFile && target.isDirectory)
            val selected = File(target, request.subfolder).canonicalFile
            require(selected.toPath().startsWith(target.toPath()) && selected.isDirectory)
            request to RepoCorpus(target, json.getString("revision"), index(selected, target))
        }.getOrNull()
    }

    private fun persistSelection(request: RepoRequest, id: String, revision: String) {
        val json = JSONObject().put("url", request.url).put("ref", request.ref)
            .put("subfolder", request.subfolder).put("id", id).put("revision", revision)
        val pending = File(reposDir, "current.json.tmp")
        pending.writeText(json.toString())
        Files.move(pending.toPath(), File(reposDir, "current.json").toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun index(selected: File, repoRoot: File): List<SourceChunk> = selected.walkTopDown()
        .filter { it.isFile && it.canonicalFile.toPath().startsWith(repoRoot.canonicalFile.toPath()) && it.extension.lowercase() in allowedExtensions && it.length() <= 512_000 }
        .flatMap { file ->
            val lines = file.readLines()
            lines.chunked(120).mapIndexed { index, block ->
                SourceChunk(file.relativeTo(repoRoot).invariantSeparatorsPath, index * 120 + 1, index * 120 + block.size, block.joinToString("\n"))
            }
        }.take(2_000).toList()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
