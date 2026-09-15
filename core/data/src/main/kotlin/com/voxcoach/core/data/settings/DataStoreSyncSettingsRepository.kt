package com.voxcoach.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voxcoach.core.domain.security.SecureKeys
import com.voxcoach.core.domain.security.SecureStringStore
import com.voxcoach.core.domain.settings.SyncSettings
import com.voxcoach.core.domain.settings.SyncSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.syncSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "voxcoach_sync_settings",
)

/**
 * SY settings (docs/05 §4.1): serverBaseUrl + syncEnabled + deviceId in
 * plaintext DataStore; deviceKey via [SecureStringStore] (Keystore AES/GCM).
 * Pull cursor persists for incremental pulls.
 */
@Singleton
class DataStoreSyncSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val secureStore: SecureStringStore,
) : SyncSettingsRepository {

    private val store = context.syncSettingsStore

    private object Keys {
        val serverBaseUrl = stringPreferencesKey("server_base_url")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
        val deviceId = stringPreferencesKey("device_id")
        val cursor = longPreferencesKey("pull_cursor")
    }

    override val settings: Flow<SyncSettings> = flow {
        // Keystore read once per collection; register/clear restarts collectors.
        val hasKey = !secureStore.get(SecureKeys.SYNC_DEVICE_KEY).isNullOrBlank()
        emitAll(
            store.data.map { prefs ->
                SyncSettings(
                    serverBaseUrl = prefs[Keys.serverBaseUrl].orEmpty(),
                    syncEnabled = prefs[Keys.syncEnabled] ?: false,
                    deviceId = prefs[Keys.deviceId],
                    hasDeviceKey = hasKey,
                )
            },
        )
    }

    override suspend fun update(serverBaseUrl: String, syncEnabled: Boolean) {
        store.edit { prefs ->
            prefs[Keys.serverBaseUrl] = serverBaseUrl.trim()
            prefs[Keys.syncEnabled] = syncEnabled
        }
    }

    override suspend fun setDevice(deviceId: String, deviceKey: String) {
        secureStore.put(SecureKeys.SYNC_DEVICE_KEY, deviceKey)
        store.edit { prefs ->
            prefs[Keys.deviceId] = deviceId
        }
    }

    override suspend fun clearDevice() {
        secureStore.remove(SecureKeys.SYNC_DEVICE_KEY)
        store.edit { prefs ->
            prefs.remove(Keys.deviceId)
        }
    }

    override suspend fun deviceKey(): String? =
        secureStore.get(SecureKeys.SYNC_DEVICE_KEY)?.takeIf { it.isNotBlank() }

    override suspend fun cursor(): Long = store.data.map { it[Keys.cursor] ?: 0L }.first()

    override suspend fun setCursor(value: Long) {
        store.edit { prefs ->
            prefs[Keys.cursor] = value
        }
    }
}
