package com.voxcoach.core.domain.gr

/**
 * M3 GR-05 shadowing pre-check + judge prompt (docs/01 GR-05, docs/02 §P1–P3, docs/06 M3).
 *
 * Shadowing = TTS plays the model sentence → user shadows it → ASR text is
 * compared. This file is the offline rule screen plus the small LLM judge
 * prompt (≤400 token/utterance budget per docs/03 §3.4). It deliberately
 * returns an *auxiliary P hint*, never a precise phoneme score — ASR text
 * alone cannot measure pronunciation reliably.
 */
object ShadowingPolicy {
    /** Model sentence length guard so TTS + ASR stay in one short utterance. */
    const val MAX_MODEL_CHARS = 160

    /** Min overlap to call the shadow attempt "on track" before LLM judging. */
    const val MIN_WORD_OVERLAP = 0.55

    /** Min word count so single-word echoes do not count as shadowing. */
    const val MIN_WORDS = 3
}

data class ShadowingPrescreen(
    /** Word overlap ratio in [0,1] between model and shadowed text. */
    val overlap: Double,
    val modelWords: Int,
    val spokenWords: Int,
    val worthLlmJudge: Boolean,
)

object ShadowingJudge {
    fun prescreen(modelSentence: String, shadowedText: String): ShadowingPrescreen {
        val modelWords = words(modelSentence)
        val spokenWords = words(shadowedText)
        if (modelWords.size < ShadowingPolicy.MIN_WORDS || spokenWords.isEmpty()) {
            return ShadowingPrescreen(
                overlap = 0.0,
                modelWords = modelWords.size,
                spokenWords = spokenWords.size,
                worthLlmJudge = false,
            )
        }
        val spokenSet = spokenWords.toSet()
        val hits = modelWords.count { it in spokenSet }
        val overlap = hits.toDouble() / modelWords.size.toDouble()
        return ShadowingPrescreen(
            overlap = overlap,
            modelWords = modelWords.size,
            spokenWords = spokenWords.size,
            worthLlmJudge = overlap >= ShadowingPolicy.MIN_WORD_OVERLAP,
        )
    }

    fun buildJudgePrompt(modelSentence: String, shadowedText: String): String = buildString {
        appendLine("Model sentence: $modelSentence")
        appendLine("Shadowed speech (ASR text): $shadowedText")
        append("Judge now. JSON only.")
    }

    val SYSTEM = """
You are an IELTS speaking shadowing coach. Compare the learner's shadowed speech
(ASR text, may contain recognition noise) against the model sentence.
Return ONLY a compact JSON object:
{
  "hit": true|false,
  "correction": "closest fluent version of the model sentence (empty if hit)",
  "why": "≤2 short lines on rhythm/chunking/word stress cues (Chinese or English ok)",
  "model": "the model sentence to shadow again"
}
Be tolerant of minor ASR noise; fail only on clearly missed content words or broken rhythm.
""".trimIndent()

    private fun words(text: String): List<String> =
        text.lowercase()
            .replace("[^a-z'\\s]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() && it.length > 1 }
}
