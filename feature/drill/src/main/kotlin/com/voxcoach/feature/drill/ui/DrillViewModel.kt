package com.voxcoach.feature.drill.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.gr.GrammarJudgeParser
import com.voxcoach.core.domain.gr.GrammarJudgePrompt
import com.voxcoach.core.domain.llm.LlmClient
import com.voxcoach.core.domain.model.AsrSessionConfig
import com.voxcoach.core.domain.model.ChatMessage
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.DrillAttempt
import com.voxcoach.core.domain.model.Mistake
import com.voxcoach.core.domain.model.MistakeStatus
import com.voxcoach.core.domain.repository.DrillAttemptRepository
import com.voxcoach.core.domain.repository.GrammarPointRepository
import com.voxcoach.core.domain.repository.MistakeRepository
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import com.voxcoach.core.domain.speech.AsrEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DrillViewModel @Inject constructor(
    private val asrEngine: AsrEngine,
    private val llmClient: LlmClient,
    private val settingsRepository: LlmSettingsRepository,
    private val grammarPointRepository: GrammarPointRepository,
    private val drillAttemptRepository: DrillAttemptRepository,
    private val mistakeRepository: MistakeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pointId: String = savedStateHandle["pointId"] ?: "GP-A1"
    private val _uiState = MutableStateFlow(DrillUiState())
    val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var judgeJob: Job? = null
    private var lastAttemptId: String? = null

    init {
        viewModelScope.launch {
            grammarPointRepository.ensureSeeded()
            val point = grammarPointRepository.get(pointId)
            _uiState.update {
                it.copy(
                    point = point,
                    statusMessage = if (point != null) "按住麦克风说一句" else "语法点未找到",
                    error = if (point == null) "未知语法点：$pointId" else null,
                )
            }
        }
        viewModelScope.launch {
            asrEngine.partialResults.collect { partial ->
                _uiState.update { it.copy(partialTranscript = partial.text) }
            }
        }
        viewModelScope.launch {
            asrEngine.finals.collect { final ->
                onUserFinal(final.text)
            }
        }
    }

    fun onMicPressed() {
        if (_uiState.value.phase == DrillUiState.Phase.Judging) return
        judgeJob?.cancel()
        listenJob?.cancel()
        _uiState.update {
            it.copy(
                phase = DrillUiState.Phase.Listening,
                partialTranscript = "",
                finalTranscript = "",
                judge = null,
                collected = false,
                offerCollect = false,
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
                        phase = DrillUiState.Phase.Idle,
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
            val text = current.finalTranscript.ifBlank { current.partialTranscript }
            if (text.isNotBlank() && current.phase == DrillUiState.Phase.Listening) {
                onUserFinal(text)
            } else if (text.isBlank() && current.phase == DrillUiState.Phase.Listening) {
                _uiState.update {
                    it.copy(
                        phase = DrillUiState.Phase.Idle,
                        statusMessage = "未识别到内容，请再试",
                    )
                }
            }
        }
    }

    private fun onUserFinal(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val phase = _uiState.value.phase
        if (phase == DrillUiState.Phase.Judging) return
        val point = _uiState.value.point ?: return
        _uiState.update {
            it.copy(
                finalTranscript = trimmed,
                partialTranscript = trimmed,
                phase = DrillUiState.Phase.Judging,
                statusMessage = "判定中…",
                judge = null,
                offerCollect = false,
                collected = false,
            )
        }
        judgeJob?.cancel()
        judgeJob = viewModelScope.launch {
            runCatching {
                val cfg = settingsRepository.config.first()
                val request = ChatRequest(
                    model = cfg.model,
                    messages = listOf(
                        ChatMessage(ChatMessage.Role.SYSTEM, GrammarJudgePrompt.SYSTEM),
                        ChatMessage(
                            ChatMessage.Role.USER,
                            GrammarJudgePrompt.userPrompt(
                                title = point.title,
                                rule = point.rule,
                                skeleton = point.skeleton,
                                topicHint = point.topicHint,
                                userSentence = trimmed,
                            ),
                        ),
                    ),
                    temperature = 0.2,
                    stream = false,
                )
                val raw = llmClient.complete(request).content
                val judge = GrammarJudgeParser.parse(raw)
                val attemptId = UUID.randomUUID().toString()
                lastAttemptId = attemptId
                drillAttemptRepository.insert(
                    DrillAttempt(
                        id = attemptId,
                        grammarPointId = point.id,
                        promptId = "default",
                        userSentence = trimmed,
                        hit = judge.hit,
                        feedbackJson = judge.toFeedbackJson(),
                        triedAt = System.currentTimeMillis(),
                    ),
                )
                _uiState.update {
                    it.copy(
                        phase = DrillUiState.Phase.Feedback,
                        judge = judge,
                        offerCollect = !judge.hit,
                        statusMessage = if (judge.hit) "命中目标结构 ✅" else "未命中，可再试或收藏到错题本",
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = DrillUiState.Phase.Idle,
                        error = e.message ?: "判定失败",
                        statusMessage = "判定失败，可跳过再试",
                    )
                }
            }
        }
    }

    fun collectToMistakes() {
        val state = _uiState.value
        val point = state.point ?: return
        val judge = state.judge ?: return
        if (judge.hit || state.collected) return
        viewModelScope.launch {
            mistakeRepository.insert(
                Mistake(
                    id = UUID.randomUUID().toString(),
                    sourceItemId = lastAttemptId,
                    sessionId = null,
                    dimension = "gra",
                    quote = state.finalTranscript,
                    correction = judge.correction.ifBlank { judge.model },
                    grammarPointId = point.id,
                    status = MistakeStatus.OPEN,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            _uiState.update {
                it.copy(collected = true, offerCollect = false, statusMessage = "已加入错题本")
            }
        }
    }

    fun retry() {
        _uiState.update {
            it.copy(
                phase = DrillUiState.Phase.Idle,
                partialTranscript = "",
                finalTranscript = "",
                judge = null,
                offerCollect = false,
                collected = false,
                statusMessage = "按住麦克风说一句",
                error = null,
            )
        }
    }
}
