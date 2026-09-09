package com.voxcoach.core.domain.llm

import com.voxcoach.core.domain.model.ChatDelta
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.ChatResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Injectable mock LLM that streams a fixed examiner reply,
 * and returns a valid EV JSON payload from [complete] when the prompt looks like evaluation.
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

    override suspend fun complete(request: ChatRequest): ChatResult {
        val joined = request.messages.joinToString("\n") { it.content }
        val isJudge = joined.contains("Judge now") ||
            joined.contains("Target grammar") ||
            request.messages.any { it.role.name == "SYSTEM" && it.content.contains("grammar coach") }
        val isEv = joined.contains("schemaVer") ||
            joined.contains("Evaluate now") ||
            joined.contains("overallBand") ||
            request.messages.any { it.role.name == "SYSTEM" && it.content.contains("Score the candidate") }
        return when {
            isJudge -> {
                val miss = joined.contains("I go yesterday") || joined.contains("FORCE_MISS")
                ChatResult(
                    content = if (miss) SAMPLE_JUDGE_MISS else SAMPLE_JUDGE_HIT,
                    finishReason = "stop",
                )
            }
            isEv -> ChatResult(content = SAMPLE_EV_JSON, finishReason = "stop")
            else -> ChatResult(content = reply, finishReason = "stop")
        }
    }

    companion object {
        val SAMPLE_JUDGE_HIT = """
{
  "hit": true,
  "correction": "",
  "why": "结构命中，时态与虚拟一致。",
  "model": "If I had more free time, I would travel more often."
}
""".trimIndent()

        val SAMPLE_JUDGE_MISS = """
{
  "hit": false,
  "correction": "If I had prepared earlier, I would have felt calmer.",
  "why": "目标是第三条件句（If + had + V3, would have + V3），原句未使用该结构。",
  "model": "If I had prepared earlier, I would have felt calmer."
}
""".trimIndent()

        val SAMPLE_EV_JSON = """
{
  "schemaVer": "ev.v1",
  "engine": { "model": "mock", "promptVer": "rubric-2026.09" },
  "overallBand": 6.5,
  "dims": {
    "fc":  { "score": 6.5, "comment": "Generally coherent with some hesitation.", "evidence": ["I come from a small town"] },
    "lr":  { "score": 6.5, "comment": "Adequate range for familiar topics.", "evidence": ["famous for its seafood"] },
    "gra": { "score": 6.0, "comment": "Mostly simple structures; occasional errors.", "evidence": ["I enjoy practising English every day"] },
    "p":   { "score": 7.0, "comment": "Clear enough; mock has no audio — score inferred from text.", "evidence": [] }
  },
  "items": [
    {
      "id": "i_01",
      "dim": "gra",
      "category": "grammar_tense",
      "quote": "I enjoy practising English every day.",
      "correction": "I practise English every day.",
      "why": "Prefer the verb 'practise' for the habit; 'enjoy practising' is fine but simpler is clearer under time pressure.",
      "model": ["I practise speaking English every day."]
    }
  ],
  "highlights": [
    { "dim": "lr", "quote": "famous for its seafood and friendly people", "note": "Natural collocation." }
  ]
}
""".trimIndent()
    }
}
