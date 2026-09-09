package com.voxcoach.app.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.model.EvResult
import com.voxcoach.core.domain.model.FeedbackItem
import com.voxcoach.core.domain.model.Mistake
import com.voxcoach.core.domain.model.MistakeStatus
import com.voxcoach.core.domain.repository.EvRepository
import com.voxcoach.core.domain.repository.MistakeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val loading: Boolean = true,
    val ev: EvResult? = null,
    val message: String? = null,
    val collectedIds: Set<String> = emptySet(),
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val evRepository: EvRepository,
    private val mistakeRepository: MistakeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ev = evRepository.getBySession(sessionId)
            _state.update {
                it.copy(
                    loading = false,
                    ev = ev,
                    message = if (ev == null) "暂无评测结果" else null,
                    collectedIds = ev?.items?.mapNotNull { item ->
                        item.id.takeIf { id -> item.collectedToMistakeAt != null }
                    }?.toSet().orEmpty(),
                )
            }
        }
    }

    fun collectToMistakes(item: FeedbackItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            mistakeRepository.insert(
                Mistake(
                    id = UUID.randomUUID().toString(),
                    sourceItemId = item.id,
                    sessionId = sessionId,
                    dimension = item.dimension,
                    quote = item.quote,
                    correction = item.correction,
                    status = MistakeStatus.OPEN,
                    createdAt = now,
                ),
            )
            evRepository.markCollected(item.id, now)
            _state.update {
                it.copy(
                    collectedIds = it.collectedIds + item.id,
                    message = "已收藏进错题本",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("会话报告") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                Text("加载中…")
                return@Column
            }
            val ev = state.ev
            if (ev == null) {
                Text(state.message ?: "无数据")
                return@Column
            }
            Text("总分 Band ${ev.overallBand}", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DimCard("FC", ev.dims.fc.score, Modifier.weight(1f))
                DimCard("LR", ev.dims.lr.score, Modifier.weight(1f))
                DimCard("GRA", ev.dims.gra.score, Modifier.weight(1f))
                DimCard("P", ev.dims.p.score, Modifier.weight(1f))
            }
            Text("分项说明", style = MaterialTheme.typography.titleMedium)
            Text("FC：${ev.dims.fc.comment}")
            Text("LR：${ev.dims.lr.comment}")
            Text("GRA：${ev.dims.gra.comment}")
            Text("P：${ev.dims.p.comment}")

            Text("反馈条目", style = MaterialTheme.typography.titleMedium)
            ev.items.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${item.dimension.uppercase()} · ${item.category}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text("你：${item.quote}")
                        Text("修正：${item.correction}")
                        Text("为何：${item.why}")
                        Spacer(Modifier.height(8.dp))
                        val collected = item.id in state.collectedIds
                        Button(
                            onClick = { viewModel.collectToMistakes(item) },
                            enabled = !collected,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (collected) "已收藏" else "收藏进错题本")
                        }
                    }
                }
            }
            if (ev.highlights.isNotEmpty()) {
                Text("高分亮点", style = MaterialTheme.typography.titleMedium)
                ev.highlights.forEach { h ->
                    Text("· [${h.dim}] ${h.quote} — ${h.note}")
                }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun DimCard(label: String, score: Double, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("%.1f".format(score), style = MaterialTheme.typography.titleLarge)
        }
    }
}
