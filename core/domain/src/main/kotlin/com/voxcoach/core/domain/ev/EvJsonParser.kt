package com.voxcoach.core.domain.ev

import com.voxcoach.core.domain.model.BandDims
import com.voxcoach.core.domain.model.DimScore
import com.voxcoach.core.domain.model.EvResult
import com.voxcoach.core.domain.model.FeedbackItem
import com.voxcoach.core.domain.model.Highlight
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses EV JSON Schema payload (docs/05 §4.4) into domain [EvResult] + items.
 */
object EvJsonParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(raw: String, sessionId: String, evId: String = UUID.randomUUID().toString()): EvResult {
        val cleaned = extractJsonObject(raw)
        val dto = json.decodeFromString(EvPayloadDto.serializer(), cleaned)
        val items = dto.items.mapIndexed { index, item ->
            FeedbackItem(
                id = item.id?.ifBlank { null } ?: "fi_${evId}_$index",
                evId = evId,
                dimension = item.dim.ifBlank { item.dimension.orEmpty() },
                category = item.category,
                quote = item.quote,
                correction = item.correction,
                why = item.why,
                model = item.model.orEmpty(),
                tRange = item.tRange,
            )
        }
        val highlights = dto.highlights.orEmpty().map {
            Highlight(dim = it.dim, quote = it.quote, note = it.note.orEmpty())
        }
        val dims = BandDims(
            fc = dto.dims.fc.toDomain(),
            lr = dto.dims.lr.toDomain(),
            gra = dto.dims.gra.toDomain(),
            p = dto.dims.p.toDomain(),
        )
        return EvResult(
            id = evId,
            sessionId = sessionId,
            overallBand = dto.overallBand,
            dims = dims,
            items = items,
            highlights = highlights,
            engineVer = dto.engine?.model ?: "unknown",
            // Prefer engine.promptVer (rubric id); schemaVer is the JSON contract id.
            promptVer = dto.engine?.promptVer ?: dto.schemaVer ?: "ev.v1",
            createdAt = System.currentTimeMillis(),
        )
    }

    /** Strip markdown fences / leading prose if the model wraps JSON. */
    fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val fenced = fence.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val candidate = fenced ?: trimmed
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        require(start >= 0 && end > start) { "No JSON object found in EV response" }
        return candidate.substring(start, end + 1)
    }

    @Serializable
    data class EvPayloadDto(
        val schemaVer: String? = null,
        val engine: EngineDto? = null,
        val overallBand: Double,
        val dims: DimsDto,
        val items: List<ItemDto> = emptyList(),
        val highlights: List<HighlightDto>? = null,
    )

    @Serializable
    data class EngineDto(
        val model: String? = null,
        val promptVer: String? = null,
    )

    @Serializable
    data class DimsDto(
        val fc: DimDto,
        val lr: DimDto,
        val gra: DimDto,
        val p: DimDto,
    )

    @Serializable
    data class DimDto(
        val score: Double,
        val comment: String = "",
        val evidence: List<String> = emptyList(),
    ) {
        fun toDomain() = DimScore(score = score, comment = comment, evidence = evidence)
    }

    @Serializable
    data class ItemDto(
        val id: String? = null,
        val dim: String = "",
        val dimension: String? = null,
        val category: String = "general",
        val quote: String = "",
        val correction: String = "",
        val why: String = "",
        val model: List<String>? = null,
        val tRange: List<Long>? = null,
    )

    @Serializable
    data class HighlightDto(
        val dim: String = "",
        val quote: String = "",
        val note: String? = null,
    )
}

object EvPrompt {
    val SYSTEM = """
You are an IELTS Speaking examiner. Score the candidate on FC, LR, GRA, P (0–9, half bands).
Return ONLY a JSON object matching schemaVer "ev.v1":
{
  "schemaVer": "ev.v1",
  "engine": { "model": "<model>", "promptVer": "rubric-2026.09" },
  "overallBand": 6.5,
  "dims": {
    "fc":  { "score": 6.5, "comment": "...", "evidence": ["..."] },
    "lr":  { "score": 6.5, "comment": "...", "evidence": ["..."] },
    "gra": { "score": 6.0, "comment": "...", "evidence": ["..."] },
    "p":   { "score": 7.0, "comment": "...", "evidence": ["..."] }
  },
  "items": [
    { "id": "i_01", "dim": "gra", "category": "grammar",
      "quote": "...", "correction": "...", "why": "...", "model": ["..."] }
  ],
  "highlights": [ { "dim": "lr", "quote": "...", "note": "..." } ]
}
Use half-band scores. Include at least one item and one highlight when possible.
""".trimIndent()

    fun userTranscript(topicTitle: String, turns: List<Pair<String, String>>): String {
        val body = turns.joinToString("\n") { (role, text) -> "$role: $text" }
        return "Topic: $topicTitle\n\nTranscript:\n$body\n\nEvaluate now. JSON only."
    }
}
