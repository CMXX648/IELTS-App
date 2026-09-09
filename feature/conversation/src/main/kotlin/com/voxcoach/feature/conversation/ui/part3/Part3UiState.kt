package com.voxcoach.feature.conversation.ui.part3

import com.voxcoach.core.domain.part2.Part2CueCard
import com.voxcoach.core.domain.part3.Part3Question

data class Part3UiState(
    val sessionId: String? = null,
    val phase: Phase = Phase.Preparing,
    val cueCard: Part2CueCard? = null,
    val questions: List<Part3Question> = emptyList(),
    /** 0-based index of current discussion question. */
    val questionIndex: Int = 0,
    val totalQuestions: Int = 5,
    val themeLabel: String = "",
    val examinerText: String = "",
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val answeredCount: Int = 0,
    val statusMessage: String = "准备模拟口试 Part 3…",
    val error: String? = null,
    val ending: Boolean = false,
    val recording: Boolean = false,
    val audioPath: String? = null,
    val navigateToReportSessionId: String? = null,
) {
    val progressLabel: String
        get() {
            if (questions.isEmpty()) return "—"
            val n = (questionIndex + 1).coerceAtMost(totalQuestions)
            return "第 $n/$totalQuestions 题"
        }

    val currentQuestion: Part3Question?
        get() = questions.getOrNull(questionIndex)

    enum class Phase {
        Preparing,
        Intro,
        Asking,
        AwaitingAnswer,
        Listening,
        Saving,
        Evaluating,
        Done,
    }
}
