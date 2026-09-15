package com.voxcoach.core.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * SY settings (docs/05 §4.1 / §8): server base URL in plaintext DataStore;
 * deviceKey via [com.voxcoach.core.domain.security.SecureStringStore]
 * (Android Keystore AES/GCM, same policy as ST-01 apiKey).
 *
 * Pull cursor persists locally so incremental pulls survive restarts.
 */
interface SyncSettingsRepository {
    val settings: Flow<SyncSettings>

    suspend fun update(serverBaseUrl: String, syncEnabled: Boolean)

    /** Persist registration result from [com.voxcoach.core.domain.sync.SyncGateway.registerDevice]. */
    suspend fun setDevice(deviceId: String, deviceKey: String)

    suspend fun clearDevice()

    suspend fun deviceKey(): String?

    suspend fun cursor(): Long

    suspend fun setCursor(value: Long)
}

data class SyncSettings(
    val serverBaseUrl: String = "",
    val syncEnabled: Boolean = false,
    val deviceId: String? = null,
    val hasDeviceKey: Boolean = false,
) {
    val canSync: Boolean
        get() = syncEnabled && !serverBaseUrl.isBlank() && deviceId != null && hasDeviceKey
}
