package com.indium.pocketqa.controller

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface CloudPatchResult {
    data class Success(val text: String) : CloudPatchResult
    data class Failed(val message: String) : CloudPatchResult
}

class OpenRouterClient {
    fun generate(config: CloudEscalationConfig, prompt: String): CloudPatchResult = runCatching {
        require(config.ready) { "OpenRouter BYOK is not enabled" }
        val body = JSONObject().put("model", config.model).put("messages", JSONArray().put(
            JSONObject().put("role", "user").put("content", prompt)
        )).put("temperature", 0.1).put("max_tokens", 1800)
        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 90_000; doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Title", "PocketQA")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val status = connection.responseCode
        val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText().take(256_000) }.orEmpty()
        if (status !in 200..299) error("OpenRouter HTTP $status: ${JSONObject.quote(raw.take(300))}")
        JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }.fold({ CloudPatchResult.Success(it) }, { CloudPatchResult.Failed(it.message ?: it.javaClass.simpleName) })
}
