package com.voxcoach.feature.conversation.ui.part2

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
import com.voxcoach.core.domain.part2.Part2CueCardBank
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
 * IELTS Part 2 mock (CV-02 P2 slice): examiner intro TTS → cue card → 60s prep →
 * up to 120s long turn → auto EV report.
 *
 * **ASR choice (documented):** continuous session m4a recording
 * ([SessionAudioCapture]) for the whole mock; during the long turn we run
 * **continuous listen** on [AsrEngine] (start at speak phase, stop on 「说完了」
 * or 120s timeout), accumulating intermediate finals if the system recognizer
 * segments on silence and restarting listen until the turn ends.
 * Press-to-talk is not used for the monologue (poor UX for 1–2 min; docs prefer
 * 「说完了」). Offline file ASR after recording is out of scope (no file-ASR API).
 */
@HiltViewModel
class Part2ViewModel @Inject constructor(
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

    private val _uiState = MutableStateFlow(Part2UiState())
    val uiState: StateFlow<Part2UiState> = _uiState.asStateFlow()

    private var pipelineJob: Job? = null
    private var speakTimerJob: Job? = null
    private var asrListenJob: Job? = null
    private val pendingTurns = mutableListOf<Turn>()
    private var sessionId: String = UUID.randomUUID().toString()
    private var sessionStartedAt: Long = System.currentTimeMillis()
    private var turnSeq: Int = 0
    private var sessionCreated = false
    private var audioPath: String? = null
    private var speakStartedMs: Long? = null
    private var started = false
    private var finishingSpeak = false
    private var speakPhaseEntered = false

    /** Accumulated ASR text across recognizer segments during the long turn. */
    private val asrChunks = mutableListOf<String>()

    init {
        viewModelScope.launch {
            asrEngine.partialResults.collect { partial ->
                if (_uiState.value.phase != Part2UiState.Phase.Speaking) return@collect
                val base = asrChunks.joinToString(" ").trim()
                val shown = if (base.isBlank()) {
                    partial.text
                } else {
                    "$base ${partial.text}".trim()
                }
                _uiState.update { it.copy(partialTranscript = shown) }
            }
        }
        viewModelScope.launch {
            asrEngine.finals.collect { final ->
                if (_uiState.value.phase != Part2UiState.Phase.Speaking) return@collect
                val trimmed = final.text.trim()
                if (trimmed.isEmpty()) return@collect
                if (asrChunks.lastOrNull() != trimmed) {
                    asrChunks += trimmed
                }
                val joined = asrChunks.joinToString(" ").trim()
                _uiState.update {
                    it.copy(
                        partialTranscript = joined,
                        finalTranscript = joined,
                    )
                }
                // System SpeechRecognizer often ends after a pause; keep listening
                // until user finishes or the speak timer expires.
                if (!finishingSpeak && _uiState.value.phase == Part2UiState.Phase.Speaking) {
                    restartAsrListen()
                }
            }
        }
    }

    fun startIfNeeded() {
        if (started) return
        started = true
        pipelineJob = viewModelScope.launch {
            runCatching {
                val card = Part2CueCardBank.pick()
                _uiState.update {
                    it.copy(
                        sessionId = sessionId,
                        cueCard = card,
                        phase = Part2UiState.Phase.Preparing,
                        statusMessage = "模拟口试 Part 2 · ${card.category.titleZh}",
                    )
                }
                ensureSession(card)
                runIntro(card)
                runPrepCountdown(card)
            }.onFailure { e ->
                if (speakPhaseEntered) return@onFailure
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Part 2 启动失败",
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
        turnCount: Int = pendingTurns.count { it.role == TurnRole.USER },
        evId: String? = null,
        updatedAt: Long = System.currentTimeMillis(),
    ): Session = Session(
        id = sessionId,
        type = SessionType.CONVERSATION,
        subtype = SessionSubtype.P2,
        topicId = card?.topicId(),
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
        val intro = Part2CueCardBank.INTRO_TTS
        _uiState.update {
            it.copy(
                phase = Part2UiState.Phase.Intro,
                examinerText = intro,
                statusMessage = "考官说明中…",
                error = null,
            )
        }
        persistAiTurn(
            text = intro,
            metaJson = """{"source":"part2_intro","cueCardId":"${card.id}"}""",
        )
        ttsEngine.speak(Sentence(intro), TtsOptions(languageTag = "en-GB"))
    }

    private suspend fun runPrepCountdown(card: Part2CueCard) {
        _uiState.update {
            it.copy(
                phase = Part2UiState.Phase.PrepCountdown,
                examinerText = card.title,
                countdownSec = Part2CueCardBank.PREP_SECONDS,
                statusMessage = "准备时间 · 可打草稿（仅本地占位）",
            )
        }
        for (left in Part2CueCardBank.PREP_SECONDS downTo 1) {
            if (_uiState.value.phase != Part2UiState.Phase.PrepCountdown) return
            _uiState.update {
                it.copy(
                    countdownSec = left,
                    statusMessage = "准备倒计时 ${left}s · 思考 cue card",
                )
            }
            delay(1_000)
        }
        if (_uiState.value.phase != Part2UiState.Phase.PrepCountdown) return
        _uiState.update { it.copy(countdownSec = 0) }
        runSpeakPrompt(card)
    }

    fun skipPrep() {
        val card = _uiState.value.cueCard ?: return
        if (_uiState.value.phase != Part2UiState.Phase.PrepCountdown) return
        // Break prep loop in pipelineJob, then start speak on a fresh job.
        _uiState.update {
            it.copy(
                phase = Part2UiState.Phase.SpeakPrompt,
                countdownSec = 0,
                statusMessage = "跳过准备，即将独白…",
            )
        }
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching { runSpeakPrompt(card) }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = e.message ?: "进入独白失败")
                    }
                }
        }
    }

    private suspend fun runSpeakPrompt(card: Part2CueCard) {
        if (speakPhaseEntered) return
        val prompt = Part2CueCardBank.SPEAK_PROMPT_TTS
        _uiState.update {
            it.copy(
                phase = Part2UiState.Phase.SpeakPrompt,
                examinerText = prompt,
                statusMessage = "考官提示开始独白…",
                countdownSec = Part2CueCardBank.SPEAK_SECONDS,
            )
        }
        persistAiTurn(
            text = prompt,
            metaJson = """{"source":"part2_speak_prompt","cueCardId":"${card.id}"}""",
        )
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
                phase = Part2UiState.Phase.Speaking,
                partialTranscript = "",
                finalTranscript = "",
                countdownSec = Part2CueCardBank.SPEAK_SECONDS,
                statusMessage = "独白中 · 最多 ${Part2CueCardBank.SPEAK_SECONDS}s，说完点「说完了」",
                asrListening = false,
                error = null,
            )
        }
        startAsrListen()
        speakTimerJob?.cancel()
        speakTimerJob = viewModelScope.launch {
            for (left in Part2CueCardBank.SPEAK_SECONDS downTo 1) {
                if (!isActive) return@launch
                if (_uiState.value.phase != Part2UiState.Phase.Speaking) return@launch
                _uiState.update {
                    it.copy(
                        countdownSec = left,
                        statusMessage = "独白剩余 ${left}s · 说完点「说完了」",
                    )
                }
                delay(1_000)
            }
            if (!isActive) return@launch
            if (_uiState.value.phase != Part2UiState.Phase.Speaking) return@launch
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
            if (finishingSpeak || _uiState.value.phase != Part2UiState.Phase.Speaking) return@launch
            runCatching {
                _uiState.update { it.copy(asrListening = true) }
                asrEngine.start(AsrSessionConfig(languageTag = "en-GB"))
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        asrListening = false,
                        error = e.message ?: "ASR 重启失败",
                    )
                }
            }
        }
    }

    fun onNotesChanged(text: String) {
        _uiState.update { it.copy(notesDraft = text) }
    }

    fun finishSpeaking() {
        if (_uiState.value.phase != Part2UiState.Phase.Speaking) return
        if (finishingSpeak) return
        speakTimerJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching { finishSpeakingInternal() }
                .onFailure { e ->
                    finishingSpeak = false
                    _uiState.update {
                        it.copy(
                            phase = Part2UiState.Phase.Speaking,
                            error = e.message ?: "结束独白失败",
                            statusMessage = "结束失败，可再试「说完了」",
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
                phase = Part2UiState.Phase.Saving,
                statusMessage = "记录独白中…",
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
                """{"source":"part2_long_turn","cueCardId":"${it.id}","notesLen":${_uiState.value.notesDraft.length}}"""
            },
        )
        pendingTurns += userTurn
        turnRepository.insert(userTurn)
        sessionRepository.update(
            baseSession(status = SessionStatus.ACTIVE, turnCount = 1),
        )
        _uiState.update {
            it.copy(
                finalTranscript = text,
                partialTranscript = text,
            )
        }
        endSessionInternal()
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

    fun endSessionEarly() {
        if (_uiState.value.ending) return
        val phase = _uiState.value.phase
        if (phase == Part2UiState.Phase.Evaluating || phase == Part2UiState.Phase.Done) return
        speakTimerJob?.cancel()
        asrListenJob?.cancel()
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            runCatching {
                if (phase == Part2UiState.Phase.Speaking && !finishingSpeak) {
                    finishSpeakingInternal()
                } else {
                    // Persist cue title as context if user aborts before speaking.
                    val card = _uiState.value.cueCard
                    if (card != null && pendingTurns.none { it.role == TurnRole.USER }) {
                        turnSeq += 1
                        val placeholder = Turn(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            role = TurnRole.USER,
                            text = "(session ended before long turn)",
                            textSource = TextSource.EDITED,
                            seq = turnSeq,
                        )
                        pendingTurns += placeholder
                        turnRepository.insert(placeholder)
                    }
                    endSessionInternal()
                }
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
                phase = Part2UiState.Phase.Evaluating,
                statusMessage = "生成报告中…",
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
        val topicTitle = "Part 2 · ${card?.category?.titleZh ?: "Cue card"} · ${card?.title ?: ""}"
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
                phase = Part2UiState.Phase.Done,
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
        runCatching { sessionAudioCapture.release() }
        super.onCleared()
    }
}
