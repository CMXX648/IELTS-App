package com.voxcoach.feature.drill.ui

import com.voxcoach.core.domain.model.DrillJudgeResult
import com.voxcoach.core.domain.model.GrammarPoint

data class DrillUiState(
    val point: GrammarPoint? = null,
    val phase: Phase = Phase.Idle,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val judge: DrillJudgeResult? = null,
    val statusMessage: String = "按住麦克风说一句",
    val error: String? = null,
    val collected: Boolean = false,
    val offerCollect: Boolean = false,
) {
    enum class Phase { Idle, Listening, Judging, Feedback }
}
