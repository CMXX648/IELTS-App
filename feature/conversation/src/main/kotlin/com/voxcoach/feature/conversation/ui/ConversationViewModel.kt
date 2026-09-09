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
import com.voxcoach.core.domain.speech.SessionAudioCapture
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
    private val sessionAudioCapture: SessionAudioCapture,
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
    private var audioPath: String? = null
    private var listenStartedMs: Long? = null

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

    private fun baseSession(
        status: SessionStatus,
        endedAt: Long? = null,
        durationMs: Long = 0,
        turnCount: Int = _uiState.value.turnCount,
        evId: String? = null,
        updatedAt: Long = System.currentTimeMillis(),
    ): Session = Session(
        id = sessionId,
        type = SessionType.CONVERSATION,
        subtype = SessionSubtype.FREE,
        topicId = topicIdArg,
        startedAt = sessionStartedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        turnCount = turnCount,
        audioPath = audioPath,
        evId = evId,
        status = status,
        updatedAt = updatedAt,
    )

    private suspend fun ensureSession() {
        if (sessionCreated) return
        val existing = sessionRepository.get(sessionId)
        if (existing == null) {
            sessionStartedAt = System.currentTimeMillis()
            sessionRepository.create(baseSession(status = SessionStatus.ACTIVE))
            startLocalRecording()
        } else {
            sessionStartedAt = existing.startedAt
            turnSeq = existing.turnCount
            audioPath = existing.audioPath
            if (existing.status == SessionStatus.ACTIVE && !sessionAudioCapture.isRecording) {
                startLocalRecording()
            }
        }
        sessionCreated = true
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                recording = sessionAudioCapture.isRecording,
            )
        }
    }

    private suspend fun startLocalRecording() {
        runCatching {
            val path = sessionAudioCapture.start(sessionId)
            audioPath = path
            sessionRepository.update(baseSession(status = SessionStatus.ACTIVE))
            _uiState.update { it.copy(recording = true, audioPath = path) }
        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    recording = false,
                    error = "本地录音未启动：${e.message ?: "未知错误"}（对话仍可继续）",
                )
            }
        }
    }

    fun onMicPressed() {
        if (_uiState.value.ending || _uiState.value.phase == ConversationUiState.Phase.Evaluating) return
        pipelineJob?.cancel()
        listenJob?.cancel()
        viewModelScope.launch { ttsEngine.stopAll() }
        listenStartedMs = if (sessionAudioCapture.isRecording) sessionAudioCapture.elapsedMs() else null
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
                        error = NetworkUx.userMessage(e, "ASR 启动失败"),
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
                listenStartedMs = null
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
        val turnStartMs = listenStartedMs
            ?: if (sessionAudioCapture.isRecording) sessionAudioCapture.elapsedMs() else asrFinalAt - sessionStartedAt
        val turnEndMs = if (sessionAudioCapture.isRecording) {
            sessionAudioCapture.elapsedMs()
        } else {
            asrFinalAt - sessionStartedAt
        }
        listenStartedMs = null
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
                    startMs = turnStartMs,
                    endMs = turnEndMs,
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
                val aiStart = if (sessionAudioCapture.isRecording) {
                    sessionAudioCapture.elapsedMs()
                } else {
                    System.currentTimeMillis() - sessionStartedAt
                }
                val aiTurn = Turn(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = TurnRole.AI,
                    text = full,
                    textSource = TextSource.EDITED,
                    seq = turnSeq,
                    startMs = aiStart,
                    endMs = aiStart,
                )
                pendingTurns += aiTurn
                turnRepository.insert(aiTurn)

                val userTurnPairs = pendingTurns.count { it.role == TurnRole.USER }
                sessionRepository.update(
                    baseSession(
                        status = SessionStatus.ACTIVE,
                        turnCount = userTurnPairs,
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
                        error = NetworkUx.userMessage(e, "链路失败"),
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
                val path = sessionAudioCapture.stop()
                if (path != null) audioPath = path
                _uiState.update { it.copy(recording = false, audioPath = audioPath) }

                val endedAt = System.currentTimeMillis()
                val duration = endedAt - sessionStartedAt
                val turns = turnRepository.listForSession(sessionId).ifEmpty { pendingTurns.toList() }
                val userCount = turns.count { it.role == TurnRole.USER }
                sessionRepository.update(
                    baseSession(
                        status = SessionStatus.EVALUATING,
                        endedAt = endedAt,
                        durationMs = duration,
                        turnCount = userCount,
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
                    baseSession(
                        status = SessionStatus.DONE,
                        endedAt = endedAt,
                        durationMs = duration,
                        turnCount = userCount,
                        evId = evId,
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
                runCatching { sessionAudioCapture.stop() }
                _uiState.update {
                    it.copy(
                        ending = false,
                        recording = false,
                        phase = ConversationUiState.Phase.Idle,
                        error = NetworkUx.userMessage(e, "评测失败"),
                        statusMessage = "评测失败，本地轮次与录音已保留，可稍后重试",
                    )
                }
            }
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(navigateToReportSessionId = null) }
    }

    override fun onCleared() {
        runCatching { sessionAudioCapture.release() }
        super.onCleared()
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a friendly IELTS Speaking examiner. Reply in clear English, " +
                "ask one follow-up question, keep answers under 3 sentences."
    }
}
