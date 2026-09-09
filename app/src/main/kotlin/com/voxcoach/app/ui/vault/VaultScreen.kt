package com.voxcoach.app.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.model.Mistake
import com.voxcoach.core.domain.repository.MistakeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class VaultListUiState(
    val items: List<Mistake> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class VaultListViewModel @Inject constructor(
    mistakeRepository: MistakeRepository,
) : ViewModel() {
    val uiState: StateFlow<VaultListUiState> = mistakeRepository.observeOpen()
        .map { VaultListUiState(items = it, loading = false) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            VaultListUiState(),
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListRoute(
    onBack: () -> Unit,
    onOpenDetail: (mistakeId: String) -> Unit,
    viewModel: VaultListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VaultListScreen(
        state = state,
        onBack = onBack,
        onOpenDetail = onOpenDetail,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    state: VaultListUiState,
    onBack: () -> Unit,
    onOpenDetail: (mistakeId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("错题本") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && state.items.isEmpty()) {
            Text(
                "加载中…",
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
            )
            return@Scaffold
        }
        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("暂无未掌握错题", style = MaterialTheme.typography.titleMedium)
                Text(
                    "会话报告或语法练习中收藏的错题会出现在这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "未掌握 · ${state.items.size} 条（VB-01/02）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.items, key = { it.id }) { item ->
                MistakeCard(item = item, onClick = { onOpenDetail(item.id) })
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MistakeCard(item: Mistake, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(dimLabel(item.dimension)) },
            )
            Text(
                item.quote.ifBlank { "（无原文）" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "→ ${item.correction.ifBlank { "（无修正）" }}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF15803D),
            )
        }
    }
}

internal fun dimLabel(raw: String): String = when (raw.trim().lowercase()) {
    "fc", "fluency", "fluency_coherence" -> "流利连贯 FC"
    "lr", "lexical", "lexical_resource" -> "词汇资源 LR"
    "gra", "grammar", "grammatical" -> "语法准确 GRA"
    "p", "pron", "pronunciation" -> "发音 P"
    else -> raw.ifBlank { "未标注" }.uppercase()
}
