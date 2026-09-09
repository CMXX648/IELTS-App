package com.voxcoach.app.ui.debug

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voxcoach.core.domain.smoke.LatencySmoke
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatencySmokeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("尚未运行。将使用 MockAsr / MockLlm / MockTts 跑 3 句固定英文。") }
    var filePath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("延迟冒烟调试") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "固定句：\n" + LatencySmoke.FIXED_SENTENCES.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = {
                    if (running) return@Button
                    running = true
                    scope.launch {
                        val report = withContext(Dispatchers.Default) { LatencySmoke.run() }
                        val md = report.toMarkdown()
                        Log.i(TAG, md)
                        val dir = File(context.filesDir, "smoke")
                        val file = withContext(Dispatchers.IO) { LatencySmoke.writeReport(report, dir) }
                        filePath = file.absolutePath
                        reportText = md
                        running = false
                    }
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "运行中…" else "运行 LatencySmoke")
            }
            filePath?.let { Text("报告已写入：$it", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Text(reportText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val TAG = "LatencySmoke"
