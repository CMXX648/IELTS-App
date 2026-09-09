package com.voxcoach.core.domain.ev

import com.voxcoach.core.domain.llm.LlmClient
import com.voxcoach.core.domain.model.ChatDelta
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.ChatResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Injectable LLM that returns each calibration fixture's canned EV JSON.
 * Matching uses candidate utterance snippets embedded in the ChatRequest.
 */
class FixtureBackedLlmClient(
    private val fixtures: List<EvCalibrationFixture> = EvCalibrationCatalog.load(),
) : LlmClient {
    override fun streamChat(request: ChatRequest): Flow<ChatDelta> {
        val content = completeSync(request)
        return flowOf(ChatDelta(content = content, finishReason = "stop"))
    }

    override suspend fun complete(request: ChatRequest): ChatResult =
        ChatResult(content = completeSync(request), finishReason = "stop")

    private fun completeSync(request: ChatRequest): String {
        val joined = request.messages.joinToString("\n") { it.content }
        val match = fixtures.firstOrNull { fixture ->
            val key = fixture.matchKey()
            key.isNotBlank() && joined.contains(key.take(MATCH_CHARS))
        } ?: fixtures.firstOrNull { fixture ->
            joined.contains(fixture.id) || joined.contains(fixture.topicTitle)
        }
        return match?.llmResponseJson
            ?: error("No calibration fixture matched ChatRequest (fixtures=${fixtures.size})")
    }

    companion object {
        private const val MATCH_CHARS = 48
    }
}
