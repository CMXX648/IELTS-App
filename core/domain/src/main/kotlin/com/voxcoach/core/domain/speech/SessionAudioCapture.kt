package com.voxcoach.core.domain.speech

/**
 * Continuous local session recording (RP-01 minimal).
 * Writes wav locally; never uploaded to the sync server.
 * Relative turn ranges use [elapsedMs].
 */
interface SessionAudioCapture {
    /** Start FGS + mic capture. Returns absolute local audio path. */
    fun start(sessionId: String): String

    /** Milliseconds since [start], for Turn.startMs/endMs. */
    fun elapsedMs(): Long

    val isRecording: Boolean

    /** Stop recorder and FGS; return final path (may be null if never started). */
    fun stop(): String?

    /** Crash-safe release (idempotent). Call from ViewModel.onCleared. */
    fun release()

    /** Current PCM byte cursor while recording, for cloud ASR utterance slices. */
    fun pcmByteCursor(): Int? = null

    /** WAV (PCM16LE mono) from [fromByte] to now. Null if not tapping this capture. */
    fun pcmSliceToWav(fromByte: Int): ByteArray? = null
}
