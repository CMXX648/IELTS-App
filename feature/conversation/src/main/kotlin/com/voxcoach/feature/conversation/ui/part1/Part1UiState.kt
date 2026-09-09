package com.voxcoach.feature.conversation.ui.part1

import com.voxcoach.core.domain.part1.Part1Question

data class Part1UiState(
    val sessionId: String? = null,
    val phase: Phase = Phase.Preparing,
    val questions: List<Part1Question> = emptyList(),
    /** 0-based index of current bank question. */
    val questionIndex: Int = 0,
    val totalQuestions: Int = 5,
    val themeLabel: String = "",
    val examinerText: String = "",
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val answeredCount: Int = 0,
    val statusMessage: String = "准备模拟口试 Part 1…",
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

    val currentQuestion: Part1Question?
        get() = questions.getOrNull(questionIndex)

    enum class Phase {
        Preparing,
        Asking,
        AwaitingAnswer,
        Listening,
        Saving,
        Evaluating,
        Done,
    }
}
