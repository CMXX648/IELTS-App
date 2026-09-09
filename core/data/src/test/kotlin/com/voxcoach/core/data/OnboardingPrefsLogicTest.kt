package com.voxcoach.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Documents onboardingDone default: absent preference == not done.
 * DataStore wiring is integration; this keeps the contract explicit for M2.
 */
class OnboardingPrefsLogicTest {
    @Test
    fun absentPreferenceMeansOnboardingNotDone() {
        val stored: Boolean? = null
        val onboardingDone = stored ?: false
        assertThat(onboardingDone).isFalse()
    }

    @Test
    fun explicitTrueMeansDone() {
        val stored: Boolean? = true
        val onboardingDone = stored ?: false
        assertThat(onboardingDone).isTrue()
    }
}
