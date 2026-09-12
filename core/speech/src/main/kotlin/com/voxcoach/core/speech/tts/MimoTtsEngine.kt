package com.voxcoach.core.speech.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.voxcoach.core.domain.model.MimoDefaults
import com.voxcoach.core.domain.model.Sentence
import com.voxcoach.core.domain.model.TtsOptions
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import com.voxcoach.core.domain.speech.TtsEngine
import com.voxcoach.core.speech.mimo.MimoHttp
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * MiMo-V2.5 TTS (pcm16 stream @ 24 kHz) played via [AudioTrack].
 */
@Singleton
class MimoTtsEngine @Inject constructor(
    private val settings: LlmSettingsRepository,
    private val httpClient: OkHttpClient,
) : TtsEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cancelled = AtomicBoolean(false)
    @Volatile private var track: AudioTrack? = null
    @Volatile private var eventSource: EventSource? = null

    override suspend fun speak(sentence: Sentence, opts: TtsOptions) {
        val text = sentence.text.trim()
        if (text.isEmpty()) return
        cancelled.set(false)
        stopPlayback()
        val cfg = settings.config.first()
        MimoHttp.requireConfig(cfg)
        val body = buildJsonObject {
            put("model", MimoDefaults.TTS_MODEL)
            put("stream", true)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", MimoDefaults.TTS_STYLE)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "assistant")
                            put("content", text)
                        },
                    )
                },
            )
            put(
                "audio",
                buildJsonObject {
                    put("format", "pcm16")
                    put("voice", MimoDefaults.TTS_VOICE)
                },
            )
        }.toString()

        withContext(Dispatchers.IO) {
            playStream(MimoHttp.chatCompletionsRequest(cfg, body, sse = true))
        }
    }

    override suspend fun stopAll() {
        cancelled.set(true)
        eventSource?.cancel()
        eventSource = null
        stopPlayback()
    }

    private suspend fun playStream(request: okhttp3.Request) {
        val player = ensureTrack()
        suspendCancellableCoroutine { cont ->
            val finished = AtomicBoolean(false)
            fun complete(error: Throwable? = null) {
                if (!finished.compareAndSet(false, true)) return
                if (!cont.isActive) return
                if (error != null) cont.resumeWithException(error) else cont.resume(Unit)
            }
            val factory = EventSources.createFactory(httpClient)
            val listener = object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    if (cancelled.get() || finished.get()) return
                    if (data == "[DONE]") {
                        complete()
                        return
                    }
                    val pcm = parseAudioDelta(data) ?: return
                    if (pcm.isEmpty()) return
                    val written = player.write(pcm, 0, pcm.size)
                    if (written < 0) {
                        complete(IOException("AudioTrack write failed: $written"))
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    if (cancelled.get()) {
                        complete()
                        return
                    }
                    val msg = t?.message
                        ?: response?.let { "HTTP ${it.code}: ${it.body?.string()?.take(200)}" }
                        ?: "TTS SSE failure"
                    complete(IOException(msg, t))
                }

                override fun onClosed(eventSource: EventSource) {
                    complete()
                }
            }
            val source = factory.newEventSource(request, listener)
            eventSource = source
            cont.invokeOnCancellation {
                cancelled.set(true)
                source.cancel()
                stopPlayback()
            }
        }
        if (!cancelled.get()) {
            delay(120)
        }
        stopPlayback()
    }

    private fun parseAudioDelta(data: String): ByteArray? {
        return runCatching {
            val root = json.parseToJsonElement(data).jsonObject
            val delta = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("delta")?.jsonObject ?: return null
            val audio = delta["audio"]?.jsonObject ?: return null
            val b64 = audio["data"]?.jsonPrimitive?.contentOrNull ?: return null
            if (b64.isBlank()) null else Base64.decode(b64, Base64.DEFAULT)
        }.getOrNull()
    }

    private fun ensureTrack(): AudioTrack {
        stopPlayback()
        val min = AudioTrack.getMinBufferSize(
            MimoDefaults.TTS_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(MimoDefaults.TTS_SAMPLE_RATE / 4)
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(MimoDefaults.TTS_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(min * 2)
            .build()
        track = created
        created.play()
        return created
    }

    private fun stopPlayback() {
        val current = track
        track = null
        if (current == null) return
        runCatching {
            current.pause()
            current.flush()
            current.stop()
            current.release()
        }
    }
}
