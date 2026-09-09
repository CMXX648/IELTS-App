package com.voxcoach.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onOpenDebug: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val topics by viewModel.topics.collectAsStateWithLifecycle()
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
            Spacer(Modifier.height(8.dp))
            val list = topics.ifEmpty {
                listOf(
                    Topic(
                        id = "T-hometown",
                        code = "hometown",
                        title = "Hometown",
                        titleZh = "家乡",
                    ),
                )
            }
            list.forEach { topic ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${topic.title}（${topic.titleZh}）", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onStartConversation(topic.id) },
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
