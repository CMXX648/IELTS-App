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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.designsystem.theme.VoxAccent
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

private val CardShape = RoundedCornerShape(16.dp)

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
    val streakDays = remember { 1 }

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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Greeting + streak
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = onOpenDebug),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("你好，今天练一会儿？", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "长按问候可开调试页",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = VoxAccent.copy(alpha = 0.15f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Text(
                        text = "🔥 $streakDays 天",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = VoxAccent,
                    )
                }
            }

            // Today practice CTA
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "今天练",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Part 1 模拟 · 5 道题，练完自动出报告",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { tryStartPart1() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("一键开练", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Text("小关卡", style = MaterialTheme.typography.titleMedium)

            QuestCard(
                title = "自由对话 · 家乡",
                subtitle = "轻松热身，随便聊",
                locked = false,
                onClick = { tryStart(hometownId) },
            )
            QuestCard(
                title = "语法句式",
                subtitle = "口头产出目标句，AI 判定",
                locked = false,
                onClick = onOpenGrammar,
            )
            QuestCard(
                title = "Part 2 独白",
                subtitle = "Cue card + 准备 + 长独白",
                locked = false,
                onClick = { tryStartPart2() },
            )
            QuestCard(
                title = "Part 3 讨论",
                subtitle = "深度 5 题",
                locked = false,
                onClick = { tryStartPart3() },
            )
            QuestCard(
                title = "完整模考",
                subtitle = "P1 → P2 → P3 一次打通",
                locked = true,
                lockedHint = "先完成一次「今天练」再解锁",
                onClick = { tryStartFullMock() },
            )
            QuestCard(
                title = "错题本",
                subtitle = "回顾收藏的纠错",
                locked = false,
                onClick = onOpenVault,
            )

            if (!hasMic()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "尚未授予麦克风权限。开始前会引导授权；也可先去设置配置 API Key。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CardShape,
                        ) { Text("打开设置") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QuestCard(
    title: String,
    subtitle: String,
    locked: Boolean,
    lockedHint: String = "暂未解锁",
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.55f else 1f),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = { if (!locked) onClick() },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (locked) "🔒" else "▶",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (locked) lockedHint else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!locked) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                ) { Text("进入") }
            }
        }
    }
}
