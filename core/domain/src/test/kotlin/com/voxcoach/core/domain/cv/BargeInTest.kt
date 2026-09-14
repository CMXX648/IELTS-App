package com.voxcoach.core.domain.cv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BargeInTest {
    @Test
    fun micDuringTts_interruptsWithMarker() {
        val decision = BargeInController.onMicPressed(
            phase = BargeInPhase.TTS_PLAY,
            sessionElapsedMs = 12_345L,
        )
        assertThat(decision.shouldInterrupt).isTrue()
        assertThat(decision.stopTts).isTrue()
        assertThat(decision.cancelLlmTail).isTrue()
        assertThat(decision.bargeInAtMs).isEqualTo(12_345L)
    }

    @Test
    fun micDuringStreaming_interrupts() {
        val decision = BargeInController.onMicPressed(
            phase = BargeInPhase.STREAMING_AI,
            sessionElapsedMs = 500L,
        )
        assertThat(decision.shouldInterrupt).isTrue()
        assertThat(decision.bargeInAtMs).isEqualTo(500L)
    }

    @Test
    fun micWhileIdleOrListening_doesNotInterrupt() {
        for (phase in listOf(BargeInPhase.IDLE, BargeInPhase.LISTENING, BargeInPhase.ENDING)) {
            val decision = BargeInController.onMicPressed(phase, sessionElapsedMs = 1L)
            assertThat(decision.shouldInterrupt).isFalse()
            assertThat(decision.bargeInAtMs).isNull()
        }
    }

    @Test
    fun metaMerge_preservesExistingKeys() {
        val merged = BargeInController.withBargeInMeta(
            "{\"source\":\"part1_bank\",\"questionId\":\"q1\"}",
            bargeInAtMs = 777L,
        )
        assertThat(merged).contains("\"questionId\":\"q1\"")
        assertThat(merged).contains("\"bargeInAtMs\":777")
        assertThat(merged).contains("\"source\":\"barge-in\"")
    }

    @Test
    fun metaMerge_handlesEmptyAndLegacy() {
        assertThat(BargeInController.withBargeInMeta(null, 10L))
            .contains("\"bargeInAtMs\":10")
        val legacy = BargeInController.withBargeInMeta("not-json", 10L)
        assertThat(legacy).contains("not-json")
        assertThat(legacy).contains("\"bargeInAtMs\":10")
    }

    @Test
    fun budget_constantMatchesM3Acceptance() {
        assertThat(BargeInPolicy.MAX_STOP_MS).isEqualTo(400L)
    }
}
