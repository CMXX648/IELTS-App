package com.voxcoach.core.llm.client

import com.voxcoach.core.domain.llm.LlmClient
import com.voxcoach.core.domain.model.ChatDelta
import com.voxcoach.core.domain.model.ChatMessage
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.ChatResult
import com.voxcoach.core.domain.model.LlmEndpointConfig
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * OpenAI-compatible `/v1/chat/completions` client with SSE streaming (OkHttp).
 * Endpoint config (baseUrl / apiKey / model) is supplied per call via [configProvider].
 */
class OpenAiCompatibleLlmClient(
    private val configProvider: () -> LlmEndpointConfig,
    private val httpClient: OkHttpClient = defaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) : LlmClient {

    override fun streamChat(request: ChatRequest): Flow<ChatDelta> = callbackFlow {
        val cfg = configProvider()
        require(cfg.apiKey.isNotBlank()) { "API key is empty — set it in Settings" }
        require(cfg.baseUrl.isNotBlank()) { "Base URL is empty — set it in Settings" }

        val body = buildRequestJson(request.copy(model = request.model.ifBlank { cfg.model }, stream = true))
        val httpRequest = Request.Builder()
            .url(joinUrl(cfg.baseUrl, "/v1/chat/completions"))
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        val factory = EventSources.createFactory(httpClient)
        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                val delta = parseSseData(data) ?: return
                trySend(delta)
                if (delta.finishReason != null) {
                    close()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val msg = t?.message
                    ?: response?.let { "HTTP ${it.code}: ${it.body?.string()?.take(200)}" }
                    ?: "SSE failure"
                close(IOException(msg, t))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val source = factory.newEventSource(httpRequest, listener)
        awaitClose { source.cancel() }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(request: ChatRequest): ChatResult = withContext(Dispatchers.IO) {
        val cfg = configProvider()
        require(cfg.apiKey.isNotBlank()) { "API key is empty — set it in Settings" }
        val body = buildRequestJson(request.copy(model = request.model.ifBlank { cfg.model }, stream = false))
        val httpRequest = Request.Builder()
            .url(joinUrl(cfg.baseUrl, "/v1/chat/completions"))
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.body?.string()?.take(300)}")
            }
            val raw = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(raw).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val content = choice?.get("message")?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull.orEmpty()
            val finish = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
            ChatResult(content = content, finishReason = finish)
        }
    }

    private fun buildRequestJson(request: ChatRequest): String {
        val obj = buildJsonObject {
            put("model", request.model)
            put("temperature", request.temperature)
            put("stream", request.stream)
            put(
                "messages",
                buildJsonArray {
                    for (m in request.messages) {
                        add(
                            buildJsonObject {
                                put(
                                    "role",
                                    when (m.role) {
                                        ChatMessage.Role.SYSTEM -> "system"
                                        ChatMessage.Role.USER -> "user"
                                        ChatMessage.Role.ASSISTANT -> "assistant"
                                    },
                                )
                                put("content", m.content)
                            },
                        )
                    }
                },
            )
        }
        return obj.toString()
    }

    private fun parseSseData(data: String): ChatDelta? {
        return runCatching {
            val root = json.parseToJsonElement(data).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val deltaObj = choice["delta"]?.jsonObject
            val content = deltaObj?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
            val finish = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
            if (content.isEmpty() && finish == null) null else ChatDelta(content, finish)
        }.getOrNull()
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun joinUrl(base: String, path: String): String {
            val b = base.trimEnd('/')
            return if (b.endsWith("/v1")) {
                b.removeSuffix("/v1") + path
            } else {
                b + path
            }
        }
    }
}
