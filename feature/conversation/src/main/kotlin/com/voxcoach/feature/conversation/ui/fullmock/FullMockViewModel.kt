package com.voxcoach.feature.conversation.ui.fullmock

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
import com.voxcoach.core.domain.part1.Part1QuestionBank
import com.voxcoach.core.domain.part2.Part2CueCard
import com.voxcoach.core.domain.part2.Part2CueCardBank
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full IELTS Speaking mock: Part1 (5Q) → Part2 (prep+long turn) → Part3 (5Q)
 * in one [SessionSubtype.MOCK] session; single EV at the very end.
 */
@HiltViewModel
class FullMockViewModel @Inject constructor(
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

    private val _uiState = MutableStateFlow(FullMockUiState())
    val uiState: StateFlow<FullMockUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var pipelineJob: Job? = null
    private var speakTimerJob: Job? = null
    private var asrListenJob: Job? = null
    private val pendingTurns = mutableListOf<Turn>()
    private var sessionId: String = UUID.randomUUID().toString()
    private var sessionStartedAt: Long = System.currentTimeMillis()
    private var turnSeq: Int = 0
    private var userTurnCount: Int = 0
    private var sessionCreated = false
    private var audioPath: String? = null
    private var listenStartedMs: Long? = null
    private var speakStartedMs: Long? = null
    private var started = false
    private var finishingSpeak = false
    private var speakPhaseEntered = false
    private val asrChunks = mutableListOf<String>()

    init {
        viewModelScope.launch {
            asrEngine.partialResults.collect { partial ->
                val st = _uiState.value
                when {
                    st.stage == FullMockUiState.Stage.Part2 &&
                        st.phase == FullMockUiState.Phase.P2Speaking -> {
                        val base = asrChunks.joinToString(" ").trim()
                        val shown = if (base.isBlank()) partial.text else "$base ${partial.text}".trim()
                        _uiState.update { it.copy(partialTranscript = shown) }
                    }
                    st.phase == FullMockUiState.Phase.Listening -> {
                        _uiState.update { it.copy(partialTranscript = partial.text) }
                    }
                }
            }
        }
        viewModelScope.launch {
            asrEngine.finals.collect { final ->
                val st = _uiState.value
                when {
                    st.stage == FullMockUiState.Stage.Part2 &&
                        st.phase == FullMockUiState.Phase.P2Speaking -> {
                        val trimmed = final.text.trim()
                        if (trimmed.isEmpty()) return@collect
                        if (asrChunks.lastOrNull() != trimmed) asrChunks += trimmed
                        val joined = asrChunks.joinToString(" ").trim()
                        _uiState.update {
                            it.copy(partialTranscript = joined, finalTranscript = joined)
                        }
                        if (!finishingSpeak &&
                            _uiState.value.phase == FullMockUiState.Phase.P2Speaking
                        ) {
                            restartAsrListen()
                        }
                    }
                    st.phase == FullMockUiState.Phase.Listening -> onQaUserFinal(final.text)
                }
            }
        }
    }

    fun startIfNeeded() {
        if (started) return
        started = true
        pipelineJob = viewModelScope.launch {
            runCatching {
                val p1 = Part1QuestionBank.pickSession(count = Part1QuestionBank.DEFAULT_QUESTION_COUNT)
                val theme = Part1QuestionBank.themeLabel(p1)
                _uiState.update {
                    it.copy(
                        sessionId = sessionId,
                        stage = FullMockUiState.Stage.Part1,
                        phase = FullMockUiState.Phase.Preparing,
                        part1Questions = p1,
                        part1ThemeLabel = theme,
                        questionIndex = 0,
                        totalQuestions = p1.size,
                        answeredInStage = 0,
                        statusMessage = "${it.stageLabelZh} · $theme",
                    )
                }
                ensureSession(topicId = "T-${p1.firstOrNull()?.themeId ?: "hometown"}")
                askPart1Question()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        error = e.message ?: "完整模考启动失败",
                        statusMessage = "启动失败，可返回重试",
                        phase = FullMockUiState.Phase.AwaitingAnswer,
                    )
                }
            }
        }
    }

    private fun baseSession(
        topicId: String? = _uiState.value.cueCard?.topicId()
            ?: _uiState.value.part1Questions.firstOrNull()?.let { "T-${it.themeId}" },
        status: SessionStatus,
        endedAt: Long? = null,
        durationMs: Long = 0,
        turnCount: Int = userTurnCount,
        evId: String? = null,
        updatedAt: Long = System.currentTimeMillis(),
    ): Session = Session(
        id = sessionId,
        type = SessionType.CONVERSATION,
        subtype = SessionSubtype.MOCK,
        topicId = topicId,
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

    private suspend fun ensureSession(topicId: String) {
        if (sessionCreated) return
        sessionStartedAt = System.currentTimeMillis()
        sessionRepository.create(
            baseSession(topicId = topicId, status = SessionStatus.ACTIVE),
        )
        startLocalRecording()
        sessionCreated = true
        _uiState.update {
            it.copy(sessionId = sessionId, recording = sessionAudioCapture.isRecording)
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
                    error = "本地录音未启动：${e.message ?: "未知错误"}（模考仍可继续）",
                )
            }
        }
    }

    private suspend fun persistAiTurn(text: String, metaJson: String) {
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
            text = text,
            textSource = TextSource.EDITED,
            seq = turnSeq,
            startMs = aiStart,
            endMs = aiStart,
            llmMetaJson = metaJson,
        )
        pendingTurns += aiTurn
        turnRepository.insert(aiTurn)
    }

    // region Part 1
    private suspend fun askPart1Question() {
        val q = _uiState.value.currentPart1Question ?: run {
            beginPart2()
            return
        }
        _uiState.update {
            it.copy(
                stage = FullMockUiState.Stage.Part1,
                phase = FullMockUiState.Phase.Asking,
                examinerText = q.text,
                partialTranscript = "",
                finalTranscript = "",
                statusMessage = "${it.stageLabelZh} · ${it.progressLabel} · 考官提问中…",
                error = null,
            )
        }
        persistAiTurn(q.text, """{"source":"mock_p1","questionId":"${q.id}"}""")
        ttsEngine.speak(Sentence(q.text), TtsOptions(languageTag = "en-GB"))
        _uiState.update {
            it.copy(
                phase = FullMockUiState.Phase.AwaitingAnswer,
                statusMessage = "${it.stageLabelZh} · ${it.progressLabel} · 按住麦克风作答",
            )
        }
    }
    // endregion

    // region Part 2
    private suspend fun beginPart2() {
        val card = Part2CueCardBank.pick()
        speakPhaseEntered = false
        finishingSpeak = false
        _uiState.update {
            it.copy(
                stage = FullMockUiState.Stage.Part2,
                phase = FullMockUiState.Phase.P2Intro,
                cueCard = card,
                questionIndex = 0,
                totalQuestions = 1,
                answeredInStage = 0,
                examinerText = Part2CueCardBank.INTRO_TTS,
                partialTranscript = "",
                finalTranscript = "",
                notesDraft = "",
                countdownSec = Part2CueCardBank.PREP_SECONDS,
                statusMessage = "${it.copy(stage = FullMockUiState.Stage.Part2).stageLabelZh} · 考官说明中…",
                error = null,
            )
        }
        sessionRepository.update(
            baseSession(topicId = card.topicId(), status = SessionStatus.ACTIVE),
        )
        persistAiTurn(
            Part2CueCardBank.INTRO_TTS,
            """{"source":"mock_p2_intro","cueCardId":"${card.id}"}""",
        )
        ttsEngine.speak(Sentence(Part2CueCardBank.INTRO_TTS), TtsOptions(languageTag = "en-GB"))
        runPrepCountdown(card)
    }

    private suspend fun runPrepCountdown(card: Part2CueCard) {
        _uiState.update {
            it.copy(
                phase = FullMockUiState.Phase.P2PrepCountdown,
                examinerText = card.title,
                countdownSec = Part2CueCardBank.PREP_SECONDS,
                statusMessage = "${it.stageLabelZh} · 准备倒计时",
            )
        }
        for (left in Part2CueCardBank.PREP_SECONDS downTo 1) {
            if (_uiState.value.phase != FullMockUiState.Phase.P2PrepCountdown) return
            _uiState.update {
                it.copy(
                    countdownSec = left,
                    statusMessage = "${it.stageLabelZh} · 准备倒计时 ${left}s",
                )
            }
            delay(1_000)
        }
        if (_uiState.value.phase != FullMockUiState.Phase.P2PrepCountdown) return
        _uiState.update { it.copy(countdownSec = 0) }
        runSpeakPrompt(card)
    }

    fun skipPrep() {
        val card = _uiState.value.cueCard ?: return
        if (_uiState.value.phase != FullMockUiState.Phase.P2PrepCountdown) return
        _uiState.update {
            it.copy(
                phase = FullMockUiState.Phase.P2SpeakPrompt,
                countdownSec = 0,
                statusMessage = "${it.stageLabelZh} · 跳过准备，即将独白…",
            )
        }
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching { runSpeakPrompt(card) }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "进入独白失败") }
                }
        }
    }

    private suspend fun runSpeakPrompt(card: Part2CueCard) {
        if (speakPhaseEntered) return
        val prompt = Part2CueCardBank.SPEAK_PROMPT_TTS
        _uiState.update {
            it.copy(
                phase = FullMockUiState.Phase.P2SpeakPrompt,
                examinerText = prompt,
                statusMessage = "${it.stageLabelZh} · 考官提示开始独白…",
                countdownSec = Part2CueCardBank.SPEAK_SECONDS,
            )
        }
        persistAiTurn(prompt, """{"source":"mock_p2_speak_prompt","cueCardId":"${card.id}"}""")
        ttsEngine.speak(Sentence(prompt), TtsOptions(languageTag = "en-GB"))
        beginSpeaking()
    }

    private fun beginSpeaking() {
        if (speakPhaseEntered) return
        speakPhaseEntered = true
        finishingSpeak = false
        asrChunks.clear()
        speakStartedMs = if (sessionAudioCapture.isRecording) {
            sessionAudioCapture.elapsedMs()
        } else {
            System.currentTimeMillis() - sessionStartedAt
        }
        _uiState.update {
            it.copy(
                phase = FullMockUiState.Phase.P2Speaking,
                partialTranscript = "",
                finalTranscript = "",
                countdownSec = Part2CueCardBank.SPEAK_SECONDS,
                statusMessage = "${it.stageLabelZh} · 独白中 · 说完点「说完了」",
                asrListening = false,
                error = null,
            )
        }
        startAsrListen()
        speakTimerJob?.cancel()
        speakTimerJob = viewModelScope.launch {
            for (left in Part2CueCardBank.SPEAK_SECONDS downTo 1) {
                if (!isActive) return@launch
                if (_uiState.value.phase != FullMockUiState.Phase.P2Speaking) return@launch
                _uiState.update {
                    it.copy(
                        countdownSec = left,
                        statusMessage = "${it.stageLabelZh} · 独白剩余 ${left}s",
                    )
                }
                delay(1_000)
            }
            if (!isActive) return@launch
            if (_uiState.value.phase != FullMockUiState.Phase.P2Speaking) return@launch
            _uiState.update { it.copy(countdownSec = 0) }
            runCatching { finishSpeakingInternal() }
        }
    }

    private fun startAsrListen() {
        asrListenJob?.cancel()
        asrListenJob = viewModelScope.launch {
            runCatching {
                _uiState.update { it.copy(asrListening = true) }
                asrEngine.start(AsrSessionConfig(languageTag = "en-GB"))
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        asrListening = false,
                        error = e.message ?: "ASR 启动失败（本地录音仍在继续）",
                    )
                }
            }
        }
    }

    private fun restartAsrListen() {
        asrListenJob?.cancel()
        asrListenJob = viewModelScope.launch {
            runCatching { asrEngine.stop() }
            delay(200)
            if (finishingSpeak || _uiState.value.phase != FullMockUiState.Phase.P2Speaking) {
                return@launch
            }
            runCatching {
                _uiState.update { it.copy(asrListening = true) }
                asrEngine.start(AsrSessionConfig(languageTag = "en-GB"))
            }.onFailure { e ->
                _uiState.update {
                    it.copy(asrListening = false, error = e.message ?: "ASR 重启失败")
                }
            }
        }
    }

    fun onNotesChanged(text: String) {
        _uiState.update { it.copy(notesDraft = text) }
    }

    fun finishSpeaking() {
        if (_uiState.value.phase != FullMockUiState.Phase.P2Speaking) return
        if (finishingSpeak) return
        speakTimerJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching { finishSpeakingInternal() }
                .onFailure { e ->
                    finishingSpeak = false
                    _uiState.update {
                        it.copy(
                            phase = FullMockUiState.Phase.P2Speaking,
                            error = e.message ?: "结束独白失败",
                            statusMessage = "${it.stageLabelZh} · 结束失败，可再试「说完了」",
                        )
                    }
                }
        }
    }

    private suspend fun finishSpeakingInternal() {
        if (finishingSpeak) return
        finishingSpeak = true
        speakTimerJob?.cancel()
        _uiState.update {
            it.copy(
                phase = FullMockUiState.Phase.P2Saving,
                statusMessage = "${it.stageLabelZh} · 记录独白中…",
                asrListening = false,
            )
        }
        runCatching { asrEngine.stop() }
        asrListenJob?.cancel()
        delay(150)

        val text = _uiState.value.finalTranscript
            .ifBlank { _uiState.value.partialTranscript }
            .ifBlank { asrChunks.joinToString(" ").trim() }
            .ifBlank { "(no speech recognised)" }

        val turnStartMs = speakStartedMs
            ?: if (sessionAudioCapture.isRecording) sessionAudioCapture.elapsedMs() else 0L
        val turnEndMs = if (sessionAudioCapture.isRecording) {
            sessionAudioCapture.elapsedMs()
        } else {
            System.currentTimeMillis() - sessionStartedAt
        }

        turnSeq += 1
        val card = _uiState.value.cueCard
        val userTurn = Turn(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = TurnRole.USER,
            text = text,
            textSource = TextSource.ASR_RAW,
            seq = turnSeq,
            startMs = turnStartMs,
            endMs = turnEndMs,
            llmMetaJson = card?.let {
                """{"source":"mock_p2_long_turn","cueCardId":"${it.id}"}"""
            },
        )
        pendingTurns += userTurn
        turnRepository.insert(userTurn)
        userTurnCount += 1
        sessionRepository.update(
            baseSession(status = SessionStatus.ACTIVE, turnCount = userTurnCount),
        )
        _uiState.update {
            it.copy(finalTranscript = text, partialTranscript = text, answeredInStage = 1)
        }
        beginPart3()
    }
    // endregion

    // region Part 3
    private suspend fun beginPart3() {
        val card = _uiState.value.cueCard ?: Part2CueCardBank.pick()
        val questions = Part3QuestionBank.pickForCueCard(
            card,
            count = Part3QuestionBank.DEFAULT_QUESTION_COUNT,
        )
        val theme = Part3QuestionBank.themeLabel(card)
        _uiState.update {
            it.copy(
                stage = FullMockUiState.Stage.Part3,
                phase = FullMockUiState.Phase.Intro,
                part3Questions = questions,
                part3ThemeLabel = theme,
                cueCard = card,
                questionIndex = 0,
                totalQuestions = questions.size,
                answeredInStage = 0,
                examinerText = Part3QuestionBank.INTRO_TTS,
                partialTranscript = "",
                finalTranscript = "",
                statusMessage = "${it.copy(stage = FullMockUiState.Stage.Part3).stageLabelZh} · 考官引入讨论…",
                asrListening = false,
                error = null,
            )
        }
        persistAiTurn(
            Part3QuestionBank.INTRO_TTS,
            """{"source":"mock_p3_intro","cueCardId":"${card.id}"}""",
        )
        ttsEngine.speak(Sentence(Part3QuestionBank.INTRO_TTS), TtsOptions(languageTag = "en-GB"))
        askPart3Question()
    }

    private suspend fun askPart3Question() {
        val q = _uiState.value.currentPart3Question ?: run {
            endSessionInternal()
            return
        }
        _uiState.update {
            it.copy(
                stage = FullMockUiState.Stage.Part3,
                phase = FullMockUiState.Phase.Asking,
                examinerText = q.text,
                partialTranscript = "",
                finalTranscript = "",
                statusMessage = "${it.stageLabelZh} · ${it.progressLabel} · 考官提问中…",
                error = null,
            )
        }
        persistAiTurn(q.text, """{"source":"mock_p3","questionId":"${q.id}"}""")
        ttsEngine.speak(Sentence(q.text), TtsOptions(languageTag = "en-GB"))
        _uiState.update {
            it.copy(
                phase = FullMockUiState.Phase.AwaitingAnswer,
                statusMessage = "${it.stageLabelZh} · ${it.progressLabel} · 按住麦克风作答",
            )
        }
    }
    // endregion

    // region Q&A mic (Part1 / Part3)
    fun onMicPressed() {
        val st = _uiState.value
        if (st.ending) return
        if (st.stage != FullMockUiState.Stage.Part1 && st.stage != FullMockUiState.Stage.Part3) return
        if (st.phase != FullMockUiState.Phase.AwaitingAnswer &&
            st.phase != FullMockUiState.Phase.Listening
        ) {
            return
        }
        listenJob?.cancel()
        viewModelScope.launch { ttsEngine.stopAll() }
        listenStartedMs = if (sessionAudioCapture.isRecording) sessionAudioCapture.elapsedMs() else null
        _uiState.update {
            it.copy(
                phase = FullMockUiState.Phase.Listening,
                partialTranscript = "",
                finalTranscript = "",
                statusMessage = "${it.stageLabelZh} · ${it.progressLabel} · 正在听…",
                error = null,
            )
        }
        listenJob = viewModelScope.launch {
            runCatching {
                asrEngine.start(AsrSessionConfig(languageTag = "en-GB"))
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = FullMockUiState.Phase.AwaitingAnswer,
                        error = e.message ?: "ASR 启动失败",
                        statusMessage = "${it.stageLabelZh} · ASR 不可用",
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
            if (text.isNotBlank() && current.phase == FullMockUiState.Phase.Listening) {
                onQaUserFinal(text)
            } else if (text.isBlank() && current.phase == FullMockUiState.Phase.Listening) {
                listenStartedMs = null
                _uiState.update {
                    it.copy(
                        phase = FullMockUiState.Phase.AwaitingAnswer,
                        statusMessage = "${it.stageLabelZh} · ${it.progressLabel} · 未识别到内容，请再试",
                    )
                }
            }
        }
    }

    private fun onQaUserFinal(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val st = _uiState.value
        if (st.ending || st.phase != FullMockUiState.Phase.Listening) return
        if (st.stage != FullMockUiState.Stage.Part1 && st.stage != FullMockUiState.Stage.Part3) {
            return
        }
        val asrFinalAt = System.currentTimeMillis()
        val turnStartMs = listenStartedMs
            ?: if (sessionAudioCapture.isRecording) {
                sessionAudioCapture.elapsedMs()
            } else {
                asrFinalAt - sessionStartedAt
            }
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
                phase = FullMockUiState.Phase.Saving,
                statusMessage = "${it.stageLabelZh} · ${it.progressLabel} · 记录中…",
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
                    llmMetaJson = """{"source":"mock_${st.stage.name.lowercase()}_answer"}""",
                )
                pendingTurns += userTurn
                turnRepository.insert(userTurn)
                userTurnCount += 1
                val answered = st.answeredInStage + 1
                sessionRepository.update(
                    baseSession(status = SessionStatus.ACTIVE, turnCount = userTurnCount),
                )
                _uiState.update { it.copy(answeredInStage = answered) }

                val nextIndex = st.questionIndex + 1
                if (nextIndex >= st.totalQuestions) {
                    when (st.stage) {
                        FullMockUiState.Stage.Part1 -> beginPart2()
                        FullMockUiState.Stage.Part3 -> endSessionInternal()
                        FullMockUiState.Stage.Part2,
                        FullMockUiState.Stage.Evaluating,
                        FullMockUiState.Stage.Done,
                        -> endSessionInternal()
                    }
                } else {
                    _uiState.update { it.copy(questionIndex = nextIndex) }
                    when (st.stage) {
                        FullMockUiState.Stage.Part1 -> askPart1Question()
                        FullMockUiState.Stage.Part3 -> askPart3Question()
                        FullMockUiState.Stage.Part2,
                        FullMockUiState.Stage.Evaluating,
                        FullMockUiState.Stage.Done,
                        -> Unit
                    }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        phase = FullMockUiState.Phase.AwaitingAnswer,
                        error = e.message ?: "保存失败",
                        statusMessage = "${it.stageLabelZh} · 出错了，请再试",
                    )
                }
            }
        }
    }
    // endregion

    fun endSessionEarly() {
        if (_uiState.value.ending) return
        val phase = _uiState.value.phase
        if (phase == FullMockUiState.Phase.Evaluating || phase == FullMockUiState.Phase.Done) return
        speakTimerJob?.cancel()
        asrListenJob?.cancel()
        listenJob?.cancel()
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching {
                if (phase == FullMockUiState.Phase.P2Speaking && !finishingSpeak) {
                    // Persist long turn then go straight to EV (skip remaining Part3).
                    finishingSpeak = true
                    runCatching { asrEngine.stop() }
                    delay(150)
                    val text = _uiState.value.finalTranscript
                        .ifBlank { _uiState.value.partialTranscript }
                        .ifBlank { asrChunks.joinToString(" ").trim() }
                        .ifBlank { "(session ended during long turn)" }
                    turnSeq += 1
                    val userTurn = Turn(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = TurnRole.USER,
                        text = text,
                        textSource = TextSource.ASR_RAW,
                        seq = turnSeq,
                    )
                    pendingTurns += userTurn
                    turnRepository.insert(userTurn)
                    userTurnCount += 1
                }
                endSessionInternal()
            }.onFailure { e ->
                runCatching { sessionAudioCapture.stop() }
                _uiState.update {
                    it.copy(
                        ending = false,
                        recording = false,
                        error = e.message ?: "评测失败",
                        statusMessage = "评测失败，可稍后重试",
                    )
                }
            }
        }
    }

    private suspend fun endSessionInternal() {
        _uiState.update {
            it.copy(
                ending = true,
                stage = FullMockUiState.Stage.Evaluating,
                phase = FullMockUiState.Phase.Evaluating,
                statusMessage = "生成报告中…（完整模考统一评测）",
                asrListening = false,
            )
        }
        ttsEngine.stopAll()
        runCatching { asrEngine.stop() }
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
        val topicTitle = buildString {
            append("Full Mock · P1(")
            append(_uiState.value.part1ThemeLabel.ifBlank { "personal" })
            append(") → P2(")
            append(card?.category?.titleZh ?: "cue")
            append(") → P3")
        }
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
                navigateToReportSessionId = sessionId,
                stage = FullMockUiState.Stage.Done,
                phase = FullMockUiState.Phase.Done,
                statusMessage = "报告已生成",
            )
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(navigateToReportSessionId = null) }
    }

    override fun onCleared() {
        speakTimerJob?.cancel()
        asrListenJob?.cancel()
        listenJob?.cancel()
        runCatching { sessionAudioCapture.release() }
        super.onCleared()
    }
}
