package com.voxcoach.core.speech.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.voxcoach.core.domain.model.AsrFinal
import com.voxcoach.core.domain.model.AsrPartial
import com.voxcoach.core.domain.model.AsrSessionConfig
import com.voxcoach.core.domain.speech.AsrEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * System [SpeechRecognizer] backed ASR (M1 preferred path — no cloud audio upload).
 */
@Singleton
class SystemAsrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : AsrEngine {

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

    @Volatile private var recognizer: SpeechRecognizer? = null
    @Volatile private var running = false

    override suspend fun start(config: AsrSessionConfig) {
        stopInternal()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw IllegalStateException("SpeechRecognizer unavailable on this device")
        }
        suspendCancellableCoroutine { cont ->
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr
            running = true
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    running = false
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val texts = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val text = texts.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        _partials.tryEmit(AsrPartial(text, isFinal = true))
                        _finals.tryEmit(AsrFinal(text))
                    }
                    running = false
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val texts = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val text = texts.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        _partials.tryEmit(AsrPartial(text, isFinal = false))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            sr.startListening(intent)

            cont.invokeOnCancellation { stopInternal() }
        }
    }

    override suspend fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        running = false
        runCatching {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }
}
