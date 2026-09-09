package com.voxcoach.core.domain.smoke

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LatencySmokeTest {
    @Test
    fun runsThreeFixedSentencesWithMocks() = runTest {
        val report = LatencySmoke.run(sentences = LatencySmoke.FIXED_SENTENCES)
        assertThat(report.turns).hasSize(3)
        report.turns.forEach { turn ->
            assertThat(turn.asrText).isNotEmpty()
            assertThat(turn.llmReply).isNotEmpty()
            assertThat(turn.latency.asrToTtsMs).isNotNull()
            assertThat(turn.latency.asrToTtsMs!!).isAtLeast(0)
        }
        assertThat(report.p50AsrToTtsMs).isNotNull()
    }
}
