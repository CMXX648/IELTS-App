package com.voxcoach.feature.conversation.ui

import com.voxcoach.core.domain.model.TurnLatency

data class ConversationUiState(
    val sessionId: String? = null,
    val topicId: String? = null,
    val topicTitle: String = "Hometown",
    val phase: Phase = Phase.Idle,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val assistantText: String = "",
    val hintText: String? = null,
    val hintVisible: Boolean = false,
    val hintEnabled: Boolean = false,
    val turnCount: Int = 0,
    val lastLatency: TurnLatency? = null,
    val bargeInCount: Int = 0,
    val statusMessage: String = "按住麦克风说话（英文）",
    val error: String? = null,
    val ending: Boolean = false,
    val endedEvId: String? = null,
    val navigateToReportSessionId: String? = null,
    val recording: Boolean = false,
    val audioPath: String? = null,
) {
    enum class Phase {
        Idle,
        Listening,
        Thinking,
        Speaking,
        Evaluating,
    }
}
