package com.voxcoach.feature.conversation.ui.fullmock

import com.voxcoach.core.domain.part1.Part1Question
import com.voxcoach.core.domain.part2.Part2CueCard
import com.voxcoach.core.domain.part2.Part2CueCardBank
import com.voxcoach.core.domain.part3.Part3Question

/**
 * Unified full-mock UI (P1 → P2 → P3 in one SessionSubtype.MOCK).
 */
data class FullMockUiState(
    val sessionId: String? = null,
    val stage: Stage = Stage.Part1,
    val phase: Phase = Phase.Preparing,
    // Part 1
    val part1Questions: List<Part1Question> = emptyList(),
    val part1ThemeLabel: String = "",
    // Part 2
    val cueCard: Part2CueCard? = null,
    val notesDraft: String = "",
    val countdownSec: Int = 0,
    val prepTotalSec: Int = Part2CueCardBank.PREP_SECONDS,
    val speakTotalSec: Int = Part2CueCardBank.SPEAK_SECONDS,
    val asrListening: Boolean = false,
    // Part 3
    val part3Questions: List<Part3Question> = emptyList(),
    val part3ThemeLabel: String = "",
    // Shared Q&A / speak transcript
    val questionIndex: Int = 0,
    val totalQuestions: Int = 5,
    val answeredInStage: Int = 0,
    val examinerText: String = "",
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val statusMessage: String = "准备完整模考…",
    val error: String? = null,
    val ending: Boolean = false,
    val recording: Boolean = false,
    val audioPath: String? = null,
    val navigateToReportSessionId: String? = null,
) {
    val stageLabelZh: String
        get() = when (stage) {
            Stage.Part1 -> "第一部分 · 问答"
            Stage.Part2 -> "第二部分 · 独白"
            Stage.Part3 -> "第三部分 · 讨论"
            Stage.Evaluating -> "生成报告"
            Stage.Done -> "已完成"
        }

    val progressLabel: String
        get() = when (stage) {
            Stage.Part1, Stage.Part3 -> {
                if (totalQuestions <= 0) "—"
                else {
                    val n = (questionIndex + 1).coerceAtMost(totalQuestions)
                    "第 $n/$totalQuestions 题"
                }
            }
            Stage.Part2 -> when (phase) {
                Phase.P2PrepCountdown -> "准备 ${countdownSec}s"
                Phase.P2Speaking -> "独白 ${countdownSec}s"
                Phase.P2Intro -> "考官说明"
                Phase.P2SpeakPrompt -> "即将独白"
                else -> "独白"
            }
            Stage.Evaluating -> "评测中"
            Stage.Done -> "完成"
        }

    val currentPart1Question: Part1Question?
        get() = part1Questions.getOrNull(questionIndex)

    val currentPart3Question: Part3Question?
        get() = part3Questions.getOrNull(questionIndex)

    enum class Stage {
        Part1,
        Part2,
        Part3,
        Evaluating,
        Done,
    }

    enum class Phase {
        Preparing,
        // Q&A (Part1 / Part3)
        Intro,
        Asking,
        AwaitingAnswer,
        Listening,
        Saving,
        // Part2
        P2Intro,
        P2PrepCountdown,
        P2SpeakPrompt,
        P2Speaking,
        P2Saving,
        Evaluating,
        Done,
    }
}
