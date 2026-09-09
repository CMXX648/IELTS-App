package com.voxcoach.core.domain.pf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DimTrendAggregatorTest {
    private fun snap(
        id: String,
        createdAt: Long,
        overall: Double,
        fc: Double = overall,
        lr: Double = overall,
        gra: Double = overall,
        p: Double = overall,
    ) = EvScoreSnapshot(
        id = id,
        sessionId = "s-$id",
        createdAt = createdAt,
        overallBand = overall,
        fc = fc,
        lr = lr,
        gra = gra,
        p = p,
    )

    @Test
    fun aggregate_empty_returnsEmptySeries() {
        val series = DimTrendAggregator.aggregate(emptyList(), n = 10)
        assertThat(series.isEmpty).isTrue()
        assertThat(series.overall).isEmpty()
        assertThat(series.latestOverall).isNull()
    }

    @Test
    fun aggregate_takesRecentN_sortedChronologically() {
        val input = listOf(
            snap("a", createdAt = 100, overall = 5.0),
            snap("b", createdAt = 300, overall = 6.5),
            snap("c", createdAt = 200, overall = 6.0),
            snap("d", createdAt = 400, overall = 7.0),
        )
        val series = DimTrendAggregator.aggregate(input, n = 3)
        assertThat(series.points.map { it.id }).containsExactly("c", "b", "d").inOrder()
        assertThat(series.overall).containsExactly(6.0, 6.5, 7.0).inOrder()
        assertThat(series.latestOverall).isEqualTo(7.0)
    }

    @Test
    fun aggregate_preservesPerDimensionScores() {
        val input = listOf(
            snap("x", 10, overall = 6.0, fc = 5.5, lr = 6.0, gra = 6.5, p = 7.0),
            snap("y", 20, overall = 6.5, fc = 6.0, lr = 6.5, gra = 7.0, p = 7.5),
        )
        val series = DimTrendAggregator.aggregate(input, n = 10)
        assertThat(series.fc).containsExactly(5.5, 6.0).inOrder()
        assertThat(series.lr).containsExactly(6.0, 6.5).inOrder()
        assertThat(series.gra).containsExactly(6.5, 7.0).inOrder()
        assertThat(series.p).containsExactly(7.0, 7.5).inOrder()
    }

    @Test
    fun cacheJson_roundTrip() {
        val series = DimTrendAggregator.aggregate(
            listOf(
                snap("1", 1, 6.0, fc = 5.5, lr = 6.0, gra = 6.5, p = 7.0),
                snap("2", 2, 6.5, fc = 6.0, lr = 6.5, gra = 7.0, p = 7.5),
            ),
            n = 10,
        )
        val json = DimTrendAggregator.toCacheJson(series, updatedAt = 99L)
        val restored = DimTrendAggregator.fromCacheJson(json)
        assertThat(restored).isNotNull()
        assertThat(restored!!.points.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(restored.fc).containsExactly(5.5, 6.0).inOrder()
        assertThat(restored.limit).isEqualTo(10)
    }

    @Test
    fun fromCacheJson_blankOrInvalid_returnsNull() {
        assertThat(DimTrendAggregator.fromCacheJson(null)).isNull()
        assertThat(DimTrendAggregator.fromCacheJson("")).isNull()
        assertThat(DimTrendAggregator.fromCacheJson("{not-json")).isNull()
    }
}
