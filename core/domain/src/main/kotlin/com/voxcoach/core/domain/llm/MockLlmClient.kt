package com.voxcoach.core.domain.llm

import com.voxcoach.core.domain.model.ChatDelta
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.ChatResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Injectable mock LLM that streams a fixed examiner reply.
 */
class MockLlmClient(
    private val reply: String = "That's interesting. Could you tell me more about your daily routine?",
) : LlmClient {
    override fun streamChat(request: ChatRequest): Flow<ChatDelta> = flow {
        val chunks = reply.chunked(12)
        for ((i, c) in chunks.withIndex()) {
            delay(30)
            emit(ChatDelta(c, finishReason = if (i == chunks.lastIndex) "stop" else null))
        }
    }

    override suspend fun complete(request: ChatRequest): ChatResult =
        ChatResult(content = reply, finishReason = "stop")
}
