package com.voxcoach.core.domain.speech

import com.voxcoach.core.domain.model.AsrFinal
import com.voxcoach.core.domain.model.AsrPartial
import com.voxcoach.core.domain.model.AsrSessionConfig
import kotlinx.coroutines.flow.Flow

interface AsrEngine {
    val partialResults: Flow<AsrPartial>
    val finals: Flow<AsrFinal>
    suspend fun start(config: AsrSessionConfig)
    suspend fun stop()
}
