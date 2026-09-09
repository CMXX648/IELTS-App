package com.voxcoach.feature.conversation.ui.fullmock

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxcoach.core.domain.part2.Part2CueCard
import com.voxcoach.core.domain.part2.Part2CueCardBank

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullMockRoute(
    onBack: () -> Unit,
    onOpenReport: (sessionId: String) -> Unit,
    viewModel: FullMockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startIfNeeded() }
    LaunchedEffect(state.navigateToReportSessionId) {
        val sid = state.navigateToReportSessionId ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        onOpenReport(sid)
    }
    FullMockScreen(
        state = state,
        onBack = onBack,
        onMicPressed = viewModel::onMicPressed,
        onMicReleased = viewModel::onMicReleased,
        onNotesChanged = viewModel::onNotesChanged,
        onSkipPrep = viewModel::skipPrep,
        onFinishSpeaking = viewModel::finishSpeaking,
        onEndEarly = viewModel::endSessionEarly,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullMockScreen(
    state: FullMockUiState,
    onBack: () -> Unit,
    onMicPressed: () -> Unit,
    onMicReleased: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onSkipPrep: () -> Unit,
    onFinishSpeaking: () -> Unit,
    onEndEarly: () -> Unit,
) {
    val overallProgress = when (state.stage) {
        FullMockUiState.Stage.Part1 -> {
            val frac = if (state.totalQuestions > 0) {
                state.answeredInStage.toFloat() / state.totalQuestions
            } else {
                0f
            }
            0.05f + 0.30f * frac
        }
        FullMockUiState.Stage.Part2 -> when (state.phase) {
            FullMockUiState.Phase.P2Intro -> 0.38f
            FullMockUiState.Phase.P2PrepCountdown -> {
                val done = if (state.prepTotalSec > 0) {
                    1f - state.countdownSec.toFloat() / state.prepTotalSec
                } else {
                    0f
                }
                0.38f + 0.08f * done
            }
            FullMockUiState.Phase.P2SpeakPrompt -> 0.48f
            FullMockUiState.Phase.P2Speaking -> {
                val done = if (state.speakTotalSec > 0) {
                    1f - state.countdownSec.toFloat() / state.speakTotalSec
                } else {
                    0f
                }
                0.48f + 0.12f * done
            }
            FullMockUiState.Phase.P2Saving -> 0.62f
            else -> 0.40f
        }
        FullMockUiState.Stage.Part3 -> {
            val frac = if (state.totalQuestions > 0) {
                state.answeredInStage.toFloat() / state.totalQuestions
            } else {
                0f
            }
            0.65f + 0.28f * frac
        }
        FullMockUiState.Stage.Evaluating -> 0.95f
        FullMockUiState.Stage.Done -> 1f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("完整模考") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Text(
                        text = state.progressLabel,
                        modifier = Modifier.padding(end = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.stageLabelZh,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                StageChips(current = state.stage)
                LinearProgressIndicator(
                    progress = { overallProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.recording) {
                        AssistChip(onClick = {}, label = { Text("● 本地录音中（不上传）") })
                    }
                    if (state.asrListening) {
                        AssistChip(onClick = {}, label = { Text("ASR 聆听中") })
                    }
                }
                state.error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }

                when (state.stage) {
                    FullMockUiState.Stage.Part1 -> {
                        if (state.part1ThemeLabel.isNotBlank()) {
                            Text(
                                "主题：${state.part1ThemeLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FullMockUiState.Stage.Part2 -> {
                        state.cueCard?.let { CueCardPanel(it) }
                    }
                    FullMockUiState.Stage.Part3 -> {
                        if (state.part3ThemeLabel.isNotBlank()) {
                            Text(
                                "讨论主题：${state.part3ThemeLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.cueCard?.let { card ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("承接 Part 2", style = MaterialTheme.typography.labelLarge)
                                    Text(card.title, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    else -> Unit
                }

                BubbleCard(title = "考官（English）", body = state.examinerText.ifBlank { "…" })

                if (state.phase == FullMockUiState.Phase.P2PrepCountdown ||
                    state.phase == FullMockUiState.Phase.P2Intro
                ) {
                    OutlinedTextField(
                        value = state.notesDraft,
                        onValueChange = onNotesChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        label = { Text("准备笔记（可选占位，不上传）") },
                        placeholder = { Text("写下关键词…") },
                    )
                }

                if (state.phase == FullMockUiState.Phase.P2Speaking ||
                    state.phase == FullMockUiState.Phase.P2Saving ||
                    state.phase == FullMockUiState.Phase.Listening ||
                    state.phase == FullMockUiState.Phase.AwaitingAnswer ||
                    state.phase == FullMockUiState.Phase.Saving ||
                    state.stage == FullMockUiState.Stage.Evaluating ||
                    state.stage == FullMockUiState.Stage.Done
                ) {
                    val title = if (state.stage == FullMockUiState.Stage.Part2) {
                        "你的独白（ASR）"
                    } else {
                        "你的回答"
                    }
                    BubbleCard(title = title, body = state.partialTranscript.ifBlank { "…" })
                }

                if (state.phase == FullMockUiState.Phase.P2PrepCountdown) {
                    Text(
                        "准备剩余 ${state.countdownSec} / ${Part2CueCardBank.PREP_SECONDS} 秒",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                if (state.phase == FullMockUiState.Phase.P2Speaking) {
                    Text(
                        "独白剩余 ${state.countdownSec} / ${Part2CueCardBank.SPEAK_SECONDS} 秒",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                val busy = state.phase in setOf(
                    FullMockUiState.Phase.Preparing,
                    FullMockUiState.Phase.Intro,
                    FullMockUiState.Phase.Asking,
                    FullMockUiState.Phase.P2Intro,
                    FullMockUiState.Phase.P2SpeakPrompt,
                    FullMockUiState.Phase.P2Saving,
                    FullMockUiState.Phase.Saving,
                    FullMockUiState.Phase.Evaluating,
                )
                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            when (state.phase) {
                                FullMockUiState.Phase.Evaluating -> "正在生成四维评测…"
                                FullMockUiState.Phase.Asking, FullMockUiState.Phase.P2SpeakPrompt,
                                FullMockUiState.Phase.P2Intro, FullMockUiState.Phase.Intro,
                                -> "考官播报中…"
                                FullMockUiState.Phase.P2Saving, FullMockUiState.Phase.Saving -> "保存转写…"
                                else -> "准备中…"
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (state.phase) {
                FullMockUiState.Phase.P2PrepCountdown -> {
                    Button(onClick = onSkipPrep, modifier = Modifier.fillMaxWidth()) {
                        Text("跳过准备，开始独白")
                    }
                }
                FullMockUiState.Phase.P2Speaking -> {
                    Button(onClick = onFinishSpeaking, modifier = Modifier.fillMaxWidth()) {
                        Text("说完了")
                    }
                }
                else -> Unit
            }

            val showMic = state.stage == FullMockUiState.Stage.Part1 ||
                state.stage == FullMockUiState.Stage.Part3
            if (showMic) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val listening = state.phase == FullMockUiState.Phase.Listening
                    val canTalk = state.phase == FullMockUiState.Phase.AwaitingAnswer || listening
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !canTalk -> MaterialTheme.colorScheme.surfaceVariant
                                    listening -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            )
                            .pointerInput(canTalk) {
                                if (!canTalk) return@pointerInput
                                detectTapGestures(
                                    onPress = {
                                        onMicPressed()
                                        try {
                                            awaitRelease()
                                        } finally {
                                            onMicReleased()
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "按住说话",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                Text(
                    text = "按住说话 · 松手发送",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onEndEarly,
                enabled = !state.ending &&
                    state.phase != FullMockUiState.Phase.Evaluating &&
                    state.phase != FullMockUiState.Phase.Preparing &&
                    state.phase != FullMockUiState.Phase.Done,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("提前结束并生成统一报告")
            }
            Text(
                text = "P1→P2→P3 不离场 · 结束后一次 EV · subtype=MOCK",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StageChips(current: FullMockUiState.Stage) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            FullMockUiState.Stage.Part1 to "Part 1",
            FullMockUiState.Stage.Part2 to "Part 2",
            FullMockUiState.Stage.Part3 to "Part 3",
        ).forEach { (stage, label) ->
            val active = when {
                current == FullMockUiState.Stage.Evaluating ||
                    current == FullMockUiState.Stage.Done -> true
                current == stage -> true
                stage.ordinal < current.ordinal -> true
                else -> false
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        label,
                        color = if (current == stage) {
                            MaterialTheme.colorScheme.primary
                        } else if (active) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun CueCardPanel(card: Part2CueCard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cue card", style = MaterialTheme.typography.labelLarge)
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            Text("You should say:", style = MaterialTheme.typography.labelMedium)
            card.bullets.forEach { bullet ->
                Text("• $bullet", style = MaterialTheme.typography.bodyLarge)
            }
            Text(card.explain, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BubbleCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
