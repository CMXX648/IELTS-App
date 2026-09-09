package com.voxcoach.core.domain.ev

import com.google.common.truth.Truth.assertThat
import com.voxcoach.core.domain.llm.MockLlmClient
import org.junit.Test

class EvJsonParserTest {
    @Test
    fun parsesSampleEvJson() {
        val result = EvJsonParser.parse(MockLlmClient.SAMPLE_EV_JSON, sessionId = "s1", evId = "ev1")
        assertThat(result.overallBand).isEqualTo(6.5)
        assertThat(result.dims.fc.score).isEqualTo(6.5)
        assertThat(result.dims.gra.score).isEqualTo(6.0)
        assertThat(result.items).isNotEmpty()
        assertThat(result.items.first().correction).contains("practise")
        assertThat(result.highlights).isNotEmpty()
        assertThat(result.id).isEqualTo("ev1")
        assertThat(result.sessionId).isEqualTo("s1")
    }

    @Test
    fun stripsMarkdownFence() {
        val fenced = "Here you go:\n```json\n${MockLlmClient.SAMPLE_EV_JSON}\n```\n"
        val result = EvJsonParser.parse(fenced, "s2")
        assertThat(result.overallBand).isEqualTo(6.5)
    }

    @Test
    fun extractJsonObjectFindsBraces() {
        val raw = "prefix {\"a\":1} suffix"
        assertThat(EvJsonParser.extractJsonObject(raw)).isEqualTo("{\"a\":1}")
    }
}
