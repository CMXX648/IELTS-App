package com.voxcoach.feature.drill.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.gr.GrammarJudgeParser
import com.voxcoach.core.domain.gr.GrammarJudgePrompt
import com.voxcoach.core.domain.gr.ShadowingJudge
import com.voxcoach.core.domain.gr.ShadowingPolicy
import com.voxcoach.core.domain.llm.LlmClient
import com.voxcoach.core.domain.model.AsrSessionConfig
import com.voxcoach.core.domain.model.ChatMessage
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.DrillAttempt
import com.voxcoach.core.domain.model.Mistake
import com.voxcoach.core.domain.model.MistakeStatus
import com.voxcoach.core.domain.model.Sentence
import com.voxcoach.core.domain.model.TtsOptions
import com.voxcoach.core.domain.repository.DrillAttemptRepository
import com.voxcoach.core.domain.repository.GrammarPointRepository
import com.voxcoach.core.domain.repository.MistakeRepository
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import com.voxcoach.core.domain.speech.AsrEngine
import com.voxcoach.core.domain.speech.TtsEngine
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
import com.voxcoach.core.domain.ux.NetworkUx

@HiltViewModel
class DrillViewModel @Inject constructor(
    private val asrEngine: AsrEngine,
    private val ttsEngine: TtsEngine,
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

    fun setMode(mode: DrillUiState.Mode) {
        if (_uiState.value.mode == mode) return
        judgeJob?.cancel()
        listenJob?.cancel()
        _uiState.update {
            it.copy(
                mode = mode,
                phase = DrillUiState.Phase.Idle,
                partialTranscript = "",
                finalTranscript = "",
                judge = null,
                statusMessage = if (mode == DrillUiState.Mode.Shadow) "先听示范，再跟读" else "按住麦克风说一句",
                error = null,
                collected = false,
                offerCollect = false,
            )
        }
    }

    fun playShadowModel() {
        val state = _uiState.value
        val model = shadowModelFor(state)
        if (model.isBlank()) {
            _uiState.update { it.copy(error = "暂无示范句") }
            return
        }
        viewModelScope.launch {
            runCatching {
                _uiState.update { it.copy(statusMessage = "播放示范中…", error = null) }
                ttsEngine.speak(Sentence(model), TtsOptions(languageTag = "en-GB"))
                _uiState.update { it.copy(statusMessage = "跟读示范句，按住麦克风说话") }
            }.onFailure { e ->
                _uiState.update { it.copy(error = NetworkUx.userMessage(e, "示范播放失败")) }
            }
        }
    }

    private fun shadowModelFor(state: DrillUiState): String {
        if (state.shadowModel.isNotBlank()) return state.shadowModel
        val examples = state.point?.let { parseExamples(it.examplesJson) }.orEmpty()
        return examples.firstOrNull().orEmpty()
    }

    private fun parseExamples(examplesJson: String): List<String> {
        val trimmed = examplesJson.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(trimmed)
            .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
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
                        error = NetworkUx.userMessage(e, "ASR 启动失败"),
                        statusMessage = "ASR 不可用",
                    )
                }
            }
        }
    }

    fun onMicReleased() {
        listenJob = viewModelScope.launch {
            runCatching { asrEngine.stop() }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = DrillUiState.Phase.Idle,
                        error = NetworkUx.userMessage(e, "ASR 失败"),
                        statusMessage = "识别失败",
                    )
                }
                return@launch
            }
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
        val snapshot = _uiState.value
        if (snapshot.phase == DrillUiState.Phase.Judging) return
        val point = snapshot.point ?: return
        if (snapshot.mode == DrillUiState.Mode.Shadow) {
            judgeShadow(point.id, trimmed)
            return
        }
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
                        error = NetworkUx.userMessage(e, "判定失败"),
                        statusMessage = if (it.mode == DrillUiState.Mode.Shadow) "跟读判定失败，可再听再试" else "判定失败，可跳过再试",
                    )
                }
            }
        }
    }

    private fun judgeShadow(grammarPointId: String, trimmed: String) {
        val model = shadowModelFor(_uiState.value).take(ShadowingPolicy.MAX_MODEL_CHARS)
        if (model.isBlank()) {
            _uiState.update {
                it.copy(
                    finalTranscript = trimmed,
                    partialTranscript = trimmed,
                    phase = DrillUiState.Phase.Idle,
                    error = "暂无示范句，先返回列表确认语法点",
                )
            }
            return
        }
        val prescreen = ShadowingJudge.prescreen(model, trimmed)
        _uiState.update {
            it.copy(
                finalTranscript = trimmed,
                partialTranscript = trimmed,
                phase = DrillUiState.Phase.Judging,
                statusMessage = "跟读判定中…",
                judge = null,
                offerCollect = false,
                collected = false,
            )
        }
        judgeJob?.cancel()
        judgeJob = viewModelScope.launch {
            runCatching {
                val judge = if (!prescreen.worthLlmJudge) {
                    com.voxcoach.core.domain.model.DrillJudgeResult(
                        hit = false,
                        correction = model,
                        why = "与示范句差距较大（重合约 ${(prescreen.overlap * 100).toInt()}%），先听示范再跟读一遍。",
                        model = model,
                    )
                } else {
                    val cfg = settingsRepository.config.first()
                    val request = ChatRequest(
                        model = cfg.model,
                        messages = listOf(
                            ChatMessage(ChatMessage.Role.SYSTEM, ShadowingJudge.SYSTEM),
                            ChatMessage(
                                ChatMessage.Role.USER,
                                ShadowingJudge.buildJudgePrompt(model, trimmed),
                            ),
                        ),
                        temperature = 0.2,
                        stream = false,
                    )
                    val raw = llmClient.complete(request).content
                    GrammarJudgeParser.parse(raw)
                }
                val attemptId = UUID.randomUUID().toString()
                lastAttemptId = attemptId
                drillAttemptRepository.insert(
                    DrillAttempt(
                        id = attemptId,
                        grammarPointId = grammarPointId,
                        promptId = "shadow",
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
                        statusMessage = if (judge.hit) "跟读通过 ✅" else "再听一遍示范，跟读节奏再试",
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = DrillUiState.Phase.Idle,
                        error = NetworkUx.userMessage(e, "判定失败"),
                        statusMessage = "跟读判定失败，可再听再试",
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
                    why = judge.why,
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
                statusMessage = if (it.mode == DrillUiState.Mode.Shadow) "先听示范，再跟读" else "按住麦克风说一句",
                error = null,
            )
        }
    }
}
