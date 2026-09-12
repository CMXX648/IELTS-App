package com.voxcoach.app.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.model.LlmEndpointConfig
import com.voxcoach.core.domain.model.MimoDefaults
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: LlmSettingsRepository,
) : ViewModel() {
    val config: StateFlow<LlmEndpointConfig> = repository.config.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LlmEndpointConfig("", "", ""),
    )

    fun save(baseUrl: String, apiKey: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.update(baseUrl, MimoDefaults.CHAT_MODEL, apiKey)
            onDone()
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
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var savedHint by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cfg) {
        baseUrl = cfg.baseUrl
        apiKey = cfg.apiKey
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MiMo 设置") },
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
            Spacer(Modifier.height(16.dp))
            Text(
                "断网可回放本地录音与查看历史，对话需网络。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
