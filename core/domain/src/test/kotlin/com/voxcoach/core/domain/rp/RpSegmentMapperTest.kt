package com.voxcoach.core.domain.rp

import com.google.common.truth.Truth.assertThat
import com.voxcoach.core.domain.model.FeedbackItem
import com.voxcoach.core.domain.model.TextSource
import com.voxcoach.core.domain.model.Turn
import com.voxcoach.core.domain.model.TurnRole
import org.junit.Test

class RpSegmentMapperTest {
    private fun turn(id: String, seq: Int, start: Long?, end: Long?) = Turn(
        id = id,
        sessionId = "s1",
        role = TurnRole.USER,
        text = "text $seq",
        textSource = TextSource.ASR_RAW,
        seq = seq,
        startMs = start,
        endMs = end,
    )

    private fun item(id: String, start: Long, end: Long) = FeedbackItem(
        id = id,
        evId = "ev1",
        dimension = "gra",
        category = "grammar",
        quote = "q",
        correction = "c",
        why = "w",
        tRange = listOf(start, end),
    )

    @Test
    fun sentences_linkFeedbackByExactRange() {
        val out = RpSegmentMapper.sentences(
            turns = listOf(turn("t1", 1, 0, 1_000), turn("t2", 2, 1_000, 2_500)),
            feedbackItems = listOf(item("i1", 1_000, 2_500)),
        )
        assertThat(out).hasSize(2)
        assertThat(out[0].feedbackItemId).isNull()
        assertThat(out[1].feedbackItemId).isEqualTo("i1")
    }

    @Test
    fun sentences_skipTurnsWithoutWindow() {
        val out = RpSegmentMapper.sentences(
            turns = listOf(turn("t1", 1, null, null), turn("t2", 2, 0, 500)),
        )
        assertThat(out.map { it.turnId }).containsExactly("t2")
    }

    @Test
    fun clampLoop_rejectsTooShortAndClampsBounds() {
        assertThat(RpSegmentMapper.clampLoop(100, 200, 10_000)).isNull()
        val loop = RpSegmentMapper.clampLoop(-100, 50_000, 10_000)!!
        assertThat(loop.startMs).isEqualTo(0L)
        assertThat(loop.endMs).isEqualTo(10_000L)
        assertThat(RpSegmentMapper.clampLoop(0, 10_000, 0)).isNull()
    }

    @Test
    fun sentenceAt_returnsCurrentSentence() {
        val sentences = RpSegmentMapper.sentences(
            turns = listOf(turn("t1", 1, 0, 1_000), turn("t2", 2, 1_000, 2_000)),
        )
        assertThat(RpSegmentMapper.sentenceAt(sentences, 1_500)?.turnId).isEqualTo("t2")
        assertThat(RpSegmentMapper.sentenceAt(sentences, 0)?.turnId).isEqualTo("t1")
    }
}
