package com.voxcoach.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.data.sync.SyncEngine
import com.voxcoach.core.domain.model.LlmEndpointConfig
import com.voxcoach.core.domain.model.MimoDefaults
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import com.voxcoach.core.domain.settings.SyncSettings
import com.voxcoach.core.domain.settings.SyncSettingsRepository
import com.voxcoach.core.domain.sync.SyncGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: LlmSettingsRepository,
    private val syncSettingsRepository: SyncSettingsRepository,
    private val syncGateway: SyncGateway,
    private val syncEngine: SyncEngine,
) : ViewModel() {
    val config: StateFlow<LlmEndpointConfig> = repository.config.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LlmEndpointConfig("", "", ""),
    )

    val syncSettings: StateFlow<SyncSettings> = syncSettingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SyncSettings(),
    )

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    fun save(baseUrl: String, apiKey: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.update(baseUrl, MimoDefaults.CHAT_MODEL, apiKey)
            onDone()
        }
    }

    fun saveSync(serverBaseUrl: String, enabled: Boolean) {
        viewModelScope.launch {
            syncSettingsRepository.update(serverBaseUrl, enabled)
        }
    }

    fun registerDevice(deviceName: String) {
        viewModelScope.launch {
            val current = syncSettingsRepository.settings.first()
            if (current.serverBaseUrl.isBlank()) {
                _syncMessage.value = "请先填写同步服务器地址"
                return@launch
            }
            runCatching {
                syncGateway.registerDevice(current.serverBaseUrl, deviceName, "android")
            }.onSuccess { reg ->
                syncSettingsRepository.setDevice(reg.deviceId, reg.deviceKey)
                _syncMessage.value = "设备已注册：${reg.deviceId}"
            }.onFailure { e ->
                _syncMessage.value = "注册失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            when (val outcome = syncEngine.runSync()) {
                is SyncEngine.Outcome.Disabled -> _syncMessage.value = "同步未开启或配置不完整"
                SyncEngine.Outcome.NotRegistered -> _syncMessage.value = "请先注册设备"
                is SyncEngine.Outcome.Done -> _syncMessage.value =
                    "同步完成：推送 ${outcome.pushed} 条" +
                        (if (outcome.conflicts.isNotEmpty()) "，冲突 ${outcome.conflicts.size} 条" else "") +
                        "，拉取应用 ${outcome.appliedFromRemote} 条"
                is SyncEngine.Outcome.Failed -> _syncMessage.value = "同步失败：${outcome.error.message}"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val cfg by viewModel.config.collectAsStateWithLifecycle()
    val syncSettings by viewModel.syncSettings.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var savedHint by remember { mutableStateOf(false) }
    var syncBaseUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cfg) {
        baseUrl = cfg.baseUrl
        apiKey = cfg.apiKey
    }
    LaunchedEffect(syncSettings) {
        if (syncBaseUrl.isBlank()) syncBaseUrl = syncSettings.serverBaseUrl
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
        ) {
            Text("ST-01：填写 MiMo Base URL 与 API Key。对话默认 mimo-v2.5，ASR/TTS 固定 mimo-v2.5-asr / mimo-v2.5-tts，无需手填模型。Key 经 Android Keystore AES/GCM 加密存本机。")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text(MimoDefaults.BASE_URL) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.save(baseUrl, apiKey) {
                        savedHint = true
                        scope.launch { /* keep UI responsive */ }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
            if (savedHint) {
                Spacer(Modifier.height(8.dp))
                Text("已保存")
            }

            Spacer(Modifier.height(24.dp))
            Text("SY 同步（自有服务器，仅文本数据，录音不出本机）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = syncBaseUrl,
                onValueChange = { syncBaseUrl = it },
                label = { Text("同步服务器地址") },
                placeholder = { Text("https://sync.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = syncSettings.syncEnabled,
                    onCheckedChange = { viewModel.saveSync(syncBaseUrl, it) },
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    if (syncSettings.syncEnabled) "已开启" else "已关闭",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    syncSettings.deviceId == null -> "设备未注册（注册后与其它设备增量同步）"
                    else -> "已注册设备：${syncSettings.deviceId}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = { viewModel.registerDevice(android.os.Build.MODEL ?: "android") }) {
                    Text("注册设备")
                }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = { viewModel.syncNow() }) {
                    Text("立即同步")
                }
            }
            syncMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "断网可回放本地录音与查看历史，对话需网络。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
