package com.voxcoach.feature.conversation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.llm.LlmClient
import com.voxcoach.core.domain.model.AsrSessionConfig
import com.voxcoach.core.domain.model.ChatMessage
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.Sentence
import com.voxcoach.core.domain.model.TtsOptions
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import com.voxcoach.core.domain.speech.AsrEngine
import com.voxcoach.core.domain.speech.TtsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val asrEngine: AsrEngine,
    private val ttsEngine: TtsEngine,
    private val llmClient: LlmClient,
    private val settingsRepository: LlmSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var pipelineJob: Job? = null
    private val history = mutableListOf<ChatMessage>()

    init {
        viewModelScope.launch {
            asrEngine.partialResults.collect { partial ->
                _uiState.update {
                    it.copy(partialTranscript = partial.text)
                }
            }
        }
        viewModelScope.launch {
            asrEngine.finals.collect { final ->
                onUserFinal(final.text)
            }
        }
    }

    fun onMicPressed() {
        pipelineJob?.cancel()
        listenJob?.cancel()
        viewModelScope.launch { ttsEngine.stopAll() }
        _uiState.update {
            it.copy(
                phase = ConversationUiState.Phase.Listening,
                partialTranscript = "",
                finalTranscript = "",
                assistantText = "",
                statusMessage = "正在听…",
                error = null,
            )
        }
        listenJob = viewModelScope.launch {
            runCatching {
                asrEngine.start(AsrSessionConfig(languageTag = "en-GB"))
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = ConversationUiState.Phase.Idle,
                        error = e.message ?: "ASR 启动失败",
                        statusMessage = "ASR 不可用",
                    )
                }
            }
        }
    }

    fun onMicReleased() {
        listenJob = viewModelScope.launch {
            runCatching { asrEngine.stop() }
            val current = _uiState.value
            val text = current.finalTranscript.ifBlank {
                current.partialTranscript
            }
            if (text.isNotBlank() && current.phase == ConversationUiState.Phase.Listening) {
                onUserFinal(text)
            } else if (text.isBlank()) {
                _uiState.update {
                    it.copy(
                        phase = ConversationUiState.Phase.Idle,
                        statusMessage = "未识别到内容，请再试",
                    )
                }
            }
        }
    }

    private fun onUserFinal(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.phase == ConversationUiState.Phase.Thinking ||
            _uiState.value.phase == ConversationUiState.Phase.Speaking
        ) {
            return
        }
        _uiState.update {
            it.copy(
                finalTranscript = trimmed,
                partialTranscript = trimmed,
                phase = ConversationUiState.Phase.Thinking,
                statusMessage = "思考中…",
                assistantText = "",
            )
        }
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching {
                val cfg = settingsRepository.config.first()
                if (history.none { it.role == ChatMessage.Role.SYSTEM }) {
                    history += ChatMessage(
                        role = ChatMessage.Role.SYSTEM,
                        content = SYSTEM_PROMPT,
                    )
                }
                history += ChatMessage(ChatMessage.Role.USER, trimmed)
                val request = ChatRequest(
                    model = cfg.model,
                    messages = history.toList(),
                    stream = true,
                )
                val sb = StringBuilder()
                llmClient.streamChat(request).collect { delta ->
                    if (delta.content.isNotEmpty()) {
                        sb.append(delta.content)
                        _uiState.update {
                            it.copy(
                                assistantText = sb.toString(),
                                statusMessage = "AI 回复中…",
                            )
                        }
                    }
                }
                val full = sb.toString().trim()
                history += ChatMessage(ChatMessage.Role.ASSISTANT, full)
                _uiState.update {
                    it.copy(
                        phase = ConversationUiState.Phase.Speaking,
                        statusMessage = "播放中…",
                        assistantText = full,
                    )
                }
                if (full.isNotBlank()) {
                    ttsEngine.speak(Sentence(full), TtsOptions(languageTag = "en-GB"))
                }
                _uiState.update {
                    it.copy(
                        phase = ConversationUiState.Phase.Idle,
                        statusMessage = "按住麦克风继续",
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = ConversationUiState.Phase.Idle,
                        error = e.message ?: "链路失败",
                        statusMessage = "出错了，请检查设置",
                    )
                }
            }
        }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a friendly IELTS Speaking examiner. Reply in clear English, " +
                "ask one follow-up question, keep answers under 3 sentences."
    }
}
