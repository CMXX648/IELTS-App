package com.voxcoach.core.domain.ev

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * M3 HINT policy + parser (docs/01 EV-07, docs/03 §4.4, docs/06 M3).
 *
 * Cost control: HINT reuses the same streaming dialogue response through a
 * trailing `hint` marker line — no extra LLM call. UI keeps HINT off by
 * default, shows at most one card per turn for ≤10 s, and can collect it
 * into the mistake vault.
 *
 * Pure JVM logic: clock is injected so unit tests stay deterministic.
 */
object HintPolicy {
    /** EV-07 default: HINT must stay off unless the user enables it. */
    const val DEFAULT_ENABLED = false

    /** Docs/03 §4.4: surface within 0.8 s after the turn ends. */
    const val SHOW_DELAY_MS = 800L

    /** Docs/03 §4.4: auto-dismiss after 10 s. */
    const val AUTO_DISMISS_MS = 10_000L

    /** Streaming marker: last non-blank line starting with this prefix carries the hint. */
    const val STREAM_MARKER = "HINT:"

    /** Max visible chars for the floating HINT card. */
    const val MAX_TEXT_CHARS = 120
}

data class HintCard(
    val text: String,
    val shownAtMs: Long,
    val expiresAtMs: Long = shownAtMs + HintPolicy.AUTO_DISMISS_MS,
) {
    fun isExpired(nowMs: Long): Boolean = nowMs >= expiresAtMs
}

object HintParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Split streamed dialogue text into (reply, hint). The hint is the last
     * non-blank `HINT:` line; everything else stays as the spoken reply.
     */
    fun splitReply(fullText: String): Pair<String, String?> {
        val lines = fullText.lines()
        var hintIndex = -1
        for (i in lines.indices.reversed()) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            if (line.startsWith(HintPolicy.STREAM_MARKER, ignoreCase = true)) {
                hintIndex = i
            }
            break
        }
        if (hintIndex < 0) return fullText.trimEnd() to null
        val rawHint = lines[hintIndex].trim()
            .substring(HintPolicy.STREAM_MARKER.length).trim()
        val reply = (lines.subList(0, hintIndex) + lines.subList(hintIndex + 1, lines.size))
            .joinToString("\n").trimEnd()
        val hint = normalize(rawHint) ?: return reply to null
        return reply to hint
    }

    /**
     * Parse the one-shot `complete()` HINT payload used only as a fallback
     * when the streaming marker is absent: `{"hint":"..."}` (lenient).
     */
    fun parseFallbackJson(raw: String): String? {
        val cleaned = runCatching { EvJsonParser.extractJsonObject(raw) }.getOrNull() ?: return null
        val dto = runCatching { json.decodeFromString(HintDto.serializer(), cleaned) }.getOrNull()
            ?: return null
        return normalize(dto.hint)
    }

    fun buildCard(text: String, nowMs: Long): HintCard? {
        val normalized = normalize(text) ?: return null
        return HintCard(text = normalized, shownAtMs = nowMs)
    }

    private fun normalize(raw: String): String? {
        val collapsed = raw.replace("\\s+".toRegex(), " ").trim()
            .trim('"', '\'')
            .trim()
        if (collapsed.isEmpty()) return null
        return if (collapsed.length > HintPolicy.MAX_TEXT_CHARS) {
            collapsed.take(HintPolicy.MAX_TEXT_CHARS).trimEnd() + "…"
        } else {
            collapsed
        }
    }

    @Serializable
    internal data class HintDto(val hint: String = "")
}
