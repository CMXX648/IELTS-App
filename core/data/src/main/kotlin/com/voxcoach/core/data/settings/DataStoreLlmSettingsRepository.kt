package com.voxcoach.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voxcoach.core.domain.model.LlmEndpointConfig
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.llmSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "voxcoach_llm_settings",
)

/**
 * M1 placeholder for ST-01 keys. apiKey stored in DataStore Preferences (device-local).
 * M2+ should wrap apiKey with Android Keystore AES/GCM (docs/04 §5.1).
 * Never commit secrets — values stay on device only.
 */
@Singleton
class DataStoreLlmSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : LlmSettingsRepository {

    private val store = context.llmSettingsStore

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val model = stringPreferencesKey("model")
        val apiKey = stringPreferencesKey("api_key")
    }

    override val config: Flow<LlmEndpointConfig> = store.data.map { prefs ->
        LlmEndpointConfig(
            baseUrl = prefs[Keys.baseUrl].orEmpty().ifBlank { DEFAULT_BASE_URL },
            model = prefs[Keys.model].orEmpty().ifBlank { DEFAULT_MODEL },
            apiKey = prefs[Keys.apiKey].orEmpty(),
        )
    }

    override suspend fun update(baseUrl: String, model: String, apiKey: String) {
        store.edit { prefs ->
            prefs[Keys.baseUrl] = baseUrl.trim()
            prefs[Keys.model] = model.trim()
            prefs[Keys.apiKey] = apiKey.trim()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}
