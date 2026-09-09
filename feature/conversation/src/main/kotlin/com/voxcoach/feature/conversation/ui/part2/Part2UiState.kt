package com.voxcoach.feature.conversation.ui.part2

import com.voxcoach.core.domain.part2.Part2CueCard
import com.voxcoach.core.domain.part2.Part2CueCardBank

data class Part2UiState(
    val sessionId: String? = null,
    val phase: Phase = Phase.Preparing,
    val cueCard: Part2CueCard? = null,
    val examinerText: String = "",
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    /** Remaining seconds in prep (60→0) or speaking (120→0). */
    val countdownSec: Int = 0,
    val prepTotalSec: Int = Part2CueCardBank.PREP_SECONDS,
    val speakTotalSec: Int = Part2CueCardBank.SPEAK_SECONDS,
    /** Optional notes placeholder (UI only; not persisted in M2). */
    val notesDraft: String = "",
    val statusMessage: String = "准备模拟口试 Part 2…",
    val error: String? = null,
    val ending: Boolean = false,
    val recording: Boolean = false,
    val audioPath: String? = null,
    val asrListening: Boolean = false,
    val navigateToReportSessionId: String? = null,
) {
    val categoryLabel: String
        get() {
            val c = cueCard ?: return "—"
            return "${c.category.titleZh} · ${c.category.title}"
        }

    val progressLabel: String
        get() = when (phase) {
            Phase.Preparing -> "准备中"
            Phase.Intro -> "考官说明"
            Phase.PrepCountdown -> "准备倒计时 ${countdownSec}s"
            Phase.SpeakPrompt -> "即将独白"
            Phase.Speaking -> "独白中 ${countdownSec}s"
            Phase.Saving -> "记录中"
            Phase.Evaluating -> "评测中"
            Phase.Done -> "已完成"
        }

    enum class Phase {
        Preparing,
        Intro,
        PrepCountdown,
        SpeakPrompt,
        Speaking,
        Saving,
        Evaluating,
        Done,
    }
}
