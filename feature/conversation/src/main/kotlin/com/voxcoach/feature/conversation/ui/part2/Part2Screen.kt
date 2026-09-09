package com.voxcoach.feature.conversation.ui.part2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxcoach.core.domain.part2.Part2CueCard
import com.voxcoach.core.domain.part2.Part2CueCardBank

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Part2Route(
    onBack: () -> Unit,
    onOpenReport: (sessionId: String) -> Unit,
    viewModel: Part2ViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startIfNeeded() }
    LaunchedEffect(state.navigateToReportSessionId) {
        val sid = state.navigateToReportSessionId ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        onOpenReport(sid)
    }
    Part2Screen(
        state = state,
        onBack = onBack,
        onNotesChanged = viewModel::onNotesChanged,
        onSkipPrep = viewModel::skipPrep,
        onFinishSpeaking = viewModel::finishSpeaking,
        onEndEarly = viewModel::endSessionEarly,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Part2Screen(
    state: Part2UiState,
    onBack: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onSkipPrep: () -> Unit,
    onFinishSpeaking: () -> Unit,
    onEndEarly: () -> Unit,
) {
    val prepProgress = if (state.phase == Part2UiState.Phase.PrepCountdown && state.prepTotalSec > 0) {
        1f - (state.countdownSec.toFloat() / state.prepTotalSec.toFloat())
    } else if (state.phase == Part2UiState.Phase.Speaking && state.speakTotalSec > 0) {
        1f - (state.countdownSec.toFloat() / state.speakTotalSec.toFloat())
    } else {
        when (state.phase) {
            Part2UiState.Phase.Preparing, Part2UiState.Phase.Intro -> 0.05f
            Part2UiState.Phase.SpeakPrompt -> 0.35f
            Part2UiState.Phase.Saving, Part2UiState.Phase.Evaluating -> 0.9f
            Part2UiState.Phase.Done -> 1f
            else -> 0.2f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模拟口试 · Part 2") },
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
                if (state.cueCard != null) {
                    Text(
                        text = "题型：${state.categoryLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { prepProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
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

                BubbleCard(
                    title = "考官（English）",
                    body = state.examinerText.ifBlank { "…" },
                )

                state.cueCard?.let { card ->
                    CueCardPanel(card = card, highlight = state.phase != Part2UiState.Phase.Preparing)
                }

                if (state.phase == Part2UiState.Phase.PrepCountdown ||
                    state.phase == Part2UiState.Phase.Intro
                ) {
                    OutlinedTextField(
                        value = state.notesDraft,
                        onValueChange = onNotesChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        label = { Text("准备笔记（可选占位，不上传）") },
                        placeholder = { Text("写下关键词…") },
                    )
                }

                if (state.phase == Part2UiState.Phase.Speaking ||
                    state.phase == Part2UiState.Phase.Saving ||
                    state.phase == Part2UiState.Phase.Evaluating ||
                    state.phase == Part2UiState.Phase.Done
                ) {
                    BubbleCard(
                        title = "你的独白（ASR）",
                        body = state.partialTranscript.ifBlank { "…" },
                    )
                }

                if (state.phase == Part2UiState.Phase.Evaluating ||
                    state.phase == Part2UiState.Phase.Preparing ||
                    state.phase == Part2UiState.Phase.Intro ||
                    state.phase == Part2UiState.Phase.SpeakPrompt ||
                    state.phase == Part2UiState.Phase.Saving
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            when (state.phase) {
                                Part2UiState.Phase.Evaluating -> "正在生成四维评测…"
                                Part2UiState.Phase.Intro -> "考官说明播报中…"
                                Part2UiState.Phase.SpeakPrompt -> "考官提示播报中…"
                                Part2UiState.Phase.Saving -> "保存独白转写…"
                                else -> "准备中…"
                            },
                        )
                    }
                }

                if (state.phase == Part2UiState.Phase.PrepCountdown) {
                    Text(
                        text = "准备剩余 ${state.countdownSec} / ${Part2CueCardBank.PREP_SECONDS} 秒",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                if (state.phase == Part2UiState.Phase.Speaking) {
                    Text(
                        text = "独白剩余 ${state.countdownSec} / ${Part2CueCardBank.SPEAK_SECONDS} 秒",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (state.phase) {
                Part2UiState.Phase.PrepCountdown -> {
                    Button(onClick = onSkipPrep, modifier = Modifier.fillMaxWidth()) {
                        Text("跳过准备，开始独白")
                    }
                }
                Part2UiState.Phase.Speaking -> {
                    Button(onClick = onFinishSpeaking, modifier = Modifier.fillMaxWidth()) {
                        Text("说完了")
                    }
                }
                else -> Unit
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onEndEarly,
                enabled = !state.ending &&
                    state.phase != Part2UiState.Phase.Evaluating &&
                    state.phase != Part2UiState.Phase.Preparing &&
                    state.phase != Part2UiState.Phase.Done,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("提前结束并生成报告")
            }

            Text(
                text = "连续录音 + 独白阶段持续 ASR（分段自动续听）· 结束后自动 EV",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CueCardPanel(card: Part2CueCard, highlight: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
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
