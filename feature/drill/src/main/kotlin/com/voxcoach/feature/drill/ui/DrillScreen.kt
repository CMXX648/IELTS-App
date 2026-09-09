package com.voxcoach.feature.drill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillRoute(
    onBack: () -> Unit,
    viewModel: DrillViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DrillScreen(
        state = state,
        onBack = onBack,
        onMicPressed = viewModel::onMicPressed,
        onMicReleased = viewModel::onMicReleased,
        onCollect = viewModel::collectToMistakes,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillScreen(
    state: DrillUiState,
    onBack: () -> Unit,
    onMicPressed: () -> Unit,
    onMicReleased: () -> Unit,
    onCollect: () -> Unit,
    onRetry: () -> Unit,
) {
    val point = state.point
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        point?.let { "${it.code} · ${it.titleZh}" } ?: "语法练习",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
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
                if (point != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("判定规则", style = MaterialTheme.typography.labelLarge)
                            Text(point.rule, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("句型骨架", style = MaterialTheme.typography.labelLarge)
                            Text(point.skeleton, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("话题提示", style = MaterialTheme.typography.labelLarge)
                            Text(point.topicHint, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium)
                if (state.partialTranscript.isNotBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("你的句子", style = MaterialTheme.typography.labelLarge)
                            Text(state.partialTranscript, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                state.judge?.let { judge ->
                    val colors = if (judge.hit) {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    } else {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        )
                    }
                    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (judge.hit) "✅ 命中" else "⚠️ 未命中",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!judge.hit) {
                                Spacer(Modifier.height(8.dp))
                                Text("你的句子 → ${state.finalTranscript}")
                                Text("修正 → ${judge.correction}")
                                Text("为什么 → ${judge.why}")
                            } else {
                                Spacer(Modifier.height(6.dp))
                                Text(judge.why)
                            }
                            if (judge.model.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text("示范：${judge.model}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state.offerCollect && !state.collected) {
                    OutlinedButton(onClick = onCollect, modifier = Modifier.fillMaxWidth()) {
                        Text("收藏到错题本")
                    }
                }
                if (state.phase == DrillUiState.Phase.Feedback) {
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text("再试一句")
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (state.phase == DrillUiState.Phase.Judging) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
                val listening = state.phase == DrillUiState.Phase.Listening
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            if (listening) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onMicPressed()
                                    tryAwaitRelease()
                                    onMicReleased()
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "按住说话",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("按住说话（单句）", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
