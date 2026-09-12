package com.voxcoach.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voxcoach.core.domain.model.LlmEndpointConfig
import com.voxcoach.core.domain.model.MimoDefaults
import com.voxcoach.core.domain.security.SecureKeys
import com.voxcoach.core.domain.security.SecureStringStore
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.llmSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "voxcoach_llm_settings",
)

/**
 * ST-01 settings: baseUrl in DataStore (plaintext); apiKey via
 * [SecureStringStore] (AndroidKeyStore AES/GCM). Chat/ASR/TTS models are
 * hardcoded MiMo-V2.5 IDs. One-shot migration clears any legacy plaintext
 * `api_key` preference.
 */
@Singleton
class DataStoreLlmSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val secureStore: SecureStringStore,
) : LlmSettingsRepository {

    private val store = context.llmSettingsStore
    private val migrateMutex = Mutex()
    @Volatile private var migrated = false

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val model = stringPreferencesKey("model")
        /** Legacy plaintext slot — cleared after migration to Keystore. */
        val apiKey = stringPreferencesKey("api_key")
        /** Bumped on every update so apiKey-only changes still emit. */
        val apiKeyRevision = intPreferencesKey("api_key_revision")
    }

    override val config: Flow<LlmEndpointConfig> = store.data
        .onStart { migratePlaintextApiKeyOnce() }
        .transform { prefs ->
            // Touch revision so DataStore emissions stay coupled to secure-store writes.
            @Suppress("UNUSED_VARIABLE")
            val revision = prefs[Keys.apiKeyRevision] ?: 0
            emit(
                LlmEndpointConfig(
                    baseUrl = prefs[Keys.baseUrl].orEmpty().ifBlank { DEFAULT_BASE_URL },
                    model = DEFAULT_MODEL,
                    apiKey = secureStore.get(SecureKeys.LLM_API_KEY).orEmpty(),
                ),
            )
        }

    override suspend fun update(baseUrl: String, model: String, apiKey: String) {
        migratePlaintextApiKeyOnce()
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            secureStore.remove(SecureKeys.LLM_API_KEY)
        } else {
            secureStore.put(SecureKeys.LLM_API_KEY, trimmed)
        }
        store.edit { prefs ->
            prefs[Keys.baseUrl] = baseUrl.trim()
            prefs[Keys.model] = DEFAULT_MODEL
            prefs.remove(Keys.apiKey)
            prefs[Keys.apiKeyRevision] = (prefs[Keys.apiKeyRevision] ?: 0) + 1
        }
    }

    /**
     * If a pre-M2 plaintext apiKey remains in DataStore, copy into secure store
     * (when secure is empty) then delete the plaintext preference.
     */
    private suspend fun migratePlaintextApiKeyOnce() {
        if (migrated) return
        migrateMutex.withLock {
            if (migrated) return
            val prefs = store.data.first()
            val plain = prefs[Keys.apiKey]
            if (!plain.isNullOrBlank()) {
                val existing = secureStore.get(SecureKeys.LLM_API_KEY)
                if (existing.isNullOrBlank()) {
                    secureStore.put(SecureKeys.LLM_API_KEY, plain.trim())
                }
                store.edit {
                    it.remove(Keys.apiKey)
                    it[Keys.apiKeyRevision] = (it[Keys.apiKeyRevision] ?: 0) + 1
                }
            }
            migrated = true
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = MimoDefaults.BASE_URL
        const val DEFAULT_MODEL = MimoDefaults.CHAT_MODEL
    }
}
