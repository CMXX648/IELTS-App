package com.voxcoach.core.speech.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.voxcoach.core.domain.model.Sentence
import com.voxcoach.core.domain.model.TtsOptions
import com.voxcoach.core.domain.speech.TtsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SystemTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsEngine {

    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)

    private suspend fun ensureReady() {
        if (ready.get() && tts != null) return
        mutex.withLock {
            if (ready.get() && tts != null) return
            suspendCancellableCoroutine { cont ->
                val engine = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        ready.set(true)
                        if (cont.isActive) cont.resume(Unit)
                    } else {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("TextToSpeech init failed: $status"),
                            )
                        }
                    }
                }
                tts = engine
                cont.invokeOnCancellation {
                    engine.shutdown()
                    tts = null
                    ready.set(false)
                }
            }
        }
    }

    override suspend fun speak(sentence: Sentence, opts: TtsOptions) {
        ensureReady()
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(opts.languageTag)
        engine.language = locale
        engine.setSpeechRate(opts.speechRate)
        engine.setPitch(opts.pitch)

        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
            })
            engine.speak(sentence.text, TextToSpeech.QUEUE_ADD, null, id)
            cont.invokeOnCancellation { engine.stop() }
        }
    }

    override suspend fun stopAll() {
        tts?.stop()
    }
}
