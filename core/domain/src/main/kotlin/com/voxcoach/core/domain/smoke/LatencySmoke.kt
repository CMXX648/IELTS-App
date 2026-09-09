package com.voxcoach.core.domain.smoke

import com.voxcoach.core.domain.llm.LlmClient
import com.voxcoach.core.domain.llm.MockLlmClient
import com.voxcoach.core.domain.model.AsrSessionConfig
import com.voxcoach.core.domain.model.ChatMessage
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.Sentence
import com.voxcoach.core.domain.model.TurnLatency
import com.voxcoach.core.domain.speech.AsrEngine
import com.voxcoach.core.domain.speech.MockAsrEngine
import com.voxcoach.core.domain.speech.MockTtsEngine
import com.voxcoach.core.domain.speech.TtsEngine
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * M1 closeout smoke: 3 fixed English sentences through Mock ASR→LLM→TTS,
 * recording ASR final → LLM first token → TTS start timings.
 */
object LatencySmoke {
    val FIXED_SENTENCES = listOf(
        "I come from a small town near the coast.",
        "My hometown is famous for its seafood and friendly people.",
        "I would like to improve my fluency for the IELTS speaking test.",
    )

    data class TurnReport(
        val sentence: String,
        val asrText: String,
        val llmReply: String,
        val latency: TurnLatency,
    )

    data class Report(
        val turns: List<TurnReport>,
        val p50AsrToTtsMs: Long?,
        val p90AsrToTtsMs: Long?,
    ) {
        fun toMarkdown(): String = buildString {
            appendLine("# LatencySmoke report")
            appendLine()
            turns.forEachIndexed { i, t ->
                appendLine("## Turn ${i + 1}")
                appendLine("- input: ${t.sentence}")
                appendLine("- asr: ${t.asrText}")
                appendLine("- llm: ${t.llmReply.take(120)}")
                appendLine("- ASR→LLM首字: ${t.latency.asrToLlmMs} ms")
                appendLine("- LLM→TTS起播: ${t.latency.llmToTtsMs} ms")
                appendLine("- ASR→TTS总: ${t.latency.asrToTtsMs} ms")
                appendLine()
            }
            appendLine("p50 ASR→TTS: $p50AsrToTtsMs ms")
            appendLine("p90 ASR→TTS: $p90AsrToTtsMs ms")
        }
    }

    suspend fun run(
        asrFactory: (String) -> AsrEngine = { MockAsrEngine(it) },
        llm: LlmClient = MockLlmClient(),
        tts: TtsEngine = MockTtsEngine(),
        sentences: List<String> = FIXED_SENTENCES,
    ): Report {
        val turns = mutableListOf<TurnReport>()
        for (sentence in sentences) {
            val asr = asrFactory(sentence)
            var asrFinalAt = 0L
            val asrText = coroutineScope {
                val collect = async {
                    val final = asr.finals.first()
                    asrFinalAt = System.currentTimeMillis()
                    final.text
                }
                asr.start(AsrSessionConfig())
                collect.await()
            }
            asr.stop()

            var llmFirst = 0L
            val sb = StringBuilder()
            val request = ChatRequest(
                model = "mock",
                messages = listOf(
                    ChatMessage(ChatMessage.Role.SYSTEM, "You are an IELTS examiner."),
                    ChatMessage(ChatMessage.Role.USER, asrText),
                ),
                stream = true,
            )
            llm.streamChat(request).collect { delta ->
                if (delta.content.isNotEmpty()) {
                    if (llmFirst == 0L) llmFirst = System.currentTimeMillis()
                    sb.append(delta.content)
                }
            }
            val reply = sb.toString().trim()
            val ttsStart = System.currentTimeMillis()
            tts.speak(Sentence(reply))
            turns += TurnReport(
                sentence = sentence,
                asrText = asrText,
                llmReply = reply,
                latency = TurnLatency(
                    asrFinalAt = asrFinalAt,
                    llmFirstTokenAt = llmFirst,
                    ttsStartAt = ttsStart,
                ),
            )
        }
        val totals = turns.mapNotNull { it.latency.asrToTtsMs }.sorted()
        return Report(
            turns = turns,
            p50AsrToTtsMs = percentile(totals, 0.50),
            p90AsrToTtsMs = percentile(totals, 0.90),
        )
    }

    fun writeReport(report: Report, dir: File): File {
        dir.mkdirs()
        val out = File(dir, "latency-smoke-${System.currentTimeMillis()}.md")
        out.writeText(report.toMarkdown())
        return out
    }

    private fun percentile(sorted: List<Long>, p: Double): Long? {
        if (sorted.isEmpty()) return null
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }
}
