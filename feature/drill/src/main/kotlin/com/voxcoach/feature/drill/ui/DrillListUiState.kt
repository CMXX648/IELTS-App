package com.voxcoach.feature.drill.ui

import com.voxcoach.core.domain.model.GrammarPoint

data class DrillGroup(
    val groupCode: String,
    val groupTitle: String,
    val points: List<GrammarPoint>,
)

data class DrillListUiState(
    val groups: List<DrillGroup> = emptyList(),
    val loading: Boolean = true,
)
