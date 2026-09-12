package com.voxcoach.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.voxcoach.core.designsystem.component.IslandConnectorTone
import com.voxcoach.core.designsystem.component.IslandState
import com.voxcoach.core.designsystem.component.IslandNode
import com.voxcoach.core.designsystem.component.StreakPill
import com.voxcoach.core.designsystem.component.IslandPathConnector
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.designsystem.theme.VoxMuted
import com.voxcoach.core.designsystem.theme.VoxOnBackground
import com.voxcoach.core.domain.model.Topic
import com.voxcoach.core.domain.repository.TopicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    topicRepository: TopicRepository,
) : ViewModel() {
    val topics: StateFlow<List<Topic>> = topicRepository.observeTopics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onStartConversation: (topicId: String) -> Unit,
    onStartPart1: () -> Unit = {},
    onStartPart2: () -> Unit = {},
    onStartPart3: () -> Unit = {},
    onStartFullMock: () -> Unit = {},
    onOpenGrammar: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    onOpenDebug: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingTopicId by remember { mutableStateOf<String?>(null) }
    var pendingPart1 by remember { mutableStateOf(false) }
    var pendingPart2 by remember { mutableStateOf(false) }
    var pendingPart3 by remember { mutableStateOf(false) }
    var pendingFullMock by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    // Lightweight local streak stub until profile streak lands
    val streakDays = remember { 5 }

    fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun notificationPerms(): Array<String> = buildList {
        if (!hasMic()) add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.RECORD_AUDIO] == true || hasMic()
        val topic = pendingTopicId
        val wantPart1 = pendingPart1
        val wantPart2 = pendingPart2
        val wantPart3 = pendingPart3
        val wantFullMock = pendingFullMock
        pendingTopicId = null
        pendingPart1 = false
        pendingPart2 = false
        pendingPart3 = false
        pendingFullMock = false
        when {
            granted && wantPart1 -> onStartPart1()
            granted && wantPart2 -> onStartPart2()
            granted && wantPart3 -> onStartPart3()
            granted && wantFullMock -> onStartFullMock()
            granted && topic != null -> onStartConversation(topic)
            !granted -> showRationale = true
        }
    }

    fun tryStart(topicId: String) {
        if (hasMic()) {
            onStartConversation(topicId)
        } else {
            pendingTopicId = topicId
            pendingPart1 = false
            pendingPart2 = false
            pendingPart3 = false
            pendingFullMock = false
            showRationale = true
        }
    }

    fun tryStartPart1() {
        if (hasMic()) onStartPart1() else {
            pendingPart1 = true; pendingPart2 = false; pendingPart3 = false
            pendingFullMock = false; pendingTopicId = null; showRationale = true
        }
    }

    fun tryStartPart2() {
        if (hasMic()) onStartPart2() else {
            pendingPart2 = true; pendingPart1 = false; pendingPart3 = false
            pendingFullMock = false; pendingTopicId = null; showRationale = true
        }
    }

    fun tryStartPart3() {
        if (hasMic()) onStartPart3() else {
            pendingPart3 = true; pendingPart1 = false; pendingPart2 = false
            pendingFullMock = false; pendingTopicId = null; showRationale = true
        }
    }

    fun tryStartFullMock() {
        if (hasMic()) onStartFullMock() else {
            pendingFullMock = true; pendingPart1 = false; pendingPart2 = false
            pendingPart3 = false; pendingTopicId = null; showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                pendingTopicId = null
                pendingPart1 = false
                pendingPart2 = false
                pendingPart3 = false
                pendingFullMock = false
            },
            title = { Text("需要麦克风权限") },
            text = {
                Text(
                    "录音仅用于练习与分析，保存在本机，不默认上传。" +
                        "请授予麦克风权限后再开始对话。" +
                        if (Build.VERSION.SDK_INT >= 33) {
                            "（Android 13+ 建议同时允许通知，以便显示「练习会话」前台提示。）"
                        } else {
                            ""
                        },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        val topic = pendingTopicId
                        val wantPart1 = pendingPart1
                        val wantPart2 = pendingPart2
                        val wantPart3 = pendingPart3
                        val wantFullMock = pendingFullMock
                        val perms = notificationPerms()
                        if (perms.isEmpty()) {
                            when {
                                wantPart1 -> { pendingPart1 = false; onStartPart1() }
                                wantPart2 -> { pendingPart2 = false; onStartPart2() }
                                wantPart3 -> { pendingPart3 = false; onStartPart3() }
                                wantFullMock -> { pendingFullMock = false; onStartFullMock() }
                                topic != null -> { pendingTopicId = null; onStartConversation(topic) }
                            }
                        } else {
                            permissionLauncher.launch(perms)
                        }
                    },
                ) { Text("去授权") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        pendingTopicId = null
                        pendingPart1 = false
                        pendingPart2 = false
                        pendingPart3 = false
                        pendingFullMock = false
                    },
                ) { Text("取消") }
            },
        )
    }

    val hometownId = topics.firstOrNull { it.code == "hometown" }?.id
        ?: topics.firstOrNull { it.group == "part1" }?.id
        ?: "T-hometown"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = onOpenDebug)
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "SPEAKING MASTER",
                        color = VoxMuted,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                    )
                    Text(
                        "Hello, Alex \uD83D\uDC4B",
                        color = VoxOnBackground,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                    )
                }
                StreakPill(days = streakDays)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
            ) {
                IslandNode(
                    title = "🎙 发音热身岛",
                    state = IslandState.Done,
                    progress = 1f,
                    onClick = { tryStart(hometownId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                )
                IslandPathConnector(tone = IslandConnectorTone.Success)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    IslandNode(
                        title = "\uD83D\uDCAC 话题素材岛",
                        subtitle = "点击开始练习 \u2192",
                        state = IslandState.Current,
                        progress = 0.45f,
                        cardWidth = 220.dp,
                        onClick = { tryStartPart1() },
                    )
                }
                IslandPathConnector(tone = IslandConnectorTone.Muted)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IslandNode(
                        title = "\uD83E\uDDE0 逻辑表达岛",
                        subtitle = "完成第2岛后解锁",
                        state = IslandState.Locked,
                        cardWidth = 200.dp,
                        onClick = onOpenGrammar,
                    )
                }
                IslandPathConnector(tone = IslandConnectorTone.Muted)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    IslandNode(
                        title = "\uD83D\uDCDD 全真模考岛",
                        subtitle = "解锁全部岛屿后开启",
                        state = IslandState.Distant,
                        cardWidth = 200.dp,
                        onClick = { tryStartFullMock() },
                    )
                }
            }

            if (!hasMic()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "需要麦克风才能开练",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenSettings, shape = RoundedCornerShape(16.dp)) {
                    Text("去设置")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

