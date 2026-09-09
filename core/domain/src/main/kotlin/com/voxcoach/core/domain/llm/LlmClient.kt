package com.voxcoach.core.domain.llm

import com.voxcoach.core.domain.model.ChatDelta
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.ChatResult
import kotlinx.coroutines.flow.Flow

interface LlmClient {
    fun streamChat(request: ChatRequest): Flow<ChatDelta>
    suspend fun complete(request: ChatRequest): ChatResult
}
