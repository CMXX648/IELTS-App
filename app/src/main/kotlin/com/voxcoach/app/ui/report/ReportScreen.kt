package com.voxcoach.app.ui.report

import android.media.MediaPlayer
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.voxcoach.core.domain.model.Turn
import com.voxcoach.core.domain.model.TurnRole
import com.voxcoach.core.domain.repository.EvRepository
import com.voxcoach.core.domain.repository.MistakeRepository
import com.voxcoach.core.domain.repository.SessionRepository
import com.voxcoach.core.domain.repository.TurnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
    val audioPath: String? = null,
    val turns: List<Turn> = emptyList(),
    val playing: Boolean = false,
    val playbackError: String? = null,
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val evRepository: EvRepository,
    private val mistakeRepository: MistakeRepository,
    private val sessionRepository: SessionRepository,
    private val turnRepository: TurnRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private var player: MediaPlayer? = null

    init {
        viewModelScope.launch {
            val ev = evRepository.getBySession(sessionId)
            val session = sessionRepository.get(sessionId)
            val turns = turnRepository.listForSession(sessionId)
            _state.update {
                it.copy(
                    loading = false,
                    ev = ev,
                    audioPath = session?.audioPath,
                    turns = turns,
                    message = when {
                        ev == null -> "暂无评测结果"
                        else -> null
                    },
                    collectedIds = ev?.items?.mapNotNull { item ->
                        item.id.takeIf { id -> item.collectedToMistakeAt != null }
                    }?.toSet().orEmpty(),
                )
            }
        }
    }

    fun playFull() {
        val path = _state.value.audioPath ?: run {
            _state.update { it.copy(playbackError = "本会话无本地录音") }
            return
        }
        if (!File(path).exists()) {
            _state.update { it.copy(playbackError = "录音文件不存在：$path") }
            return
        }
        runCatching {
            releasePlayer()
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    _state.update { s -> s.copy(playing = false) }
                }
                prepare()
                start()
            }
            _state.update { it.copy(playing = true, playbackError = null, message = "正在播放整段录音") }
        }.onFailure { e ->
            _state.update { it.copy(playing = false, playbackError = e.message ?: "播放失败") }
        }
    }

    fun seekToTurn(turn: Turn) {
        val path = _state.value.audioPath ?: run {
            _state.update { it.copy(playbackError = "本会话无本地录音") }
            return
        }
        val startMs = turn.startMs ?: 0L
        runCatching {
            val p = player
            if (p == null || !_state.value.playing) {
                releasePlayer()
                player = MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener {
                        _state.update { s -> s.copy(playing = false) }
                    }
                    prepare()
                    seekTo(startMs.toInt().coerceAtLeast(0))
                    start()
                }
            } else {
                p.seekTo(startMs.toInt().coerceAtLeast(0))
                if (!p.isPlaying) p.start()
            }
            _state.update {
                it.copy(
                    playing = true,
                    playbackError = null,
                    message = "跳转到第 ${turn.seq} 句（${startMs}ms）",
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(playbackError = e.message ?: "跳转失败") }
        }
    }

    fun pausePlayback() {
        runCatching { player?.pause() }
        _state.update { it.copy(playing = false) }
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

    private fun releasePlayer() {
        runCatching {
            player?.run {
                stop()
                release()
            }
        }
        player = null
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        onDispose { /* ViewModel.onCleared releases player */ }
    }
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

            Text("录音回放（RP-01）", style = MaterialTheme.typography.titleMedium)
            if (state.audioPath.isNullOrBlank()) {
                Text(
                    "本会话无本地录音文件",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "文件：${state.audioPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = {
                            if (state.playing) viewModel.pausePlayback() else viewModel.playFull()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text(if (state.playing) "暂停" else "播放整段")
                    }
                }
                val userTurns = state.turns.filter { it.role == TurnRole.USER && it.startMs != null }
                if (userTurns.isNotEmpty()) {
                    Text("跳转到用户轮次", style = MaterialTheme.typography.labelLarge)
                    userTurns.forEach { turn ->
                        OutlinedButton(
                            onClick = { viewModel.seekToTurn(turn) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "第 ${turn.seq} 句 · ${turn.startMs}–${turn.endMs ?: "?"}ms · " +
                                    turn.text.take(28),
                            )
                        }
                    }
                }
            }
            state.playbackError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            val ev = state.ev
            if (ev == null) {
                Text(state.message ?: "无评测数据")
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
