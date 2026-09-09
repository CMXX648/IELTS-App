package com.voxcoach.core.domain.speech

import com.voxcoach.core.domain.model.Sentence
import com.voxcoach.core.domain.model.TtsOptions
import kotlinx.coroutines.delay

/**
 * Injectable mock TTS that records spoken sentences for assertions.
 */
class MockTtsEngine : TtsEngine {
    val spoken = mutableListOf<String>()
    @Volatile var stopped = false
        private set

    override suspend fun speak(sentence: Sentence, opts: TtsOptions) {
        stopped = false
        spoken += sentence.text
        delay(50)
    }

    override suspend fun stopAll() {
        stopped = true
    }
}
