package com.voxcoach.core.domain.settings

import com.voxcoach.core.domain.model.LlmEndpointConfig
import kotlinx.coroutines.flow.Flow

interface LlmSettingsRepository {
    val config: Flow<LlmEndpointConfig>
    suspend fun update(baseUrl: String, model: String, apiKey: String)
}
