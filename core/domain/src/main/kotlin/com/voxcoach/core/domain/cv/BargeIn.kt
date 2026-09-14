package com.voxcoach.core.domain.cv

/**
 * M3 barge-in policy (docs/01 CV-04, docs/03 §2.3-3, docs/04 §4, docs/06 M3).
 *
 * Pressing the mic while AI is streaming/playing must win within 400 ms:
 * stop TTS + cancel the LLM tail, then enter LISTENING. The interruption
 * point (session-relative elapsed ms) is recorded into turn llmMetaJson
 * so replay (RP) can show where the user cut in.
 *
 * Pure JVM logic: clock is injected so unit tests stay deterministic.
 */
object BargeInPolicy {
    /** M3 acceptance: stop playback and enter listening within 400 ms. */
    const val MAX_STOP_MS = 400L

    const val META_KEY = "bargeInAtMs"
    const val META_SOURCE_KEY = "source"
    const val META_SOURCE_VALUE = "barge-in"
}

/**
 * Minimal playback/streaming phase the conversation ViewModels already track.
 * Kept in domain so every CV ViewModel (free/P1/P2/P3/full-mock) shares one rule.
 */
enum class BargeInPhase {
    IDLE,
    LISTENING,
    STREAMING_AI,
    TTS_PLAY,
    ENDING,
}

data class BargeInDecision(
    /** True when the mic press must preempt AI output. */
    val shouldInterrupt: Boolean,
    /** True when the LLM tail job must be cancelled as well. */
    val cancelLlmTail: Boolean,
    /** True when TTS stopAll() must be invoked. */
    val stopTts: Boolean,
    /** Session-relative interruption point for llmMetaJson (null when no interrupt). */
    val bargeInAtMs: Long?,
)

object BargeInController {
    fun onMicPressed(
        phase: BargeInPhase,
        sessionElapsedMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): BargeInDecision {
        val interrupting = phase == BargeInPhase.STREAMING_AI || phase == BargeInPhase.TTS_PLAY
        if (!interrupting) {
            return BargeInDecision(
                shouldInterrupt = false,
                cancelLlmTail = false,
                stopTts = false,
                bargeInAtMs = null,
            )
        }
        val atMs = (sessionElapsedMs ?: nowMs).coerceAtLeast(0L)
        return BargeInDecision(
            shouldInterrupt = true,
            cancelLlmTail = true,
            stopTts = true,
            bargeInAtMs = atMs,
        )
    }

    /**
     * Merge the interruption marker into an existing llmMetaJson object.
     * Existing keys are preserved; only the barge-in marker is added/overwritten.
     */
    fun withBargeInMeta(existingMetaJson: String?, bargeInAtMs: Long): String {
        val trimmed = existingMetaJson?.trim().orEmpty()
        val body = if (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else if (trimmed.isEmpty()) {
            ""
        } else {
            // Non-JSON legacy payload: keep it under a quoted fallback key pair is unsafe,
            // so preserve it as a plain string field value instead of dropping it.
            "\"legacy\":" + quote(trimmed)
        }
        val marker = "\"${BargeInPolicy.META_SOURCE_KEY}\":\"${BargeInPolicy.META_SOURCE_VALUE}\"," +
            "\"${BargeInPolicy.META_KEY}\":$bargeInAtMs"
        val merged = if (body.isEmpty()) marker else "$body,$marker"
        return "{$merged}"
    }

    private fun quote(raw: String): String =
        "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
