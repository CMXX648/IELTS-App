package com.voxcoach.feature.conversation.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.ev.EvJsonParser
import com.voxcoach.core.domain.ev.EvPrompt
import com.voxcoach.core.domain.llm.LlmClient
import com.voxcoach.core.domain.model.AsrSessionConfig
import com.voxcoach.core.domain.model.ChatMessage
import com.voxcoach.core.domain.model.ChatRequest
import com.voxcoach.core.domain.model.Sentence
import com.voxcoach.core.domain.model.Session
import com.voxcoach.core.domain.model.SessionStatus
import com.voxcoach.core.domain.model.SessionSubtype
import com.voxcoach.core.domain.model.SessionType
import com.voxcoach.core.domain.model.TextSource
import com.voxcoach.core.domain.model.TtsOptions
import com.voxcoach.core.domain.model.Turn
import com.voxcoach.core.domain.model.TurnLatency
import com.voxcoach.core.domain.model.TurnRole
import com.voxcoach.core.domain.repository.EvRepository
import com.voxcoach.core.domain.repository.ProfileRepository
import com.voxcoach.core.domain.repository.SessionRepository
import com.voxcoach.core.domain.repository.TopicRepository
import com.voxcoach.core.domain.repository.TurnRepository
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

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val asrEngine: AsrEngine,
    private val ttsEngine: TtsEngine,
    private val llmClient: LlmClient,
    private val settingsRepository: LlmSettingsRepository,
    private val sessionRepository: SessionRepository,
    private val turnRepository: TurnRepository,
    private val evRepository: EvRepository,
    private val topicRepository: TopicRepository,
    private val profileRepository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val topicIdArg: String = savedStateHandle["topicId"] ?: "T-hometown"
    private val sessionIdArg: String? = savedStateHandle["sessionId"]

    private val _uiState = MutableStateFlow(ConversationUiState(topicId = topicIdArg))
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var pipelineJob: Job? = null
    private val history = mutableListOf<ChatMessage>()
    private val pendingTurns = mutableListOf<Turn>()
    private var sessionId: String = sessionIdArg ?: UUID.randomUUID().toString()
    private var sessionStartedAt: Long = System.currentTimeMillis()
    private var turnSeq: Int = 0
    private var sessionCreated = false

    init {
        viewModelScope.launch {
            val topic = topicRepository.getTopic(topicIdArg)
            _uiState.update {
                it.copy(
                    sessionId = sessionId,
                    topicId = topicIdArg,
                    topicTitle = topic?.title ?: "Hometown",
                )
            }
            ensureSession()
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

    private suspend fun ensureSession() {
        if (sessionCreated) return
        val existing = sessionRepository.get(sessionId)
        if (existing == null) {
            sessionStartedAt = System.currentTimeMillis()
            sessionRepository.create(
                Session(
                    id = sessionId,
                    type = SessionType.CONVERSATION,
                    subtype = SessionSubtype.FREE,
                    topicId = topicIdArg,
                    startedAt = sessionStartedAt,
                    status = SessionStatus.ACTIVE,
                ),
            )
        } else {
            sessionStartedAt = existing.startedAt
            turnSeq = existing.turnCount
        }
        sessionCreated = true
        _uiState.update { it.copy(sessionId = sessionId) }
    }

    fun onMicPressed() {
        if (_uiState.value.ending || _uiState.value.phase == ConversationUiState.Phase.Evaluating) return
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
            val text = current.finalTranscript.ifBlank { current.partialTranscript }
            if (text.isNotBlank() && current.phase == ConversationUiState.Phase.Listening) {
                onUserFinal(text)
            } else if (text.isBlank() && current.phase == ConversationUiState.Phase.Listening) {
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
        val phase = _uiState.value.phase
        if (phase == ConversationUiState.Phase.Thinking ||
            phase == ConversationUiState.Phase.Speaking ||
            phase == ConversationUiState.Phase.Evaluating ||
            _uiState.value.ending
        ) {
            return
        }
        val asrFinalAt = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                finalTranscript = trimmed,
                partialTranscript = trimmed,
                phase = ConversationUiState.Phase.Thinking,
                statusMessage = "思考中…",
                assistantText = "",
                lastLatency = TurnLatency(asrFinalAt = asrFinalAt),
            )
        }
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching {
                ensureSession()
                val cfg = settingsRepository.config.first()
                if (history.none { it.role == ChatMessage.Role.SYSTEM }) {
                    history += ChatMessage(
                        role = ChatMessage.Role.SYSTEM,
                        content = SYSTEM_PROMPT + " Topic focus: ${_uiState.value.topicTitle}.",
                    )
                }
                turnSeq += 1
                val userTurn = Turn(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = TurnRole.USER,
                    text = trimmed,
                    textSource = TextSource.ASR_RAW,
                    seq = turnSeq,
                    startMs = asrFinalAt - sessionStartedAt,
                    endMs = asrFinalAt - sessionStartedAt,
                )
                pendingTurns += userTurn
                turnRepository.insert(userTurn)

                history += ChatMessage(ChatMessage.Role.USER, trimmed)
                val request = ChatRequest(
                    model = cfg.model,
                    messages = history.toList(),
                    stream = true,
                )
                val sb = StringBuilder()
                var llmFirstTokenAt = 0L
                llmClient.streamChat(request).collect { delta ->
                    if (delta.content.isNotEmpty()) {
                        if (llmFirstTokenAt == 0L) {
                            llmFirstTokenAt = System.currentTimeMillis()
                            _uiState.update {
                                it.copy(
                                    lastLatency = (it.lastLatency ?: TurnLatency(asrFinalAt = asrFinalAt))
                                        .copy(llmFirstTokenAt = llmFirstTokenAt),
                                )
                            }
                        }
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
                turnSeq += 1
                val aiTurn = Turn(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = TurnRole.AI,
                    text = full,
                    textSource = TextSource.EDITED,
                    seq = turnSeq,
                    startMs = System.currentTimeMillis() - sessionStartedAt,
                )
                pendingTurns += aiTurn
                turnRepository.insert(aiTurn)

                val userTurnPairs = pendingTurns.count { it.role == TurnRole.USER }
                sessionRepository.update(
                    Session(
                        id = sessionId,
                        type = SessionType.CONVERSATION,
                        subtype = SessionSubtype.FREE,
                        topicId = topicIdArg,
                        startedAt = sessionStartedAt,
                        turnCount = userTurnPairs,
                        status = SessionStatus.ACTIVE,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )

                val ttsStartAt = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        phase = ConversationUiState.Phase.Speaking,
                        statusMessage = "播放中…",
                        assistantText = full,
                        turnCount = userTurnPairs,
                        lastLatency = (it.lastLatency ?: TurnLatency(asrFinalAt = asrFinalAt)).copy(
                            llmFirstTokenAt = llmFirstTokenAt,
                            ttsStartAt = ttsStartAt,
                        ),
                    )
                }
                if (full.isNotBlank()) {
                    ttsEngine.speak(Sentence(full), TtsOptions(languageTag = "en-GB"))
                }
                _uiState.update {
                    it.copy(
                        phase = ConversationUiState.Phase.Idle,
                        statusMessage = "按住麦克风继续 · 第 ${it.turnCount} 轮",
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

    fun endSession() {
        if (_uiState.value.ending) return
        _uiState.update {
            it.copy(
                ending = true,
                phase = ConversationUiState.Phase.Evaluating,
                statusMessage = "生成报告中…",
            )
        }
        viewModelScope.launch {
            runCatching {
                ttsEngine.stopAll()
                ensureSession()
                val endedAt = System.currentTimeMillis()
                val duration = endedAt - sessionStartedAt
                val turns = turnRepository.listForSession(sessionId).ifEmpty { pendingTurns.toList() }
                val userCount = turns.count { it.role == TurnRole.USER }
                sessionRepository.update(
                    Session(
                        id = sessionId,
                        type = SessionType.CONVERSATION,
                        subtype = SessionSubtype.FREE,
                        topicId = topicIdArg,
                        startedAt = sessionStartedAt,
                        endedAt = endedAt,
                        durationMs = duration,
                        turnCount = userCount,
                        status = SessionStatus.EVALUATING,
                        updatedAt = endedAt,
                    ),
                )

                val cfg = settingsRepository.config.first()
                val transcriptPairs = turns.map { t ->
                    (if (t.role == TurnRole.USER) "Candidate" else "Examiner") to t.text
                }
                val evRequest = ChatRequest(
                    model = cfg.model,
                    messages = listOf(
                        ChatMessage(ChatMessage.Role.SYSTEM, EvPrompt.SYSTEM),
                        ChatMessage(
                            ChatMessage.Role.USER,
                            EvPrompt.userTranscript(_uiState.value.topicTitle, transcriptPairs),
                        ),
                    ),
                    temperature = 0.3,
                    stream = false,
                )
                val evRaw = llmClient.complete(evRequest).content
                val evId = UUID.randomUUID().toString()
                val ev = EvJsonParser.parse(evRaw, sessionId = sessionId, evId = evId)
                evRepository.save(ev)
                sessionRepository.update(
                    Session(
                        id = sessionId,
                        type = SessionType.CONVERSATION,
                        subtype = SessionSubtype.FREE,
                        topicId = topicIdArg,
                        startedAt = sessionStartedAt,
                        endedAt = endedAt,
                        durationMs = duration,
                        turnCount = userCount,
                        evId = evId,
                        status = SessionStatus.DONE,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                profileRepository.addPractice(durationMs = duration, turnCount = userCount)
                _uiState.update {
                    it.copy(
                        ending = false,
                        endedEvId = evId,
                        navigateToReportSessionId = sessionId,
                        phase = ConversationUiState.Phase.Idle,
                        statusMessage = "报告已生成",
                        turnCount = userCount,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        ending = false,
                        phase = ConversationUiState.Phase.Idle,
                        error = e.message ?: "评测失败",
                        statusMessage = "评测失败，可稍后重试结束会话",
                    )
                }
            }
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(navigateToReportSessionId = null) }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a friendly IELTS Speaking examiner. Reply in clear English, " +
                "ask one follow-up question, keep answers under 3 sentences."
    }
}
