package com.voxcoach.core.domain.gr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShadowingJudgeTest {
    @Test
    fun prescreen_closeShadow_isWorthJudge() {
        val result = ShadowingJudge.prescreen(
            modelSentence = "Walking along the river, I felt relaxed.",
            shadowedText = "walking along the river I felt relaxed",
        )
        assertThat(result.worthLlmJudge).isTrue()
        assertThat(result.overlap).isAtLeast(ShadowingPolicy.MIN_WORD_OVERLAP)
    }

    @Test
    fun prescreen_offTopic_isNotWorthJudge() {
        val result = ShadowingJudge.prescreen(
            modelSentence = "Walking along the river, I felt relaxed.",
            shadowedText = "I like pizza very much today",
        )
        assertThat(result.worthLlmJudge).isFalse()
    }

    @Test
    fun prescreen_tooShort_isNotWorthJudge() {
        val result = ShadowingJudge.prescreen(
            modelSentence = "Walking along the river, I felt relaxed.",
            shadowedText = "relaxed",
        )
        assertThat(result.worthLlmJudge).isFalse()
    }

    @Test
    fun judgePrompt_embedsBothSides() {
        val prompt = ShadowingJudge.buildJudgePrompt("model one", "shadow one")
        assertThat(prompt).contains("model one")
        assertThat(prompt).contains("shadow one")
    }
}
