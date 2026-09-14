package com.voxcoach.feature.drill.ui

import com.voxcoach.core.domain.model.DrillJudgeResult
import com.voxcoach.core.domain.model.GrammarPoint

data class DrillUiState(
    val point: GrammarPoint? = null,
    val phase: Phase = Phase.Idle,
    val mode: Mode = Mode.Produce,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val judge: DrillJudgeResult? = null,
    val shadowModel: String = "",
    val statusMessage: String = "按住麦克风说一句",
    val error: String? = null,
    val collected: Boolean = false,
    val offerCollect: Boolean = false,
) {
    enum class Phase { Idle, Listening, Judging, Feedback }
    /** GR-02 produce vs GR-05 shadowing (same mic pipeline, different judge). */
    enum class Mode { Produce, Shadow }
}
