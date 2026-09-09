package com.voxcoach.core.domain.speech

import com.voxcoach.core.domain.model.AsrFinal
import com.voxcoach.core.domain.model.AsrPartial
import com.voxcoach.core.domain.model.AsrSessionConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Injectable mock ASR for unit / smoke tests without a microphone.
 */
class MockAsrEngine(
    private val scriptedFinal: String = "I enjoy practising English every day.",
) : AsrEngine {
    private val _partials = MutableSharedFlow<AsrPartial>(extraBufferCapacity = 16)
    private val _finals = MutableSharedFlow<AsrFinal>(extraBufferCapacity = 16)

    override val partialResults: Flow<AsrPartial> = _partials.asSharedFlow()
    override val finals: Flow<AsrFinal> = _finals.asSharedFlow()

    override suspend fun start(config: AsrSessionConfig) {
        val words = scriptedFinal.split(" ")
        var acc = ""
        for (w in words) {
            acc = if (acc.isEmpty()) w else "$acc $w"
            _partials.emit(AsrPartial(acc, isFinal = false))
            delay(80)
        }
        _partials.emit(AsrPartial(scriptedFinal, isFinal = true))
        _finals.emit(AsrFinal(scriptedFinal, confidence = 1f))
    }

    override suspend fun stop() = Unit
}
