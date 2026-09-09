package com.voxcoach.core.domain.speech

/**
 * Continuous local session recording (RP-01 minimal).
 * Writes m4a only; never uploads. Relative turn ranges use [elapsedMs].
 */
interface SessionAudioCapture {
    /** Start FGS + MediaRecorder. Returns absolute local audio path. */
    fun start(sessionId: String): String

    /** Milliseconds since [start], for Turn.startMs/endMs. */
    fun elapsedMs(): Long

    val isRecording: Boolean

    /** Stop recorder and FGS; return final path (may be null if never started). */
    fun stop(): String?

    /** Crash-safe release (idempotent). Call from ViewModel.onCleared. */
    fun release()
}
