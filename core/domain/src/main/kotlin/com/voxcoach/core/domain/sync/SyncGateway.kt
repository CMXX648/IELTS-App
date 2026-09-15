package com.voxcoach.core.domain.sync

/**
 * M3 SY network port (docs/05 §4). Pure Kotlin — Android impl lives in
 * `core:data` (OkHttpSyncGateway); tests inject fakes.
 *
 * Paths/headers come from [com.voxcoach.core.domain.model.SyncContract].
 * Timestamps cross the wire as RFC3339 UTC strings (docs/05 §4.2 examples);
 * epoch-millis conversion is the gateway's job.
 */
interface SyncGateway {
    suspend fun registerDevice(baseUrl: String, deviceName: String, platform: String): DeviceRegistration

    suspend fun push(baseUrl: String, deviceKey: String, changes: List<SyncChange>): SyncPushResult

    suspend fun pull(baseUrl: String, deviceKey: String, cursor: Long, full: Boolean): SyncPullResult
}

data class DeviceRegistration(
    val deviceId: String,
    val deviceKey: String,
    val serverTimeMs: Long,
)

data class SyncPushResult(
    val pushed: Int,
    val conflictIds: List<String>,
    val newCursor: Long,
)

data class SyncPullResult(
    val changes: List<SyncChange>,
    val cursor: Long,
    val truncated: Boolean,
)

/** Sealed failure so callers can map to NetworkUx messaging (SY-03 style). */
sealed class SyncGatewayException(message: String) : Exception(message) {
    class InvalidDeviceKey(message: String) : SyncGatewayException(message)
    class PayloadTooLarge(message: String) : SyncGatewayException(message)
    class InvalidChange(message: String) : SyncGatewayException(message)
    class RateLimited(message: String) : SyncGatewayException(message)
    class Server(message: String, val httpCode: Int) : SyncGatewayException(message)
    class Network(message: String) : SyncGatewayException(message)
}
