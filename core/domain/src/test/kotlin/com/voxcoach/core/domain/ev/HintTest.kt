package com.voxcoach.core.domain.ev

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HintTest {
    @Test
    fun splitReply_extractsTrailingHint() {
        val (reply, hint) = HintParser.splitReply(
            "That's a good start. Tell me more.\nHINT: try linking with 'because'.",
        )
        assertThat(reply).isEqualTo("That's a good start. Tell me more.")
        assertThat(hint).isEqualTo("try linking with 'because'.")
    }

    @Test
    fun splitReply_withoutMarker_returnsNullHint() {
        val (reply, hint) = HintParser.splitReply("Just a normal reply.")
        assertThat(reply).isEqualTo("Just a normal reply.")
        assertThat(hint).isNull()
    }

    @Test
    fun splitReply_blankHint_isIgnored() {
        val (reply, hint) = HintParser.splitReply("Reply text\nHINT:   ")
        assertThat(reply).isEqualTo("Reply text")
        assertThat(hint).isNull()
    }

    @Test
    fun splitReply_longHint_isTruncated() {
        val long = "HINT: " + "word ".repeat(40)
        val (_, hint) = HintParser.splitReply("Reply\n$long")
        assertThat(hint).isNotNull()
        assertThat(hint!!.length).isAtMost(HintPolicy.MAX_TEXT_CHARS + 1)
    }

    @Test
    fun fallbackJson_parsesHintObject() {
        assertThat(HintParser.parseFallbackJson("{\"hint\":\"watch your tense\"}"))
            .isEqualTo("watch your tense")
        assertThat(HintParser.parseFallbackJson("no json here")).isNull()
        assertThat(HintParser.parseFallbackJson("{\"hint\":\"\"}")).isNull()
    }

    @Test
    fun card_expiryFollowsPolicy() {
        val card = HintParser.buildCard("link with because", nowMs = 1_000L)!!
        assertThat(card.expiresAtMs).isEqualTo(1_000L + HintPolicy.AUTO_DISMISS_MS)
        assertThat(card.isExpired(1_000L + HintPolicy.AUTO_DISMISS_MS - 1)).isFalse()
        assertThat(card.isExpired(1_000L + HintPolicy.AUTO_DISMISS_MS)).isTrue()
        assertThat(HintPolicy.DEFAULT_ENABLED).isFalse()
        assertThat(HintPolicy.SHOW_DELAY_MS).isEqualTo(800L)
    }
}
