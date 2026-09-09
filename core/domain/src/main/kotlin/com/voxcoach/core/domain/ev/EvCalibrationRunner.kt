package com.voxcoach.core.domain.ev

import com.voxcoach.core.domain.llm.LlmClient
import com.voxcoach.core.domain.model.BandDims
import com.voxcoach.core.domain.model.ChatMessage
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.EvResult
import kotlin.math.abs

/**
 * Offline / live EV calibration runner (docs/04 §8).
 *
 * Default path is deterministic: an injectable [LlmClient] returns fixture JSON,
 * [EvJsonParser] builds [EvResult], then each dimension is checked against human
 * labels with |Δ| ≤ [EvCalibrationCatalog.MAX_ABS_DELTA].
 *
 * Set [Config.live] = true only for optional debug against a real LLM; unit tests
 * must keep live=false and use [FixtureBackedLlmClient].
 */
class EvCalibrationRunner(
    private val llm: LlmClient,
    private val fixtures: List<EvCalibrationFixture> = EvCalibrationCatalog.load(),
    private val config: Config = Config(),
) {
    data class Config(
        /** When true, still builds EvPrompt requests but does not require fixture-backed replies. */
        val live: Boolean = false,
        val maxAbsDelta: Double = EvCalibrationCatalog.MAX_ABS_DELTA,
        val model: String = if (live) "live" else "fixture",
        val temperature: Double = 0.0,
    )

    data class DimDelta(
        val name: String,
        val label: Double,
        val predicted: Double,
        val absDelta: Double,
        val withinTolerance: Boolean,
    )

    data class CaseResult(
        val fixtureId: String,
        val title: String,
        val overallBand: Double,
        val dims: BandDims,
        val engineVer: String,
        val promptVer: String,
        val deltas: List<DimDelta>,
        val passed: Boolean,
    ) {
        val maxAbsDelta: Double get() = deltas.maxOf { it.absDelta }
    }

    data class Report(
        val results: List<CaseResult>,
        val promptVerExpected: String = EvCalibrationCatalog.PROMPT_VER,
        val maxAbsDelta: Double,
    ) {
        val passed: Boolean get() = results.isNotEmpty() && results.all { it.passed }
        val failureSummary: String
            get() = results.filterNot { it.passed }.joinToString("\n") { case ->
                val bad = case.deltas.filterNot { it.withinTolerance }
                    .joinToString { d -> "${d.name}: |${d.predicted}-${d.label}|=${d.absDelta}" }
                "${case.fixtureId} (${case.title}): $bad"
            }
    }

    suspend fun run(): Report {
        require(fixtures.size == EvCalibrationCatalog.FIXTURE_COUNT) {
            "Expected ${EvCalibrationCatalog.FIXTURE_COUNT} fixtures, got ${fixtures.size}"
        }
        val results = fixtures.map { evaluate(it) }
        return Report(
            results = results,
            maxAbsDelta = config.maxAbsDelta,
        )
    }

    suspend fun evaluate(fixture: EvCalibrationFixture): CaseResult {
        val request = buildRequest(fixture)
        val raw = llm.complete(request).content
        val ev = EvJsonParser.parse(
            raw = raw,
            sessionId = "cal_${fixture.id}",
            evId = "ev_${fixture.id}",
        )
        val deltas = compare(fixture.labels, ev.dims, config.maxAbsDelta)
        return CaseResult(
            fixtureId = fixture.id,
            title = fixture.title,
            overallBand = ev.overallBand,
            dims = ev.dims,
            engineVer = ev.engineVer,
            promptVer = ev.promptVer,
            deltas = deltas,
            passed = deltas.all { it.withinTolerance },
        )
    }

    fun buildRequest(fixture: EvCalibrationFixture): ChatRequest =
        ChatRequest(
            model = config.model,
            messages = listOf(
                ChatMessage(ChatMessage.Role.SYSTEM, EvPrompt.SYSTEM),
                ChatMessage(
                    ChatMessage.Role.USER,
                    EvPrompt.userTranscript(fixture.topicTitle, fixture.transcriptPairs()),
                ),
            ),
            temperature = config.temperature,
            stream = false,
        )

    companion object {
        fun compare(
            labels: BandLabels,
            dims: BandDims,
            maxAbsDelta: Double = EvCalibrationCatalog.MAX_ABS_DELTA,
        ): List<DimDelta> {
            val pairs = listOf(
                "fc" to (labels.fc to dims.fc.score),
                "lr" to (labels.lr to dims.lr.score),
                "gra" to (labels.gra to dims.gra.score),
                "p" to (labels.p to dims.p.score),
            )
            return pairs.map { (name, pair) ->
                val (label, predicted) = pair
                val delta = abs(predicted - label)
                DimDelta(
                    name = name,
                    label = label,
                    predicted = predicted,
                    absDelta = delta,
                    withinTolerance = delta <= maxAbsDelta + 1e-9,
                )
            }
        }
    }
}
