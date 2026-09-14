package com.voxcoach.core.domain.rp

import com.voxcoach.core.domain.model.FeedbackItem
import com.voxcoach.core.domain.model.Turn

/**
 * M3 RP sentence-segment mapping (docs/03 §5, docs/04 §3.3, docs/06 M3).
 *
 * Session audio is one local wav; every user Turn carries session-relative
 * [Turn.startMs]/[Turn.endMs]. EV items carry [FeedbackItem.tRange]. This
 * mapper joins the two so replay can jump to a sentence, apply 0.75x/1x,
 * and clamp A-B loops — all pure math, no Android player here.
 */
object RpPolicy {
    const val SLOW_RATE = 0.75f
    const val NORMAL_RATE = 1.0f

    /** Min A-B loop length so accidental taps do not create zero ranges. */
    const val MIN_LOOP_MS = 300L
}

data class RpSentence(
    val turnId: String,
    val seq: Int,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val feedbackItemId: String? = null,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

data class RpLoop(
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = endMs - startMs
}

object RpSegmentMapper {
    /**
     * Build the sentence timeline from persisted turns. Turns without a valid
     * [startMs,endMs] window are skipped (recording may be incomplete after a crash).
     */
    fun sentences(
        turns: List<Turn>,
        feedbackItems: List<FeedbackItem> = emptyList(),
    ): List<RpSentence> {
        val itemByRange = feedbackItems.filter { (it.tRange?.size ?: 0) >= 2 }
            .associateBy { item -> item.tRange!![0] to item.tRange[1] }
        return turns.filter { it.startMs != null && it.endMs != null }
            .mapNotNull { turn ->
                val start = turn.startMs ?: return@mapNotNull null
                val end = turn.endMs ?: return@mapNotNull null
                if (end < start) return@mapNotNull null
                val linked = itemByRange[start to end]?.id
                    ?: feedbackItems.firstOrNull { contains(it.tRange, start, end) }?.id
                RpSentence(
                    turnId = turn.id,
                    seq = turn.seq,
                    text = turn.text,
                    startMs = start,
                    endMs = end,
                    feedbackItemId = linked,
                )
            }
            .sortedWith(compareBy({ it.startMs }, { it.seq }))
    }

    /** Clamp a user-picked A-B loop into [0, sessionDurationMs]. */
    fun clampLoop(aMs: Long, bMs: Long, sessionDurationMs: Long): RpLoop? {
        if (sessionDurationMs <= 0) return null
        val start = aMs.coerceIn(0L, sessionDurationMs)
        val end = bMs.coerceIn(0L, sessionDurationMs)
        if (end - start < RpPolicy.MIN_LOOP_MS) return null
        return RpLoop(startMs = start, endMs = end)
    }

    /** Next sentence at/after [positionMs] (for timeline scrubbing). */
    fun sentenceAt(sentences: List<RpSentence>, positionMs: Long): RpSentence? =
        sentences.lastOrNull { positionMs >= it.startMs }
            ?: sentences.firstOrNull()

    private fun contains(tRange: List<Long>?, start: Long, end: Long): Boolean {
        if (tRange == null || tRange.size < 2) return false
        return start >= tRange[0] && end <= tRange[1]
    }
}
