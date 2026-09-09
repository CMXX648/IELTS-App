package com.voxcoach.feature.conversation.ui

data class ConversationUiState(
    val phase: Phase = Phase.Idle,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val assistantText: String = "",
    val statusMessage: String = "按住麦克风说话（英文）",
    val error: String? = null,
) {
    enum class Phase {
        Idle,
        Listening,
        Thinking,
        Speaking,
    }
}
