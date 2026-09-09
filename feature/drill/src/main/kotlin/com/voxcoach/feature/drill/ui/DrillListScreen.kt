package com.voxcoach.feature.drill.ui

import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillListRoute(
    onBack: () -> Unit,
    onOpenDrill: (pointId: String) -> Unit,
    viewModel: DrillListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DrillListScreen(
        state = state,
        onBack = onBack,
        onOpenDrill = onOpenDrill,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillListScreen(
    state: DrillListUiState,
    onBack: () -> Unit,
    onOpenDrill: (pointId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语法句式") },
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
            Text(
                "按雅思高价值句式分组（MVP：A/B/C/E）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.loading && state.groups.isEmpty()) {
                Text("加载中…", style = MaterialTheme.typography.bodyLarge)
            }
            state.groups.forEach { group ->
                Text(
                    "${group.groupCode} · ${group.groupTitle}",
                    style = MaterialTheme.typography.titleMedium,
                )
                group.points.forEach { point ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDrill(point.id) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${point.code} ${point.titleZh}",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                point.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                point.skeleton,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
