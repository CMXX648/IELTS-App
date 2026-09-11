package com.voxcoach.feature.conversation.ui.part1

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.voxcoach.core.designsystem.component.ScoreToast
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Part1Route(
    onBack: () -> Unit,
    onOpenReport: (sessionId: String) -> Unit,
    viewModel: Part1ViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startIfNeeded() }
    LaunchedEffect(state.navigateToReportSessionId) {
        val sid = state.navigateToReportSessionId ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        onOpenReport(sid)
    }
    Part1Screen(
        state = state,
        onBack = onBack,
        onMicPressed = viewModel::onMicPressed,
        onMicReleased = viewModel::onMicReleased,
        onEndSession = viewModel::endSession,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Part1Screen(
    state: Part1UiState,
    onBack: () -> Unit,
    onMicPressed: () -> Unit,
    onMicReleased: () -> Unit,
    onEndSession: () -> Unit,
) {
    val progress = if (state.totalQuestions > 0) {
        state.answeredCount.toFloat() / state.totalQuestions.toFloat()
    } else {
        0f
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模拟口试 · Part 1") },
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
                if (state.themeLabel.isNotBlank()) {
                    Text(
                        text = "主题：${state.themeLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "关卡进度 · ${state.progressLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                ScoreToast(
                    message = "+分 · 连击 ${state.answeredCount}",
                    token = state.answeredCount.takeIf { it > 0 },
                    visibleWhen = state.answeredCount > 0 && state.phase != Part1UiState.Phase.Evaluating,
                )
                if (state.phase == Part1UiState.Phase.Evaluating) {
                    AssistChip(
                        onClick = {},
                        label = { Text("⭐ 本关完成，正在生成报告…") },
                    )
                }
                state.error?.let { err ->
                    AssistChip(
                        onClick = {},
                        label = { Text("没关系，可以再试一次") },
                    )
                }
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.recording) {
                    AssistChip(
                        onClick = {},
                        label = { Text("● 本地录音中（不上传）") },
                    )
                }
                state.error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
                BubbleCard(title = "考官（English）", body = state.examinerText.ifBlank { "…" })
                BubbleCard(title = "你的回答", body = state.partialTranscript.ifBlank { "…" })
                if (state.phase == Part1UiState.Phase.Evaluating ||
                    state.phase == Part1UiState.Phase.Preparing ||
                    state.phase == Part1UiState.Phase.Asking
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            when (state.phase) {
                                Part1UiState.Phase.Evaluating -> "正在生成四维评测…"
                                Part1UiState.Phase.Asking -> "考官提问播报中…"
                                else -> "准备中…"
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onEndSession,
                enabled = !state.ending &&
                    state.phase != Part1UiState.Phase.Evaluating &&
                    state.phase != Part1UiState.Phase.Preparing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("提前结束并生成报告")
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val listening = state.phase == Part1UiState.Phase.Listening
                val canTalk = state.phase == Part1UiState.Phase.AwaitingAnswer || listening
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
                text = "按住说话 · 松手发送（题库题，共 ${state.totalQuestions} 题后自动评测）",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            )
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
