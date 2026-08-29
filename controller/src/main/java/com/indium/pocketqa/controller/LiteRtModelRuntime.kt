package com.indium.pocketqa.controller

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.benchmark
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors

sealed interface ModelLoadResult {
    data class Ready(val initializationMs: Long, val modelPath: String) : ModelLoadResult
    data class Missing(val expectedPath: String) : ModelLoadResult
    data class Failed(val message: String) : ModelLoadResult
}

sealed interface ModelPromptResult {
    data class Success(val text: String, val elapsedMs: Long) : ModelPromptResult
    data class Failed(val message: String) : ModelPromptResult
}

data class BenchmarkPass(
    val timeToFirstTokenMs: Long,
    val prefillTokens: Int,
    val decodeTokens: Int,
    val prefillTokensPerSecond: Double,
    val decodeTokensPerSecond: Double,
    val response: String,
)

sealed interface ModelBenchmarkResult {
    data class Success(val passes: List<BenchmarkPass>, val elapsedMs: Long) : ModelBenchmarkResult
    data class Failed(val message: String) : ModelBenchmarkResult
}

/** Owns one LiteRT-LM engine. All initialization happens away from the UI thread. */
class LiteRtModelRuntime(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private var engine: Engine? = null

    fun modelFile(): File = ModelInstallContract.file(appContext)

    fun initialize(onResult: (ModelLoadResult) -> Unit) {
        executor.execute {
            val model = modelFile()
            if (!model.isFile || model.length() == 0L) {
                onResult(ModelLoadResult.Missing(model.absolutePath))
                return@execute
            }
            engine?.takeIf { it.isInitialized() }?.let {
                onResult(ModelLoadResult.Ready(initializationMs = 0, modelPath = model.absolutePath))
                return@execute
            }
            val startedAt = System.nanoTime()
            val result = runCatching {
                @OptIn(ExperimentalApi::class)
                ExperimentalFlags.enableSpeculativeDecoding = true
                Engine(
                    EngineConfig(
                        modelPath = model.absolutePath,
                        backend = Backend.GPU(),
                        maxNumTokens = 512,
                        cacheDir = appContext.cacheDir.absolutePath,
                    ),
                ).also { it.initialize() }
            }.fold(
                onSuccess = { initialized ->
                    engine?.close()
                    engine = initialized
                    ModelLoadResult.Ready(
                        initializationMs = (System.nanoTime() - startedAt) / 1_000_000,
                        modelPath = model.absolutePath,
                    )
                },
                onFailure = {
                    Log.e(TAG, "LiteRT engine initialization failed", it)
                    ModelLoadResult.Failed(it.describeRootCause())
                },
            )
            onResult(result)
        }
    }

    /** Generates one bounded plain-text response for the physical-device spike. */
    fun runSmokePrompt(prompt: String, onResult: (ModelPromptResult) -> Unit) {
        executor.execute {
            val activeEngine = engine
            if (activeEngine == null || !activeEngine.isInitialized()) {
                onResult(ModelPromptResult.Failed("Model is not initialized"))
                return@execute
            }
            val startedAt = System.nanoTime()
            val result = runCatching {
                activeEngine.createConversation().use { conversation ->
                    conversation.sendMessage(prompt)
                        .contents
                        .contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                }
            }.fold(
                onSuccess = { text ->
                    ModelPromptResult.Success(
                        text = text,
                        elapsedMs = (System.nanoTime() - startedAt) / 1_000_000,
                    )
                },
                onFailure = { ModelPromptResult.Failed(it.describeRootCause()) },
            )
            onResult(result)
        }
    }

    /** Sends a real screenshot plus text instruction to the multimodal E4B artifact. */
    fun runVisionPrompt(imageFile: File, prompt: String, onResult: (ModelPromptResult) -> Unit) {
        executor.execute {
            val activeEngine = engine
            if (activeEngine == null || !activeEngine.isInitialized()) {
                onResult(ModelPromptResult.Failed("Model is not initialized"))
                return@execute
            }
            if (!ModelInstallContract.supportsVision(appContext)) {
                onResult(ModelPromptResult.Failed("Vision artifact missing; install ${ModelInstallContract.visionFileName}"))
                return@execute
            }
            if (!imageFile.isFile || imageFile.length() == 0L) {
                onResult(ModelPromptResult.Failed("Screenshot file is unavailable"))
                return@execute
            }
            val startedAt = System.nanoTime()
            val result = runCatching {
                activeEngine.createConversation().use { conversation ->
                    val contents = Contents.of(
                        Content.ImageFile(imageFile.absolutePath),
                        Content.Text(prompt),
                    )
                    conversation.sendMessage(Message.user(contents))
                        .contents
                        .contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                }
            }.fold(
                onSuccess = { text -> ModelPromptResult.Success(text, (System.nanoTime() - startedAt) / 1_000_000) },
                onFailure = { ModelPromptResult.Failed(it.describeRootCause()) },
            )
            onResult(result)
        }
    }

    /**
     * Three bounded reasoning passes for a repeatable thermal/throughput check.
     * The selected model uses LiteRT's GPU backend. It deliberately does not
     * claim NPU execution because the runtime backend is GPU.
     */
    @OptIn(ExperimentalApi::class)
    fun runThermalBenchmark(onResult: (ModelBenchmarkResult) -> Unit) {
        executor.execute {
            val model = modelFile()
            if (!model.isFile || model.length() == 0L) {
                onResult(ModelBenchmarkResult.Failed("Model is missing at ${model.absolutePath}"))
                return@execute
            }
            // The benchmark API creates its own engine. Release any chat engine
            // first; two E4B engines exceed the practical memory budget on the
            // demo phone and can cause Android to restart the process.
            engine?.close()
            engine = null
            val startedAt = System.nanoTime()
            val result = runCatching {
                (1..3).map { pass ->
                    val benchmarkMetrics = benchmark(
                        modelPath = model.absolutePath,
                        backend = Backend.GPU(),
                        prefillTokens = 256,
                        decodeTokens = 128,
                        cacheDir = appContext.cacheDir.absolutePath,
                        prompt = "PocketQA benchmark pass $pass. Diagnose a cart quantity becoming negative.",
                    )
                    BenchmarkPass(
                        timeToFirstTokenMs = (benchmarkNumber(benchmarkMetrics, "getTimeToFirstTokenInSecond") * 1_000).toLong(),
                        prefillTokens = benchmarkNumber(benchmarkMetrics, "getLastPrefillTokenCount").toInt(),
                        decodeTokens = benchmarkNumber(benchmarkMetrics, "getLastDecodeTokenCount").toInt(),
                        prefillTokensPerSecond = benchmarkNumber(benchmarkMetrics, "getLastPrefillTokensPerSecond"),
                        decodeTokensPerSecond = benchmarkNumber(benchmarkMetrics, "getLastDecodeTokensPerSecond"),
                        response = "LiteRT GPU benchmark pass $pass completed.",
                    )
                }
            }.fold(
                onSuccess = { passes ->
                    ModelBenchmarkResult.Success(
                        passes = passes,
                        elapsedMs = (System.nanoTime() - startedAt) / 1_000_000,
                    )
                },
                onFailure = { ModelBenchmarkResult.Failed(it.describeRootCause()) },
            )
            onResult(result)
        }
    }

    private fun benchmarkNumber(metrics: Any?, getter: String): Double = try {
        (requireNotNull(metrics).javaClass.getMethod(getter).invoke(metrics) as Number).toDouble()
    } catch (error: InvocationTargetException) {
        throw (error.targetException ?: error)
    }

    private fun Throwable.describeRootCause(): String {
        var current: Throwable = this
        while (current is InvocationTargetException && current.targetException != null) {
            current = current.targetException
        }
        return current.message ?: current.javaClass.simpleName
    }

    override fun close() {
        engine?.close()
        engine = null
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "PocketQAModel"
    }
}
