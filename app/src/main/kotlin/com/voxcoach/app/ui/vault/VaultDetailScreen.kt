package com.voxcoach.app.ui.vault

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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.model.Mistake
import com.voxcoach.core.domain.model.MistakeStatus
import com.voxcoach.core.domain.repository.MistakeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultDetailUiState(
    val mistake: Mistake? = null,
    val loading: Boolean = true,
    val message: String? = null,
    val mastered: Boolean = false,
)

@HiltViewModel
class VaultDetailViewModel @Inject constructor(
    private val mistakeRepository: MistakeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mistakeId: String = checkNotNull(savedStateHandle["mistakeId"])

    private val _uiState = MutableStateFlow(VaultDetailUiState())
    val uiState: StateFlow<VaultDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val m = mistakeRepository.get(mistakeId)
            _uiState.update {
                it.copy(
                    mistake = m,
                    loading = false,
                    mastered = m?.status == MistakeStatus.MASTERED,
                )
            }
        }
    }

    fun markMastered() {
        viewModelScope.launch {
            mistakeRepository.markMastered(mistakeId)
            _uiState.update {
                it.copy(
                    mastered = true,
                    message = "已标记为掌握，将从开放列表移除",
                    mistake = it.mistake?.copy(status = MistakeStatus.MASTERED),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDetailRoute(
    onBack: () -> Unit,
    viewModel: VaultDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VaultDetailScreen(
        state = state,
        onBack = onBack,
        onMarkMastered = viewModel::markMastered,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDetailScreen(
    state: VaultDetailUiState,
    onBack: () -> Unit,
    onMarkMastered: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("错题详情") },
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
            when {
                state.loading -> Text("加载中…")
                state.mistake == null -> Text("找不到该错题")
                else -> {
                    val m = state.mistake
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(dimLabel(m.dimension)) },
                    )
                    Text("原文", style = MaterialTheme.typography.labelLarge)
                    Text(
                        m.quote.ifBlank { "（无）" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("修正", style = MaterialTheme.typography.labelLarge)
                    Text(
                        m.correction.ifBlank { "（无）" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF15803D),
                    )
                    Text("为什么", style = MaterialTheme.typography.labelLarge)
                    Text(
                        m.why.ifBlank { "（暂无说明）" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (!m.grammarPointId.isNullOrBlank()) {
                        Text(
                            "关联语法点：${m.grammarPointId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (state.mastered) {
                        Text(
                            state.message ?: "已掌握",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Button(
                            onClick = onMarkMastered,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("标记已掌握")
                        }
                    }
                    state.message?.takeIf { state.mastered.not() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
