package com.voxcoach.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.model.TodayStats
import com.voxcoach.core.domain.model.UserProfile
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
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {
    val state: StateFlow<ProfileUiState> = flow {
        val (start, end) = shanghaiDayBounds()
        val today = sessionRepository.todayStats(start, end)
        profileRepository.observe().collect { profile ->
            emit(ProfileUiState(profile = profile, today = today))
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
                .padding(16.dp),
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
            Spacer(Modifier.height(8.dp))
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
        }
    }
}
