package com.voxcoach.core.speech.mimo

import com.voxcoach.core.domain.model.LlmEndpointConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal object MimoHttp {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun requireConfig(cfg: LlmEndpointConfig) {
        require(cfg.apiKey.isNotBlank()) { "API key is empty — set it in Settings" }
        require(cfg.baseUrl.isNotBlank()) { "Base URL is empty — set it in Settings" }
    }

    fun joinUrl(base: String, path: String): String {
        val b = base.trimEnd('/')
        return if (b.endsWith("/v1")) {
            b.removeSuffix("/v1") + path
        } else {
            b + path
        }
    }

    fun chatCompletionsRequest(
        cfg: LlmEndpointConfig,
        jsonBody: String,
        sse: Boolean,
    ): Request {
        val builder = Request.Builder()
            .url(joinUrl(cfg.baseUrl, "/v1/chat/completions"))
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("api-key", cfg.apiKey)
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON))
        if (sse) {
            builder.header("Accept", "text/event-stream")
        }
        return builder.build()
    }

    fun asrLanguage(languageTag: String): String {
        val primary = languageTag.substringBefore('-').lowercase()
        return when (primary) {
            "en", "zh" -> primary
            else -> "auto"
        }
    }
}
