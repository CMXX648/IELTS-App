package com.voxcoach.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** First-run onboarding flag (ST chrome). */
interface OnboardingRepository {
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone(done: Boolean)
}
