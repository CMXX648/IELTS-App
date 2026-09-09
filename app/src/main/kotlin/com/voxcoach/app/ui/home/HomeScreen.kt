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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
    onOpenGrammar: () -> Unit = {},
    onOpenDebug: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingTopicId by remember { mutableStateOf<String?>(null) }
    var pendingPart1 by remember { mutableStateOf(false) }
    var pendingPart2 by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

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
        pendingTopicId = null
        pendingPart1 = false
        pendingPart2 = false
        when {
            granted && wantPart1 -> onStartPart1()
            granted && wantPart2 -> onStartPart2()
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
            showRationale = true
        }
    }

    fun tryStartPart1() {
        if (hasMic()) {
            onStartPart1()
        } else {
            pendingPart1 = true
            pendingPart2 = false
            pendingTopicId = null
            showRationale = true
        }
    }

    fun tryStartPart2() {
        if (hasMic()) {
            onStartPart2()
        } else {
            pendingPart2 = true
            pendingPart1 = false
            pendingTopicId = null
            showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                pendingTopicId = null
                pendingPart1 = false
                pendingPart2 = false
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
                        val perms = notificationPerms()
                        if (perms.isEmpty()) {
                            when {
                                wantPart1 -> {
                                    pendingPart1 = false
                                    onStartPart1()
                                }
                                wantPart2 -> {
                                    pendingPart2 = false
                                    onStartPart2()
                                }
                                topic != null -> {
                                    pendingTopicId = null
                                    onStartConversation(topic)
                                }
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
                    },
                ) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "VoxCoach 首页",
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onOpenDebug,
                        ),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("选择话题开始自由对话（CV-01）", style = MaterialTheme.typography.titleMedium)
            Text(
                "长按标题可打开延迟冒烟调试页",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("模拟口试 · Part 1", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "固定 5 道考官题（银行题），进度「第 x/5 题」，答完自动出 EV 报告（CV-02 P1）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { tryStartPart1() }, modifier = Modifier.fillMaxWidth()) {
                        Text("开始 Part 1 模拟")
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("模拟口试 · Part 2", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cue card + 60s 准备 + 最长 120s 独白，连续录音与 ASR，结束后自动 EV（CV-02 P2）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { tryStartPart2() }, modifier = Modifier.fillMaxWidth()) {
                        Text("开始 Part 2 模拟")
                    }
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpenGrammar),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("语法句式", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "口头产出目标句式，AI 判定命中与纠错（GR-01/02/03）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenGrammar, modifier = Modifier.fillMaxWidth()) {
                        Text("进入语法练习")
                    }
                }
            }
            if (!hasMic()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "尚未授予麦克风权限。开始对话前会引导授权；" +
                                "也可先到设置配置 LLM。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("打开设置")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val list = topics.filter { it.group == "part1" }.ifEmpty {
                listOf(
                    Topic(
                        id = "T-hometown",
                        code = "hometown",
                        title = "Hometown",
                        titleZh = "家乡",
                        group = "part1",
                    ),
                )
            }
            list.forEach { topic ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${topic.title}（${topic.titleZh}）", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { tryStart(topic.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("开始自由对话")
                        }
                    }
                }
            }
        }
    }
}
