package com.voxcoach.core.domain.ev

import com.google.common.truth.Truth.assertThat
import com.voxcoach.core.domain.model.ChatMessage
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * M2 EV calibration regression (docs/04 §8, docs/06): 8 fixtures, |Δ| ≤ 0.5 per dim.
 */
class EvCalibrationTest {

    @Test
    fun catalogLoadsEightFixtures() {
        val fixtures = EvCalibrationCatalog.load()
        assertThat(fixtures).hasSize(EvCalibrationCatalog.FIXTURE_COUNT)
        assertThat(fixtures.map { it.id }).containsExactly(
            "cal-01", "cal-02", "cal-03", "cal-04",
            "cal-05", "cal-06", "cal-07", "cal-08",
        ).inOrder()
        fixtures.forEach { f ->
            assertThat(f.turns.size).isAtLeast(4)
            assertThat(f.labels.asMap().values).isNotEmpty()
            f.labels.asMap().values.forEach { score ->
                assertThat(score % 0.5).isEqualTo(0.0)
                assertThat(score).isAtLeast(0.0)
                assertThat(score).isAtMost(9.0)
            }
            assertThat(f.llmResponseJson).contains("\"schemaVer\"")
        }
    }

    @Test
    fun offlineRegressionAllDimensionsWithinHalfBand() = runTest {
        val fixtures = EvCalibrationCatalog.load()
        val llm = FixtureBackedLlmClient(fixtures)
        val report = EvCalibrationRunner(llm = llm, fixtures = fixtures).run()

        assertThat(report.results).hasSize(8)
        assertWithMessage(report)
        assertThat(report.passed).isTrue()
        report.results.forEach { case ->
            case.deltas.forEach { d ->
                assertThat(d.absDelta).isAtMost(EvCalibrationCatalog.MAX_ABS_DELTA)
            }
            assertThat(case.promptVer).isEqualTo(EvCalibrationCatalog.PROMPT_VER)
            assertThat(case.engineVer).isEqualTo("fixture")
        }
    }

    @Test
    fun band70AnchorMustNotDriftToEightFive() = runTest {
        val fixture = EvCalibrationCatalog.byId("cal-05")
        assertThat(fixture.labels.fc).isEqualTo(7.0)
        assertThat(fixture.labels.lr).isEqualTo(7.0)
        assertThat(fixture.labels.gra).isEqualTo(7.0)
        assertThat(fixture.labels.p).isEqualTo(7.0)

        val llm = FixtureBackedLlmClient(listOf(fixture))
        val case = EvCalibrationRunner(llm = llm, fixtures = listOf(fixture)).evaluate(fixture)

        assertThat(case.passed).isTrue()
        // Direction check from docs/06: a 7.0 sample must not be scored 8.5.
        case.deltas.forEach { d ->
            assertThat(d.predicted).isNotEqualTo(8.5)
            assertThat(kotlin.math.abs(d.predicted - 7.0)).isAtMost(0.5)
        }
    }

    @Test
    fun mixedDimensionProfilePreservesWeakGra() = runTest {
        val fixture = EvCalibrationCatalog.byId("cal-07")
        assertThat(fixture.labels.gra).isEqualTo(5.5)
        assertThat(fixture.labels.lr).isEqualTo(7.0)

        val llm = FixtureBackedLlmClient(listOf(fixture))
        val case = EvCalibrationRunner(llm = llm, fixtures = listOf(fixture)).evaluate(fixture)

        assertThat(case.passed).isTrue()
        val gra = case.deltas.first { it.name == "gra" }
        val lr = case.deltas.first { it.name == "lr" }
        assertThat(gra.predicted).isLessThan(lr.predicted)
    }

    @Test
    fun fixtureClientMatchesOnCandidateUtterance() = runTest {
        val fixtures = EvCalibrationCatalog.load()
        val llm = FixtureBackedLlmClient(fixtures)
        val target = fixtures.first { it.id == "cal-04" }
        val request = EvCalibrationRunner(llm, listOf(target)).buildRequest(target)
        val raw = llm.complete(request).content
        val parsed = EvJsonParser.parse(raw, sessionId = "s", evId = "e")
        assertThat(parsed.overallBand).isEqualTo(6.5)
        assertThat(parsed.dims.fc.score).isEqualTo(6.5)
    }

    @Test
    fun compareHelperFlagsOutOfTolerance() {
        val labels = BandLabels(fc = 7.0, lr = 7.0, gra = 7.0, p = 7.0)
        val dims = com.voxcoach.core.domain.model.BandDims(
            fc = com.voxcoach.core.domain.model.DimScore(7.0),
            lr = com.voxcoach.core.domain.model.DimScore(7.0),
            gra = com.voxcoach.core.domain.model.DimScore(8.5), // |Δ|=1.5
            p = com.voxcoach.core.domain.model.DimScore(7.0),
        )
        val deltas = EvCalibrationRunner.compare(labels, dims)
        assertThat(deltas.first { it.name == "gra" }.withinTolerance).isFalse()
        assertThat(deltas.filter { it.name != "gra" }.all { it.withinTolerance }).isTrue()
    }

    @Test
    fun buildRequestUsesEvPromptContract() {
        val fixture = EvCalibrationCatalog.byId("cal-01")
        val llm = FixtureBackedLlmClient(listOf(fixture))
        val request = EvCalibrationRunner(llm, listOf(fixture)).buildRequest(fixture)
        assertThat(request.stream).isFalse()
        assertThat(request.messages).hasSize(2)
        assertThat(request.messages[0].role).isEqualTo(ChatMessage.Role.SYSTEM)
        assertThat(request.messages[0].content).contains("schemaVer")
        assertThat(request.messages[1].content).contains("Topic: Hometown")
        assertThat(request.messages[1].content).contains("Evaluate now")
    }

    private fun assertWithMessage(report: EvCalibrationRunner.Report) {
        if (!report.passed) {
            throw AssertionError("EV calibration failed:\n${report.failureSummary}")
        }
    }
}
