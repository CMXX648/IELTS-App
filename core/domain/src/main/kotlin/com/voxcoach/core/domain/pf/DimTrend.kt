package com.voxcoach.core.domain.pf

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One EV session's band scores for PF-02 trend charts (no feedback items). */
data class EvScoreSnapshot(
    val id: String,
    val sessionId: String,
    val createdAt: Long,
    val overallBand: Double,
    val fc: Double,
    val lr: Double,
    val gra: Double,
    val p: Double,
)

/** Chronological (oldest → newest) series for overall + FC/LR/GRA/P. */
data class DimTrendSeries(
    val points: List<EvScoreSnapshot>,
    val limit: Int = DimTrendAggregator.DEFAULT_N,
) {
    val isEmpty: Boolean get() = points.isEmpty()
    val overall: List<Double> get() = points.map { it.overallBand }
    val fc: List<Double> get() = points.map { it.fc }
    val lr: List<Double> get() = points.map { it.lr }
    val gra: List<Double> get() = points.map { it.gra }
    val p: List<Double> get() = points.map { it.p }
    val latestOverall: Double? get() = points.lastOrNull()?.overallBand
}

@Serializable
data class DimTrendCacheDto(
    val n: Int,
    val updatedAt: Long,
    val points: List<EvScoreSnapshotDto>,
)

@Serializable
data class EvScoreSnapshotDto(
    val id: String,
    val sessionId: String,
    val createdAt: Long,
    val overall: Double,
    val fc: Double,
    val lr: Double,
    val gra: Double,
    val p: Double,
)

/**
 * PF-02: aggregate recent N EV sessions into chronological dim trends.
 * Input may be newest-first (Room DESC); output points are oldest→newest for charts.
 */
object DimTrendAggregator {
    const val DEFAULT_N = 10

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun aggregate(
        recent: List<EvScoreSnapshot>,
        n: Int = DEFAULT_N,
    ): DimTrendSeries {
        require(n > 0) { "n must be > 0" }
        val chronological = recent
            .sortedByDescending { it.createdAt }
            .take(n)
            .sortedBy { it.createdAt }
        return DimTrendSeries(points = chronological, limit = n)
    }

    fun toCacheJson(series: DimTrendSeries, updatedAt: Long = System.currentTimeMillis()): String {
        val dto = DimTrendCacheDto(
            n = series.limit,
            updatedAt = updatedAt,
            points = series.points.map {
                EvScoreSnapshotDto(
                    id = it.id,
                    sessionId = it.sessionId,
                    createdAt = it.createdAt,
                    overall = it.overallBand,
                    fc = it.fc,
                    lr = it.lr,
                    gra = it.gra,
                    p = it.p,
                )
            },
        )
        return json.encodeToString(dto)
    }

    fun fromCacheJson(raw: String?): DimTrendSeries? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val dto = json.decodeFromString<DimTrendCacheDto>(raw)
            DimTrendSeries(
                points = dto.points.map {
                    EvScoreSnapshot(
                        id = it.id,
                        sessionId = it.sessionId,
                        createdAt = it.createdAt,
                        overallBand = it.overall,
                        fc = it.fc,
                        lr = it.lr,
                        gra = it.gra,
                        p = it.p,
                    )
                }.sortedBy { it.createdAt },
                limit = dto.n.coerceAtLeast(1),
            )
        }.getOrNull()
    }
}
