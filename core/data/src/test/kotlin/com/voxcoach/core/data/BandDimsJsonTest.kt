package com.voxcoach.core.data

import com.google.common.truth.Truth.assertThat
import com.voxcoach.core.data.db.toBandDims
import com.voxcoach.core.data.db.toJson
import com.voxcoach.core.domain.model.BandDims
import com.voxcoach.core.domain.model.DimScore
import org.junit.Test

class BandDimsJsonTest {
    @Test
    fun roundTripDimsJson() {
        val dims = BandDims(
            fc = DimScore(6.5, "ok", listOf("a")),
            lr = DimScore(7.0, "good", emptyList()),
            gra = DimScore(6.0, "meh", listOf("b", "c")),
            p = DimScore(7.5, "clear", emptyList()),
        )
        val restored = dims.toJson().toBandDims()
        assertThat(restored.fc.score).isEqualTo(6.5)
        assertThat(restored.gra.evidence).containsExactly("b", "c")
        assertThat(restored.p.score).isEqualTo(7.5)
    }
}
