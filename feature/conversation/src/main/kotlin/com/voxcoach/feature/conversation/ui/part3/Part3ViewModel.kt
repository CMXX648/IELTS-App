package com.voxcoach.feature.conversation.ui.part3

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
import com.voxcoach.core.domain.model.TurnRole
import com.voxcoach.core.domain.part2.Part2CueCard
import com.voxcoach.core.domain.part3.Part3Question
import com.voxcoach.core.domain.part3.Part3QuestionBank
import com.voxcoach.core.domain.repository.EvRepository
import com.voxcoach.core.domain.repository.ProfileRepository
import com.voxcoach.core.domain.repository.SessionRepository
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

/**
 * IELTS Part 3 mock (CV-02 P3 slice): discussion questions linked to a Part 2
 * cue theme, TTS examiner lines, ASR press-to-talk, auto-end → EV report.
 */
@HiltViewModel
class Part3ViewModel @Inject constructor(
    private val asrEngine: AsrEngine,
    private val ttsEngine: TtsEngine,
    private val llmClient: LlmClient,
    private val settingsRepository: LlmSettingsRepository,
    private val sessionRepository: SessionRepository,
    private val turnRepository: TurnRepository,
    private val evRepository: EvRepository,
    private val profileRepository: ProfileRepository,
    private val sessionAudioCapture: SessionAudioCapture,
) : ViewModel() {

    private val _uiState = MutableStateFlow(Part3UiState())
    val uiState: StateFlow<Part3UiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var pipelineJob: Job? = null
    private val pendingTurns = mutableListOf<Turn>()
    private var sessionId: String = UUID.randomUUID().toString()
    private var sessionStartedAt: Long = System.currentTimeMillis()
    private var turnSeq: Int = 0
    private var sessionCreated = false
    private var audioPath: String? = null
    private var listenStartedMs: Long? = null
    private var started = false

    init {
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

    fun startIfNeeded() {
        if (started) return
        started = true
        pipelineJob = viewModelScope.launch {
            runCatching {
                val (card, questions) = Part3QuestionBank.pickSession(
                    count = Part3QuestionBank.DEFAULT_QUESTION_COUNT,
                )
                val themeLabel = Part3QuestionBank.themeLabel(card)
                _uiState.update {
                    it.copy(
                        sessionId = sessionId,
                        cueCard = card,
                        questions = questions,
                        totalQuestions = questions.size,
                        themeLabel = themeLabel,
                        phase = Part3UiState.Phase.Preparing,
                        statusMessage = "模拟口试 Part 3 · $themeLabel",
                    )
                }
                ensureSession(card)
                runIntro(card)
                askCurrentQuestion()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = Part3UiState.Phase.AwaitingAnswer,
                        error = NetworkUx.userMessage(e, "Part 3 启动失败"),
                        statusMessage = "启动失败，可返回重试",
                    )
                }
            }
        }
    }

    private fun baseSession(
        card: Part2CueCard? = _uiState.value.cueCard,
        status: SessionStatus,
        endedAt: Long? = null,
        durationMs: Long = 0,
        turnCount: Int = _uiState.value.answeredCount,
        evId: String? = null,
        updatedAt: Long = System.currentTimeMillis(),
    ): Session = Session(
        id = sessionId,
        type = SessionType.CONVERSATION,
        subtype = SessionSubtype.P3,
        topicId = card?.let { "T-p3-${it.category.id}" },
        stage = "S0",
        startedAt = sessionStartedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        turnCount = turnCount,
        audioPath = audioPath,
        evId = evId,
        status = status,
        updatedAt = updatedAt,
    )

    private suspend fun ensureSession(card: Part2CueCard) {
        if (sessionCreated) return
        sessionStartedAt = System.currentTimeMillis()
        sessionRepository.create(baseSession(card = card, status = SessionStatus.ACTIVE))
        startLocalRecording()
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
                    error = "本地录音未启动：${e.message ?: "未知错误"}（口试仍可继续）",
                )
            }
        }
    }

    private suspend fun runIntro(card: Part2CueCard) {
        val intro = Part3QuestionBank.INTRO_TTS
        _uiState.update {
            it.copy(
                phase = Part3UiState.Phase.Intro,
                examinerText = intro,
                statusMessage = "考官引入讨论…",
                error = null,
            )
        }
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
            text = intro,
            textSource = TextSource.EDITED,
            seq = turnSeq,
            startMs = aiStart,
            endMs = aiStart,
            llmMetaJson = """{"source":"part3_intro","cueCardId":"${card.id}"}""",
        )
        pendingTurns += aiTurn
        turnRepository.insert(aiTurn)
        ttsEngine.speak(Sentence(intro), TtsOptions(languageTag = "en-GB"))
    }

    private suspend fun askCurrentQuestion() {
        val state = _uiState.value
        val q = state.currentQuestion ?: run {
            endSession()
            return
        }
        _uiState.update {
            it.copy(
                phase = Part3UiState.Phase.Asking,
                examinerText = q.text,
                partialTranscript = "",
                finalTranscript = "",
                statusMessage = "${it.progressLabel} · 考官提问中…",
                error = null,
            )
        }
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
            text = q.text,
            textSource = TextSource.EDITED,
            seq = turnSeq,
            startMs = aiStart,
            endMs = aiStart,
            llmMetaJson = """{"source":"part3_bank","questionId":"${q.id}"}""",
        )
        pendingTurns += aiTurn
        turnRepository.insert(aiTurn)

        ttsEngine.speak(Sentence(q.text), TtsOptions(languageTag = "en-GB"))

        _uiState.update {
            it.copy(
                phase = Part3UiState.Phase.AwaitingAnswer,
                statusMessage = "${it.progressLabel} · 按住麦克风作答",
            )
        }
    }

    fun onMicPressed() {
        val phase = _uiState.value.phase
        if (_uiState.value.ending ||
            phase == Part3UiState.Phase.Evaluating ||
            phase == Part3UiState.Phase.Asking ||
            phase == Part3UiState.Phase.Preparing ||
            phase == Part3UiState.Phase.Intro ||
            phase == Part3UiState.Phase.Saving ||
            phase == Part3UiState.Phase.Done
        ) {
            return
        }
        listenJob?.cancel()
        viewModelScope.launch { ttsEngine.stopAll() }
        listenStartedMs = if (sessionAudioCapture.isRecording) sessionAudioCapture.elapsedMs() else null
        _uiState.update {
            it.copy(
                phase = Part3UiState.Phase.Listening,
                partialTranscript = "",
                finalTranscript = "",
                statusMessage = "${it.progressLabel} · 正在听…",
                error = null,
            )
        }
        listenJob = viewModelScope.launch {
            runCatching {
                asrEngine.start(AsrSessionConfig(languageTag = "en-GB"))
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = Part3UiState.Phase.AwaitingAnswer,
                        error = NetworkUx.userMessage(e, "ASR 启动失败"),
                        statusMessage = "${it.progressLabel} · ASR 不可用",
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
                        phase = Part3UiState.Phase.AwaitingAnswer,
                        error = NetworkUx.userMessage(e, "ASR 失败"),
                        statusMessage = "${it.progressLabel} · 识别失败",
                    )
                }
                return@launch
            }
            val current = _uiState.value
            val text = current.finalTranscript.ifBlank { current.partialTranscript }
            if (text.isNotBlank() && current.phase == Part3UiState.Phase.Listening) {
                onUserFinal(text)
            } else if (text.isBlank() && current.phase == Part3UiState.Phase.Listening) {
                listenStartedMs = null
                _uiState.update {
                    it.copy(
                        phase = Part3UiState.Phase.AwaitingAnswer,
                        statusMessage = "${it.progressLabel} · 未识别到内容，请再试",
                    )
                }
            }
        }
    }

    private fun onUserFinal(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val phase = _uiState.value.phase
        if (_uiState.value.ending || phase != Part3UiState.Phase.Listening) {
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
                phase = Part3UiState.Phase.Saving,
                statusMessage = "${it.progressLabel} · 记录中…",
            )
        }
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching {
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

                val answered = _uiState.value.answeredCount + 1
                sessionRepository.update(
                    baseSession(
                        status = SessionStatus.ACTIVE,
                        turnCount = answered,
                    ),
                )
                _uiState.update { it.copy(answeredCount = answered) }

                val nextIndex = _uiState.value.questionIndex + 1
                if (nextIndex >= _uiState.value.totalQuestions) {
                    endSessionInternal()
                } else {
                    _uiState.update { it.copy(questionIndex = nextIndex) }
                    askCurrentQuestion()
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = Part3UiState.Phase.AwaitingAnswer,
                        error = NetworkUx.userMessage(e, "保存失败"),
                        statusMessage = "${it.progressLabel} · 出错了，请再试",
                    )
                }
            }
        }
    }

    fun endSession() {
        if (_uiState.value.ending) return
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching { endSessionInternal() }
                .onFailure { e ->
                    runCatching { sessionAudioCapture.stop() }
                    _uiState.update {
                        it.copy(
                            ending = false,
                            recording = false,
                            phase = Part3UiState.Phase.AwaitingAnswer,
                            error = NetworkUx.userMessage(e, "评测失败"),
                            statusMessage = "评测失败，可稍后重试结束",
                        )
                    }
                }
        }
    }

    private suspend fun endSessionInternal() {
        _uiState.update {
            it.copy(
                ending = true,
                phase = Part3UiState.Phase.Evaluating,
                statusMessage = "生成报告中…",
            )
        }
        ttsEngine.stopAll()
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
        val card = _uiState.value.cueCard
        val topicTitle =
            "Part 3 · ${_uiState.value.themeLabel} · linked cue: ${card?.title ?: ""}"
        val evRequest = ChatRequest(
            model = cfg.model,
            messages = listOf(
                ChatMessage(ChatMessage.Role.SYSTEM, EvPrompt.SYSTEM),
                ChatMessage(
                    ChatMessage.Role.USER,
                    EvPrompt.userTranscript(topicTitle, transcriptPairs),
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
                answeredCount = userCount,
                navigateToReportSessionId = sessionId,
                phase = Part3UiState.Phase.Done,
                statusMessage = "报告已生成",
            )
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(navigateToReportSessionId = null) }
    }

    override fun onCleared() {
        runCatching { sessionAudioCapture.release() }
        super.onCleared()
    }
}
