package com.voxcoach.core.data.sync

import com.voxcoach.core.domain.model.SyncContract
import com.voxcoach.core.domain.sync.DeviceRegistration
import com.voxcoach.core.domain.sync.SyncChange
import com.voxcoach.core.domain.sync.SyncGateway
import com.voxcoach.core.domain.sync.SyncGatewayException
import com.voxcoach.core.domain.sync.SyncPullResult
import com.voxcoach.core.domain.sync.SyncPushResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OkHttp impl of [SyncGateway] against the Go server (docs/05 §4).
 * Paths/headers come from [SyncContract]; errors map to docs/05 §9 codes.
 */
@Singleton
class OkHttpSyncGateway @Inject constructor(
    private val client: OkHttpClient,
) : SyncGateway {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun registerDevice(
        baseUrl: String,
        deviceName: String,
        platform: String,
    ): DeviceRegistration = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("deviceName", deviceName)
            put("platform", platform)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(normalizeBaseUrl(baseUrl) + SyncContract.REGISTER_DEVICE_PATH)
            .post(body)
            .build()
        execute(request) { obj ->
            DeviceRegistration(
                deviceId = obj.strOrThrow("deviceId"),
                deviceKey = obj.strOrThrow("deviceKey"),
                serverTimeMs = obj.isoOrMillis("serverTime"),
            )
        }
    }

    override suspend fun push(
        baseUrl: String,
        deviceKey: String,
        changes: List<SyncChange>,
    ): SyncPushResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put(
                "changes",
                JsonArray(changes.map { SyncPayloadCodec.encodeChange(it) }),
            )
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(normalizeBaseUrl(baseUrl) + SyncContract.PUSH_PATH)
            .header(SyncContract.DEVICE_KEY_HEADER, deviceKey)
            .post(payload)
            .build()
        execute(request) { obj ->
            SyncPushResult(
                pushed = obj["pushed"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                conflictIds = (obj["conflicts"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonObject)?.str("id") }
                    .orEmpty(),
                newCursor = obj["newCursor"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
    }

    override suspend fun pull(
        baseUrl: String,
        deviceKey: String,
        cursor: Long,
        full: Boolean,
    ): SyncPullResult = withContext(Dispatchers.IO) {
        val url = buildString {
            append(normalizeBaseUrl(baseUrl))
            append(SyncContract.PULL_PATH)
            append("?cursor=").append(cursor)
            if (full) append("&full=1")
        }
        val request = Request.Builder()
            .url(url)
            .header(SyncContract.DEVICE_KEY_HEADER, deviceKey)
            .get()
            .build()
        execute(request) { obj ->
            SyncPullResult(
                changes = (obj["changes"] as? JsonArray)
                    ?.mapNotNull { SyncPayloadCodec.decodeChange(it) }
                    .orEmpty(),
                cursor = obj["cursor"]?.jsonPrimitive?.longOrNull ?: 0L,
                truncated = obj["truncated"]?.jsonPrimitive?.content == "true",
            )
        }
    }

    // ------------------------------------------------------------------

    private inline fun <T> execute(request: Request, parse: (JsonObject) -> T): T {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw SyncGatewayException.Network(e.message ?: "network error")
        }
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, text)
            val obj = runCatching { json.parseToJsonElement(text).let { it as? JsonObject } }
                .getOrNull()
                ?: throw SyncGatewayException.Server("malformed response body", resp.code)
            return parse(obj)
        }
    }

    private fun httpError(code: Int, body: String): SyncGatewayException {
        val serverCode = runCatching {
            (json.parseToJsonElement(body) as? JsonObject)?.str("code")
        }.getOrNull()
        return when (serverCode) {
            "invalid_device_key" -> SyncGatewayException.InvalidDeviceKey(body)
            "payload_too_large" -> SyncGatewayException.PayloadTooLarge(body)
            "invalid_change" -> SyncGatewayException.InvalidChange(body)
            "rate_limited" -> SyncGatewayException.RateLimited(body)
            else -> SyncGatewayException.Server(body, code)
        }
    }

    companion object {
        /**
         * Accepts `https://host` or `https://host/api/v1`; always yields a
         * base without trailing slash and WITHOUT the `/api/v1` prefix
         * (paths in [SyncContract] already carry it).
         */
        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (url.endsWith("/api/v1")) url = url.removeSuffix("/api/v1")
            return url
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.strOrThrow(key: String): String =
            str(key) ?: throw SyncGatewayException.Server("missing `$key` in response", 500)

        private fun JsonObject.isoOrMillis(key: String): Long {
            val primitive = (this[key] as? JsonPrimitive) ?: return 0L
            return primitive.longOrNull
                ?: SyncPayloadCodec.updatedAtFromWire(primitive.content)
        }
    }
}
