package com.voxcoach.app.ui.profile

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.model.TodayStats
import com.voxcoach.core.domain.model.UserProfile
import com.voxcoach.core.domain.pf.DimTrendAggregator
import com.voxcoach.core.domain.pf.DimTrendSeries
import com.voxcoach.core.domain.repository.EvRepository
import com.voxcoach.core.domain.repository.ProfileRepository
import com.voxcoach.core.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val today: TodayStats = TodayStats(0, 0, 0),
    val trend: DimTrendSeries = DimTrendSeries(emptyList()),
    val trendLoading: Boolean = true,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val sessionRepository: SessionRepository,
    private val evRepository: EvRepository,
) : ViewModel() {
    val state: StateFlow<ProfileUiState> = flow {
        val (start, end) = shanghaiDayBounds()
        val today = sessionRepository.todayStats(start, end)
        // Fast path: show cached trend while we refresh from ev_results.
        val cachedProfile = profileRepository.get()
        val cachedTrend = DimTrendAggregator.fromCacheJson(cachedProfile.dimTrendCacheJson)
        if (cachedTrend != null) {
            emit(
                ProfileUiState(
                    profile = cachedProfile,
                    today = today,
                    trend = cachedTrend,
                    trendLoading = false,
                ),
            )
        }

        val snapshots = evRepository.listRecentScores(DimTrendAggregator.DEFAULT_N)
        val trend = DimTrendAggregator.aggregate(snapshots, DimTrendAggregator.DEFAULT_N)
        val cacheJson = DimTrendAggregator.toCacheJson(trend)
        runCatching { profileRepository.updateDimTrendCache(cacheJson) }

        profileRepository.observe().collect { profile ->
            emit(
                ProfileUiState(
                    profile = profile,
                    today = today,
                    trend = trend,
                    trendLoading = false,
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    private fun shanghaiDayBounds(): Pair<Long, Long> {
        val tz = TimeZone.getTimeZone("Asia/Shanghai")
        val cal = Calendar.getInstance(tz)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val minutes = remember(state.today.durationMs) {
        state.today.durationMs / 60_000.0
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("学习档案") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("今日练习（PF-01）", style = MaterialTheme.typography.titleMedium)
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("今日时长：${"%.1f".format(minutes)} 分钟")
                    Text("今日轮数：${state.today.turnCount}")
                    Text("今日会话：${state.today.sessionCount}")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("累计", style = MaterialTheme.typography.titleMedium)
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("目标 Band：${state.profile.targetBand}")
                    Text("阶段：${state.profile.stage}")
                    Text("累计时长：${state.profile.totalDurationMs / 60_000} 分钟")
                    Text("累计轮数：${state.profile.totalTurnCount}")
                    Text("累计会话：${state.profile.totalSessionCount}")
                    Text("连续打卡：${state.profile.streak} 天（简化计数）")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "四维分数趋势（PF-02 · 最近 ${state.trend.limit} 次）",
                style = MaterialTheme.typography.titleMedium,
            )
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        state.trendLoading && state.trend.isEmpty -> {
                            Text("加载趋势中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.trend.isEmpty -> {
                            Text(
                                "暂无评测记录",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "完成一次对话并生成四维评测后，这里会显示总分与 FC / LR / GRA / P 趋势。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            val latest = state.trend.latestOverall
                            Text(
                                "最近总分：${latest?.let { "%.1f".format(it) } ?: "—"}　·　样本 ${state.trend.points.size} 次",
                            )
                            TrendSparklineRow(
                                label = "总分",
                                values = state.trend.overall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TrendSparklineRow(
                                label = "FC 流利连贯",
                                values = state.trend.fc,
                                color = Color(0xFF1565C0),
                            )
                            TrendSparklineRow(
                                label = "LR 词汇资源",
                                values = state.trend.lr,
                                color = Color(0xFF00897B),
                            )
                            TrendSparklineRow(
                                label = "GRA 语法",
                                values = state.trend.gra,
                                color = Color(0xFF6A1B9A),
                            )
                            TrendSparklineRow(
                                label = "P 发音",
                                values = state.trend.p,
                                color = Color(0xFFE65100),
                            )
                            Text(
                                "左旧右新 · Band 0–9",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendSparklineRow(
    label: String,
    values: List<Double>,
    color: Color,
) {
    val latest = values.lastOrNull()?.let { "%.1f".format(it) } ?: "—"
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(latest, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Sparkline(
            values = values,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        )
    }
}

@Composable
private fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    yMin: Double = 0.0,
    yMax: Double = 9.0,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || values.isEmpty()) return@Canvas

        // Mid band guide (6.0)
        val midY = ((yMax - 6.0) / (yMax - yMin)).toFloat() * h
        drawLine(
            color = grid,
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1f,
        )

        if (values.size == 1) {
            val y = ((yMax - values[0]) / (yMax - yMin)).toFloat().coerceIn(0f, 1f) * h
            drawCircle(color = color, radius = 4f, center = Offset(w / 2f, y))
            return@Canvas
        }

        val stepX = w / (values.size - 1).toFloat()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = ((yMax - v) / (yMax - yMin)).toFloat().coerceIn(0f, 1f) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
        val last = values.last()
        val lastX = (values.size - 1) * stepX
        val lastY = ((yMax - last) / (yMax - yMin)).toFloat().coerceIn(0f, 1f) * h
        drawCircle(color = color, radius = 4f, center = Offset(lastX, lastY))
    }
}
