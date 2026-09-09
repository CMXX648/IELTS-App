package com.voxcoach.core.domain.gr

import com.voxcoach.core.domain.ev.EvJsonParser
import com.voxcoach.core.domain.model.DrillJudgeResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses GR-03 LLM JSON judge payload: hit/miss + correction/why.
 */
object GrammarJudgeParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(raw: String): DrillJudgeResult {
        val cleaned = EvJsonParser.extractJsonObject(raw)
        val dto = json.decodeFromString(JudgeDto.serializer(), cleaned)
        return DrillJudgeResult(
            hit = dto.hit,
            correction = dto.correction.trim(),
            why = dto.why.trim(),
            model = dto.model?.trim().orEmpty(),
        )
    }

    @Serializable
    data class JudgeDto(
        val hit: Boolean,
        val correction: String = "",
        val why: String = "",
        val model: String? = null,
    )
}

object GrammarJudgePrompt {
    val SYSTEM = """
You are an IELTS speaking grammar coach. Judge whether the candidate's spoken sentence
hits the target grammar pattern. Return ONLY a compact JSON object:
{
  "hit": true|false,
  "correction": "corrected English sentence (empty if hit)",
  "why": "≤2 short lines explaining the structure miss or praise (Chinese or English ok)",
  "model": "one model sentence using the target pattern"
}
Be strict on the target structure but tolerant of minor ASR noise.
""".trimIndent()

    fun userPrompt(
        title: String,
        rule: String,
        skeleton: String,
        topicHint: String,
        userSentence: String,
    ): String = buildString {
        appendLine("Target grammar: $title")
        appendLine("Rule: $rule")
        appendLine("Skeleton: $skeleton")
        appendLine("Topic hint: $topicHint")
        appendLine("Candidate sentence: $userSentence")
        append("Judge now. JSON only.")
    }
}
