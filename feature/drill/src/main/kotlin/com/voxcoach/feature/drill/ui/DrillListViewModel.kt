package com.voxcoach.feature.drill.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcoach.core.domain.repository.GrammarPointRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DrillListViewModel @Inject constructor(
    private val grammarPointRepository: GrammarPointRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { grammarPointRepository.ensureSeeded() }
    }

    val uiState: StateFlow<DrillListUiState> = grammarPointRepository.observeAll()
        .map { points ->
            val groups = points
                .groupBy { it.groupCode to it.groupTitle }
                .entries
                .sortedBy { it.value.minOfOrNull { p -> p.sortOrder } ?: 0 }
                .map { (key, list) ->
                    DrillGroup(
                        groupCode = key.first,
                        groupTitle = key.second,
                        points = list.sortedBy { it.sortOrder },
                    )
                }
            DrillListUiState(groups = groups, loading = false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrillListUiState())
}
