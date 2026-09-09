package com.voxcoach.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.settings.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            onboardingRepository.setOnboardingDone(true)
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(
    onOpenSettings: () -> Unit,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.complete(onFinished)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("欢迎使用 VoxCoach", style = MaterialTheme.typography.headlineMedium)
        when (step) {
            0 -> {
                Text(
                    "这是一款个人口语练习 App：用麦克风与 AI 考官对话，" +
                        "本地录音复盘，并获得雅思四维评分反馈。\n\n" +
                        "录音仅保存在本机，默认不会上传。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text("下一步")
                }
            }
            1 -> {
                Text(
                    "请先配置你的 LLM（OpenAI 兼容接口：baseUrl / model / API Key）。\n" +
                        "没有 Key 时仍可用内置 Mock 跑通流程。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("去设置配置 LLM")
                }
                OutlinedButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                    Text("稍后再说，继续")
                }
            }
            else -> {
                Text(
                    "练习会话需要麦克风权限以便 ASR 与本地录音。" +
                        if (Build.VERSION.SDK_INT >= 33) {
                            "\nAndroid 13+ 还需通知权限，用于「练习会话」前台服务提示。"
                        } else {
                            ""
                        } +
                        "\n\n录音仅用于练习与分析，可离线使用，不默认上传。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = {
                        val needed = buildList {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                add(Manifest.permission.RECORD_AUDIO)
                            }
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        if (needed.isEmpty()) {
                            viewModel.complete(onFinished)
                        } else {
                            permissionLauncher.launch(needed.toTypedArray())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("授权并进入首页")
                }
                OutlinedButton(
                    onClick = { viewModel.complete(onFinished) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("跳过权限，稍后授予")
                }
            }
        }
    }
}
