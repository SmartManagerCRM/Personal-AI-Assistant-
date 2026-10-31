package com.smartmanager.ai.core.ai

import com.smartmanager.ai.core.intent.ActionName
import com.smartmanager.ai.core.intent.ParsedIntent
import com.smartmanager.ai.core.language.Lang

data class ChatMessage(val role: Role, val content: String) { enum class Role { SYSTEM, USER, ASSISTANT } }

data class AiRequest(
    val history: List<ChatMessage>,
    val userText: String,
    val language: Lang,
    val allowedActions: Set<ActionName>,
    val maxTokens: Int = 256,
)

sealed interface AiResponse {
    data class Text(val text: String) : AiResponse
    /** The model asked for a registered action; it still goes through [ActionRegistry] policy. */
    data class Action(val intent: ParsedIntent, val say: String?) : AiResponse
    data class Unavailable(val reason: String) : AiResponse
}

/**
 * Replaceable reasoning backend. Local providers (llama.cpp / Qwen3) are the default; online providers are
 * optional, disabled by default and clearly attributed to the user via [displayName] / [isOnline].
 */
interface AIProvider {
    val id: String
    val displayName: String
    val isOnline: Boolean
    suspend fun isAvailable(): Boolean
    suspend fun generate(request: AiRequest): AiResponse
    /** Release heavy resources (model weights) without affecting wake-word detection. */
    fun unload() {}
}

/** Always-available provider used when no model is installed: never fabricates an answer. */
class NoModelProvider : AIProvider {
    override val id = "none"
    override val displayName = "No AI model installed"
    override val isOnline = false
    override suspend fun isAvailable() = false
    override suspend fun generate(request: AiRequest): AiResponse = AiResponse.Unavailable("no model installed")
}

/**
 * Chooses a provider honouring the offline-first rules: local first, online only if the user enabled
 * hybrid mode *and* the provider is enabled, and never as a hidden fallback when [onlineAllowed] is false.
 */
class ProviderRouter(
    private val local: List<AIProvider>,
    private val online: List<AIProvider>,
    private val onlineAllowed: () -> Boolean,
    private val enabledOnline: () -> Set<String>,
) {
    var lastUsed: AIProvider? = null; private set
    suspend fun pick(): AIProvider? {
        for (p in local) if (p.isAvailable()) { lastUsed = p; return p }
        if (onlineAllowed()) for (p in online) if (p.id in enabledOnline() && p.isAvailable()) { lastUsed = p; return p }
        lastUsed = null
        return null
    }
}
