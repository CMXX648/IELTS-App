package com.voxcoach.core.domain.gr

import com.google.common.truth.Truth.assertThat
import com.voxcoach.core.domain.llm.MockLlmClient
import org.junit.Test

class GrammarJudgeParserTest {
    @Test
    fun parsesHitJson() {
        val result = GrammarJudgeParser.parse(MockLlmClient.SAMPLE_JUDGE_HIT)
        assertThat(result.hit).isTrue()
        assertThat(result.why).contains("命中")
        assertThat(result.model).isNotEmpty()
    }

    @Test
    fun parsesMissJson() {
        val result = GrammarJudgeParser.parse(MockLlmClient.SAMPLE_JUDGE_MISS)
        assertThat(result.hit).isFalse()
        assertThat(result.correction).contains("If I had")
        assertThat(result.why).isNotEmpty()
    }

    @Test
    fun stripsMarkdownFence() {
        val fenced = "```json\n${MockLlmClient.SAMPLE_JUDGE_HIT}\n```"
        val result = GrammarJudgeParser.parse(fenced)
        assertThat(result.hit).isTrue()
    }

    @Test
    fun toleratesProsePrefix() {
        val raw = "Sure.\n" + MockLlmClient.SAMPLE_JUDGE_MISS
        val result = GrammarJudgeParser.parse(raw)
        assertThat(result.hit).isFalse()
    }
}
