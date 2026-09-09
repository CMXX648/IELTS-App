package com.voxcoach.core.domain

import com.google.common.truth.Truth.assertThat
import com.voxcoach.core.domain.llm.MockLlmClient
import com.voxcoach.core.domain.model.AsrSessionConfig
import com.voxcoach.core.domain.model.ChatMessage
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.Sentence
import com.voxcoach.core.domain.speech.MockAsrEngine
import com.voxcoach.core.domain.speech.MockTtsEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MockEnginesTest {
    @Test
    fun mockAsrEmitsFinal() = runTest(UnconfinedTestDispatcher()) {
        val asr = MockAsrEngine("Hello world")
        val finals = mutableListOf<String>()
        backgroundScope.launch { asr.finals.collect { finals += it.text } }
        asr.start(AsrSessionConfig())
        assertThat(finals).contains("Hello world")
    }

    @Test
    fun mockLlmStreamsReply() = runTest {
        val llm = MockLlmClient("OK.")
        val deltas = llm.streamChat(
            ChatRequest(
                model = "mock",
                messages = listOf(ChatMessage(ChatMessage.Role.USER, "hi")),
            ),
        ).toList()
        assertThat(deltas.joinToString("") { it.content }).isEqualTo("OK.")
    }

    @Test
    fun mockTtsRecordsSpeech() = runTest {
        val tts = MockTtsEngine()
        tts.speak(Sentence("Hi"))
        assertThat(tts.spoken).containsExactly("Hi")
    }
}
