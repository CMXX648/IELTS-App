package com.voxcoach.core.domain.model

/** Aligns with docs/04 §6.1 Room entities (M2 slice). */

enum class SessionType { CONVERSATION, DRILL }

enum class SessionSubtype { FREE, P1, P2, P3, MOCK }

enum class SessionStatus { ACTIVE, ENDED, EVALUATING, DONE }

enum class TurnRole { USER, AI }

enum class TextSource { ASR_RAW, EDITED }

enum class MistakeStatus { OPEN, MASTERED }

data class Topic(
    val id: String,
    val code: String,
    val title: String,
    val titleZh: String,
    val group: String = "part1",
)

data class Session(
    val id: String,
    val type: SessionType = SessionType.CONVERSATION,
    val subtype: SessionSubtype = SessionSubtype.FREE,
    val topicId: String?,
    val stage: String? = "S0",
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMs: Long = 0,
    val turnCount: Int = 0,
    val audioPath: String? = null,
    val evId: String? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val dirty: Boolean = true,
    val updatedAt: Long = startedAt,
    val deleted: Boolean = false,
)

data class Turn(
    val id: String,
    val sessionId: String,
    val role: TurnRole,
    val text: String,
    val textSource: TextSource = TextSource.ASR_RAW,
    val seq: Int,
    val partialMs: Long? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val llmMetaJson: String? = null,
)

data class DimScore(
    val score: Double,
    val comment: String = "",
    val evidence: List<String> = emptyList(),
)

data class BandDims(
    val fc: DimScore,
    val lr: DimScore,
    val gra: DimScore,
    val p: DimScore,
)

data class EvResult(
    val id: String,
    val sessionId: String,
    val overallBand: Double,
    val dims: BandDims,
    val items: List<FeedbackItem>,
    val highlights: List<Highlight>,
    val engineVer: String = "mock",
    val promptVer: String = "ev.v1",
    val createdAt: Long,
)

data class FeedbackItem(
    val id: String,
    val evId: String,
    val dimension: String,
    val category: String,
    val quote: String,
    val correction: String,
    val why: String,
    val model: List<String> = emptyList(),
    val tRange: List<Long>? = null,
    val collectedToMistakeAt: Long? = null,
)

data class Highlight(
    val dim: String,
    val quote: String,
    val note: String = "",
)

data class Mistake(
    val id: String,
    val sourceItemId: String?,
    val sessionId: String?,
    val dimension: String,
    val quote: String,
    val correction: String,
    val grammarPointId: String? = null,
    val status: MistakeStatus = MistakeStatus.OPEN,
    val retriedCount: Int = 0,
    val lastRetriedAt: Long? = null,
    val createdAt: Long,
)

data class UserProfile(
    val version: Int = 1,
    val stage: String = "S0",
    val targetBand: Double = 7.5,
    val dimTrendCacheJson: String? = null,
    val streak: Int = 0,
    val totalDurationMs: Long = 0,
    val totalTurnCount: Int = 0,
    val totalSessionCount: Int = 0,
    val updatedAt: Long = 0,
)

data class TodayStats(
    val durationMs: Long,
    val turnCount: Int,
    val sessionCount: Int,
)

/** Last-turn latency chips (ASR final → LLM first token → TTS start). */
data class TurnLatency(
    val asrFinalAt: Long = 0,
    val llmFirstTokenAt: Long = 0,
    val ttsStartAt: Long = 0,
) {
    val asrToLlmMs: Long?
        get() = if (asrFinalAt > 0 && llmFirstTokenAt > 0) llmFirstTokenAt - asrFinalAt else null
    val llmToTtsMs: Long?
        get() = if (llmFirstTokenAt > 0 && ttsStartAt > 0) ttsStartAt - llmFirstTokenAt else null
    val asrToTtsMs: Long?
        get() = if (asrFinalAt > 0 && ttsStartAt > 0) ttsStartAt - asrFinalAt else null
}
