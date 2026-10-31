package com.smartmanager.ai.ai

import android.content.Context
import com.smartmanager.ai.core.ai.*
import com.smartmanager.ai.core.intent.ActionName
import com.smartmanager.ai.core.language.Lang
import com.smartmanager.ai.data.SettingsStore
import com.smartmanager.ai.models.ModelCatalog
import com.smartmanager.ai.models.ModelManager
import com.smartmanager.ai.net.NetworkGate
import com.smartmanager.ai.security.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Local brain: Qwen3 via llama.cpp (JNI). Reports itself unavailable until both the GGUF model and the native library are present. */
@Singleton
class LlamaCppProvider @Inject constructor(private val models: ModelManager) : AIProvider {
    override val id = "llama-cpp"; override val displayName = "Qwen3 1.7B (local, llama.cpp)"; override val isOnline = false
    private val entry get() = ModelCatalog.byId("llm-qwen3-1.7b-q4km")!!
    private var handle = 0L
    val nativeLoaded: Boolean get() = NativeLlama.available
    @Volatile var lastTokensPerSecond = 0.0; private set
    @Volatile var loadMs = -1L; private set
    override suspend fun isAvailable() = nativeLoaded && models.isInstalled(entry)
    val unavailableReason: String get() = when { !nativeLoaded -> "llama.cpp native library is not bundled in this build"; !models.isInstalled(entry) -> "model not installed"; else -> "" }

    @Synchronized private fun ensureLoaded() {
        if (handle != 0L) return
        val t0 = System.currentTimeMillis()
        handle = NativeLlama.load(File(models.dir(entry.id), "Qwen3-1.7B-Q4_K_M.gguf").absolutePath, 4096, 4)
        loadMs = System.currentTimeMillis() - t0
        if (handle == 0L) error("llama.cpp failed to load the model")
    }

    override suspend fun generate(request: AiRequest): AiResponse = withContext(Dispatchers.Default) {
        if (!isAvailable()) return@withContext AiResponse.Unavailable(unavailableReason)
        try {
            ensureLoaded()
            val prompt = StringBuilder("<|im_start|>system\n${PromptBuilder.system(request.language, request.allowedActions)}<|im_end|>\n")
            request.history.takeLast(6).forEach { prompt.append("<|im_start|>${if (it.role == ChatMessage.Role.USER) "user" else "assistant"}\n${it.content}<|im_end|>\n") }
            prompt.append("<|im_start|>user\n${request.userText}<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n")
            val t0 = System.nanoTime()
            val out = NativeLlama.generateText(handle, prompt.toString(), request.maxTokens, 0.4f)
            val secs = (System.nanoTime() - t0) / 1e9; lastTokensPerSecond = if (secs > 0) (out.length / 3.5) / secs else 0.0
            StructuredOutput.parse(out, request.allowedActions)
        } catch (e: Throwable) { AiResponse.Unavailable(e.message ?: "llama error") }
    }
    @Synchronized override fun unload() { if (handle != 0L) { NativeLlama.free(handle); handle = 0 } }
}

/** JNI bridge (libsmartllama.so, built from llama.cpp when the sources are present — see docs/MODEL_INSTALL.md). */
object NativeLlama {
    /** arm64 with dot-product instructions only (the library is built for armv8.2-a+dotprod); anything else → local LLM unavailable, never a crash. */
    val available: Boolean = try {
        val arm64 = android.os.Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
        val dotprod = arm64 && File("/proc/cpuinfo").readLines().any { it.startsWith("Features") && it.split(' ', '\t', ':').contains("asimddp") }
        if (dotprod) { System.loadLibrary("smartllama"); true } else false
    } catch (_: Throwable) { false }
    external fun load(path: String, nCtx: Int, threads: Int): Long
    private external fun generate(handle: Long, prompt: String, maxTokens: Int, temperature: Float): ByteArray
    fun generateText(handle: Long, prompt: String, maxTokens: Int, temperature: Float): String = String(generate(handle, prompt, maxTokens, temperature), Charsets.UTF_8)
    external fun free(handle: Long)
}

/** Optional online reasoning through any OpenAI-compatible endpoint. Disabled by default; needs the master network switch, hybrid mode, this provider enabled, and a user-supplied key. */
@Singleton
class OpenAiCompatibleProvider @Inject constructor(@ApplicationContext private val ctx: Context, private val settings: SettingsStore, private val gate: NetworkGate, private val secrets: SecretStore) : AIProvider {
    override val id = "openai-compatible"; override val displayName = "Online provider (OpenAI-compatible)"; override val isOnline = true
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val prefs get() = ctx.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    fun saveKey(key: String) { prefs.edit().putString("online_key", if (key.isBlank()) null else secrets.encrypt(key)).apply() }
    fun hasKey() = prefs.getString("online_key", null) != null
    private fun key() = prefs.getString("online_key", null)?.let { runCatching { secrets.decrypt(it) }.getOrNull() }

    override suspend fun isAvailable(): Boolean { val s = settings.data.first(); return s.networkAllowed && s.hybridMode && s.onlineEndpoint.startsWith("https://") && hasKey() }

    override suspend fun generate(request: AiRequest): AiResponse = withContext(Dispatchers.IO) {
        gate.require("online AI provider")
        val s = settings.data.first()
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", PromptBuilder.system(request.language, request.allowedActions)))
        request.history.takeLast(6).forEach { msgs.put(JSONObject().put("role", if (it.role == ChatMessage.Role.USER) "user" else "assistant").put("content", it.content)) }
        msgs.put(JSONObject().put("role", "user").put("content", request.userText))
        val body = JSONObject().put("model", s.onlineModel).put("messages", msgs).put("max_tokens", request.maxTokens).put("temperature", 0.4)
        val req = Request.Builder().url(s.onlineEndpoint.trimEnd('/') + "/chat/completions").header("Authorization", "Bearer ${key()}").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext AiResponse.Unavailable("HTTP ${r.code}")
                val text = JSONObject(r.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                StructuredOutput.parse(text, request.allowedActions)
            }
        } catch (e: Exception) { AiResponse.Unavailable(e.message ?: "network error") }
    }
}
