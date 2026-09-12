package com.voxcoach.core.speech.asr

import android.util.Base64
import com.voxcoach.core.domain.model.AsrFinal
import com.voxcoach.core.domain.model.AsrPartial
import com.voxcoach.core.domain.model.AsrSessionConfig
import com.voxcoach.core.domain.model.MimoDefaults
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import com.voxcoach.core.domain.speech.AsrEngine
import com.voxcoach.core.domain.speech.SessionAudioCapture
import com.voxcoach.core.speech.audio.UtteranceAudioRecorder
import com.voxcoach.core.speech.mimo.MimoHttp
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * MiMo-V2.5 cloud ASR via OpenAI-compatible chat completions.
 * Press-to-talk: record until [stop], then upload wav.
 */
@Singleton
class MimoAsrEngine @Inject constructor(
    private val settings: LlmSettingsRepository,
    private val sessionAudioCapture: SessionAudioCapture,
    private val httpClient: OkHttpClient,
) : AsrEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()
    private val utterance = UtteranceAudioRecorder()

    private val _partials = MutableSharedFlow<AsrPartial>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _finals = MutableSharedFlow<AsrFinal>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val partialResults: Flow<AsrPartial> = _partials.asSharedFlow()
    override val finals: Flow<AsrFinal> = _finals.asSharedFlow()

    private var tapFrom: Int? = null
    private var languageTag: String = "en-GB"

    override suspend fun start(config: AsrSessionConfig) {
        val cfg = settings.config.first()
        MimoHttp.requireConfig(cfg)
        mutex.withLock {
            languageTag = config.languageTag
            val cursor = sessionAudioCapture.pcmByteCursor()
            if (cursor != null) {
                tapFrom = cursor
            } else {
                tapFrom = null
                withContext(Dispatchers.IO) { utterance.start() }
            }
        }
    }

    override suspend fun stop() {
        val wav = mutex.withLock {
            val from = tapFrom
            tapFrom = null
            if (from != null) {
                sessionAudioCapture.pcmSliceToWav(from) ?: ByteArray(0)
            } else {
                withContext(Dispatchers.IO) { utterance.stopToWav() }
            }
        }
        if (wav.size <= 44) return
        if (wav.size > MAX_WAV_BYTES) {
            throw IOException("录音过长，无法识别（超过 MiMo ASR 上限）")
        }
        _partials.emit(AsrPartial("正在识别…", isFinal = false))
        val text = withContext(Dispatchers.IO) { transcribe(wav, languageTag) }
        if (text.isBlank()) return
        _partials.emit(AsrPartial(text, isFinal = true))
        _finals.emit(AsrFinal(text))
    }

    private suspend fun transcribe(wav: ByteArray, languageTag: String): String {
        val cfg = settings.config.first()
        MimoHttp.requireConfig(cfg)
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        val body = buildJsonObject {
            put("model", MimoDefaults.ASR_MODEL)
            put("stream", false)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "input_audio")
                                            put(
                                                "input_audio",
                                                buildJsonObject {
                                                    put("data", "data:audio/wav;base64,$b64")
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
            put(
                "asr_options",
                buildJsonObject {
                    put("language", MimoHttp.asrLanguage(languageTag))
                },
            )
        }.toString()

        val request = MimoHttp.chatCompletionsRequest(cfg, body, sse = false)
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${raw.take(300)}")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            return extractTranscript(root)
        }
    }

    private fun extractTranscript(root: JsonObject): String {
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject ?: return ""
        return when (val content = message["content"]) {
            is JsonPrimitive -> content.contentOrNull.orEmpty().trim()
            is JsonArray -> content.joinToString("") { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull.orEmpty()
                    is JsonObject -> el["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    else -> ""
                }
            }.trim()
            else -> ""
        }
    }

    companion object {
        /** MiMo limit is 10MB base64; stay under ~7.5MB binary. */
        private const val MAX_WAV_BYTES = 7_500_000
    }
}
