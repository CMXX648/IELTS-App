package com.voxcoach.core.domain.ev

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Bundled EV calibration set (docs/04 §8, docs/06 M2): 8 synthetic IELTS-like
 * dialogues with human half-band labels. Original content — not Cambridge copies.
 */
object EvCalibrationCatalog {
    const val FIXTURE_COUNT = 8
    const val RESOURCE_DIR = "ev_calibration"
    const val PROMPT_VER = "rubric-2026.09"
    const val SCHEMA_VER = "ev.v1"
    /** M2 acceptance: per-dimension absolute delta vs human labels. */
    const val MAX_ABS_DELTA = 0.5

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    private val resourceNames = listOf(
        "cal-01-low-band.json",
        "cal-02-mid-low.json",
        "cal-03-mid-band.json",
        "cal-04-hometown-65.json",
        "cal-05-band70.json",
        "cal-06-high-75.json",
        "cal-07-mixed-dims.json",
        "cal-08-high-band.json",
    )

    fun load(classLoader: ClassLoader = EvCalibrationCatalog::class.java.classLoader): List<EvCalibrationFixture> {
        return resourceNames.map { name ->
            val path = "$RESOURCE_DIR/$name"
            val stream = classLoader.getResourceAsStream(path)
                ?: error("Missing EV calibration asset: $path")
            stream.use { input ->
                val text = input.bufferedReader(Charsets.UTF_8).readText()
                parseFixture(text)
            }
        }
    }

    fun parseFixture(raw: String): EvCalibrationFixture {
        val dto = json.decodeFromString(EvCalibrationFixtureDto.serializer(), raw)
        val llmJson = when (val el = dto.llmResponse) {
            is JsonObject -> json.encodeToString(JsonObject.serializer(), el)
            else -> error("Fixture ${dto.id}: llmResponse must be a JSON object")
        }
        return EvCalibrationFixture(
            id = dto.id,
            title = dto.title,
            topicTitle = dto.topicTitle,
            notes = dto.notes.orEmpty(),
            labels = BandLabels(
                fc = dto.labels.fc,
                lr = dto.labels.lr,
                gra = dto.labels.gra,
                p = dto.labels.p,
            ),
            turns = dto.turns.map { EvCalibrationTurn(role = it.role, text = it.text) },
            llmResponseJson = llmJson,
        )
    }

    fun byId(id: String, classLoader: ClassLoader = EvCalibrationCatalog::class.java.classLoader): EvCalibrationFixture =
        load(classLoader).firstOrNull { it.id == id }
            ?: error("Unknown calibration fixture id: $id")
}

data class BandLabels(
    val fc: Double,
    val lr: Double,
    val gra: Double,
    val p: Double,
) {
    fun asMap(): Map<String, Double> = mapOf(
        "fc" to fc,
        "lr" to lr,
        "gra" to gra,
        "p" to p,
    )
}

data class EvCalibrationTurn(
    val role: String,
    val text: String,
)

data class EvCalibrationFixture(
    val id: String,
    val title: String,
    val topicTitle: String,
    val notes: String,
    val labels: BandLabels,
    val turns: List<EvCalibrationTurn>,
    /** Canonical EV JSON the injectable LLM returns for offline regression. */
    val llmResponseJson: String,
) {
    fun transcriptPairs(): List<Pair<String, String>> =
        turns.map { it.role to it.text }

    /** Stable snippet used to match a ChatRequest back to this fixture. */
    fun matchKey(): String =
        turns.firstOrNull { it.role.equals("Candidate", ignoreCase = true) }?.text
            ?: turns.first().text
}

@Serializable
internal data class EvCalibrationFixtureDto(
    val id: String,
    val title: String,
    val topicTitle: String,
    val notes: String? = null,
    val labels: BandLabelsDto,
    val turns: List<TurnDto>,
    val llmResponse: JsonElement,
)

@Serializable
internal data class BandLabelsDto(
    val fc: Double,
    val lr: Double,
    val gra: Double,
    val p: Double,
)

@Serializable
internal data class TurnDto(
    val role: String,
    val text: String,
)
