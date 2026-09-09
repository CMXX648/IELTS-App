package com.voxcoach.core.domain.speech

import com.voxcoach.core.domain.model.Sentence
import com.voxcoach.core.domain.model.TtsOptions

interface TtsEngine {
    suspend fun speak(sentence: Sentence, opts: TtsOptions = TtsOptions())
    suspend fun stopAll()
}
