package com.voxcoach.core.domain.model

/**
 * Domain models for M1 smoke path (CV turn + speech/LLM payloads).
 * Full Session/Turn Room schema lands in M2.
 */

data class AsrPartial(
    val text: String,
    val isFinal: Boolean = false,
)

data class AsrFinal(
    val text: String,
    val confidence: Float? = null,
)

data class AsrSessionConfig(
    val languageTag: String = "en-GB",
    val preferOffline: Boolean = false,
)

data class Sentence(
    val text: String,
)

data class TtsOptions(
    val languageTag: String = "en-GB",
    val speechRate: Float = 0.9f,
    val pitch: Float = 1.0f,
)

data class ChatMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val stream: Boolean = true,
)

data class ChatDelta(
    val content: String,
    val finishReason: String? = null,
)

data class ChatResult(
    val content: String,
    val finishReason: String? = null,
)

data class LlmEndpointConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)
