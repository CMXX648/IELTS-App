package com.voxcoach.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.voxcoach.core.domain.settings.OnboardingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingStore: DataStore<Preferences> by preferencesDataStore(
    name = "voxcoach_onboarding",
)

@Singleton
class DataStoreOnboardingRepository @Inject constructor(
    @ApplicationContext context: Context,
) : OnboardingRepository {

    private val store = context.onboardingStore

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    override val onboardingDone: Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.onboardingDone] ?: false
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        store.edit { prefs ->
            prefs[Keys.onboardingDone] = done
        }
    }
}
