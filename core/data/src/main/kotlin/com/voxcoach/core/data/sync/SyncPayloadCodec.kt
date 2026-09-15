package com.voxcoach.core.data.sync

import com.voxcoach.core.data.db.entity.EvResultEntity
import com.voxcoach.core.data.db.entity.MistakeEntity
import com.voxcoach.core.data.db.entity.SessionEntity
import com.voxcoach.core.data.db.entity.UserProfileEntity
import com.voxcoach.core.domain.sync.SyncChange
import com.voxcoach.core.domain.sync.SyncOp
import com.voxcoach.core.domain.sync.SyncPolicy
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * M3 SY wire codec (docs/05 §4.2–4.4). Pure Kotlin — JVM-testable.
 *
 * - SyncChange.updatedAt (epoch millis) ⇄ RFC3339 UTC string on the wire.
 * - dataJson (opaque) ⇄ JSON object under `data`.
 * - Entity ⇄ payload JSON with field names aligned to docs/05 §4.2/§4.4
 *   (AGENTS.md rule 5: entity/API/Room 三处对齐).
 */
object SyncPayloadCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ------------------------------------------------------------------
    // Timestamps
    // ------------------------------------------------------------------

    /** Epoch millis → RFC3339 UTC (e.g. `2026-09-03T08:12:30Z`). */
    fun updatedAtToWire(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    /** RFC3339 UTC string (or bare epoch millis fallback) → epoch millis. */
    fun updatedAtFromWire(raw: String): Long =
        runCatching { Instant.parse(raw).toEpochMilli() }
            .getOrElse { raw.toLongOrNull() ?: 0L }

    // ------------------------------------------------------------------
    // Change ⇄ wire JSON
    // ------------------------------------------------------------------

    fun encodeChange(change: SyncChange): JsonObject = buildJsonObject {
        put("entity", change.entity)
        put("op", change.op)
        put("id", change.id)
        put("updatedAt", updatedAtToWire(change.updatedAt))
        put("deviceId", change.deviceId)
        put(
            "data",
            when {
                change.op == SyncOp.DELETE || change.dataJson == null -> JsonNull
                else -> runCatching { json.parseToJsonElement(change.dataJson) }.getOrDefault(JsonNull)
            },
        )
    }

    fun decodeChange(element: JsonElement): SyncChange? {
        if (element !is JsonObject) return null
        val entity = element["entity"]?.jsonPrimitive?.contentOrNull ?: return null
        val id = element["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val op = element["op"]?.jsonPrimitive?.contentOrNull ?: SyncOp.UPSERT
        val deviceId = element["deviceId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val updatedAtEl = element["updatedAt"]
        val updatedAt = when {
            updatedAtEl == null || updatedAtEl is JsonNull -> 0L
            updatedAtEl.jsonPrimitive.longOrNull != null -> updatedAtEl.jsonPrimitive.long
            else -> updatedAtFromWire(updatedAtEl.jsonPrimitive.content)
        }
        val data = element["data"]
        val dataJson = when (data) {
            null, is JsonNull -> null
            else -> data.toString()
        }
        return SyncChange(
            entity = entity,
            op = op,
            id = id,
            updatedAt = updatedAt,
            deviceId = deviceId,
            dataJson = dataJson,
        )
    }

    // ------------------------------------------------------------------
    // Room entity → payload JSON (push)
    // ------------------------------------------------------------------

    fun sessionPayload(e: SessionEntity): String = buildJsonObject {
        put("type", e.type)
        put("subtype", e.subtype)
        put("topicId", e.topicId)
        put("stage", e.stage)
        put("startedAt", e.startedAt)
        put("endedAt", e.endedAt)
        put("durationMs", e.durationMs)
        put("turnCount", e.turnCount)
        put("deleted", e.deleted)
    }.toString()

    fun evResultPayload(e: EvResultEntity): String = buildJsonObject {
        put("schemaVer", SyncPolicy.EV_SCHEMA_VER)
        put("sessionId", e.sessionId)
        put("overallBand", e.overallBand)
        put("dims", parsedOrNull(e.dimsJson))
        put("items", parsedOrNull(e.itemsJson) ?: JsonArray(emptyList()))
        put("highlights", parsedOrNull(e.highlightsJson))
        put("engine", buildJsonObject {
            put("model", e.engineVer)
            put("promptVer", e.promptVer)
        })
        put("createdAt", e.createdAt)
    }.toString()

    fun mistakePayload(e: MistakeEntity): String = buildJsonObject {
        put("sessionId", e.sessionId)
        put("dimension", e.dimension)
        put("quote", e.quote)
        put("correction", e.correction)
        put("why", e.why)
        put("grammarPointId", e.grammarPointId)
        put("status", e.status)
        put("retriedCount", e.retriedCount)
        put("createdAt", e.createdAt)
    }.toString()

    fun profilePayload(e: UserProfileEntity): String = buildJsonObject {
        put("stage", e.stage)
        put("targetBand", e.targetBand)
        put("streak", e.streak)
        put("totalDurationMs", e.totalDurationMs)
        put("totalTurnCount", e.totalTurnCount)
        put("totalSessionCount", e.totalSessionCount)
    }.toString()

    private fun parsedOrNull(raw: String?): JsonElement? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

    // ------------------------------------------------------------------
    // Payload JSON → Room fields (pull apply)
    // ------------------------------------------------------------------

    fun SessionFields(
        payload: JsonObject,
    ): SessionFields = SessionFields(
        type = payload.str("type") ?: "CONVERSATION",
        subtype = payload.str("subtype") ?: "FREE",
        topicId = payload.str("topicId"),
        stage = payload.str("stage"),
        startedAt = payload.long("startedAt") ?: 0L,
        endedAt = payload.long("endedAt"),
        durationMs = payload.long("durationMs") ?: 0L,
        turnCount = payload.int("turnCount") ?: 0,
        deleted = payload.bool("deleted") ?: false,
    )

    fun MistakeFields(payload: JsonObject): MistakeFields = MistakeFields(
        sessionId = payload.str("sessionId"),
        dimension = payload.str("dimension").orEmpty(),
        quote = payload.str("quote").orEmpty(),
        correction = payload.str("correction").orEmpty(),
        why = payload.str("why").orEmpty(),
        grammarPointId = payload.str("grammarPointId"),
        status = payload.str("status") ?: "OPEN",
        retriedCount = payload.int("retriedCount") ?: 0,
        createdAt = payload.long("createdAt") ?: 0L,
    )

    fun EvFields(payload: JsonObject): EvFields = EvFields(
        sessionId = payload.str("sessionId").orEmpty(),
        overallBand = payload.double("overallBand") ?: 0.0,
        dimsJson = payload["dims"]?.toString() ?: "{}",
        itemsJson = payload["items"]?.toString() ?: "[]",
        highlightsJson = payload["highlights"]?.toString() ?: "[]",
        engineVer = payload.obj("engine")?.str("model").orEmpty(),
        promptVer = payload.obj("engine")?.str("promptVer").orEmpty(),
        createdAt = payload.long("createdAt") ?: 0L,
    )

    fun ProfileFields(payload: JsonObject): ProfileFields = ProfileFields(
        stage = payload.str("stage") ?: "S0",
        targetBand = payload.double("targetBand") ?: 7.5,
        streak = payload.int("streak") ?: 0,
        totalDurationMs = payload.long("totalDurationMs") ?: 0L,
        totalTurnCount = payload.int("totalTurnCount") ?: 0,
        totalSessionCount = payload.int("totalSessionCount") ?: 0,
    )

    /** docs/05 §4.4 items[] → feedback_items rows (dimension/model/tRange JSON). */
    fun evItems(payload: JsonObject): List<EvItemFields> =
        (payload["items"] as? JsonArray)?.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            EvItemFields(
                id = item.str("id") ?: return@mapNotNull null,
                dimension = item.str("dim").orEmpty(),
                category = item.str("category").orEmpty(),
                quote = item.str("quote").orEmpty(),
                correction = item.str("correction").orEmpty(),
                why = item.str("why").orEmpty(),
                modelJson = (item["model"] as? JsonArray)?.toString() ?: "[]",
                tRangeJson = item["tRange"]?.takeIf { it !is JsonNull }?.toString(),
            )
        }.orEmpty()

    // JSON helpers ------------------------------------------------------------

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
}

data class SessionFields(
    val type: String,
    val subtype: String,
    val topicId: String?,
    val stage: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val durationMs: Long,
    val turnCount: Int,
    val deleted: Boolean,
)

data class MistakeFields(
    val sessionId: String?,
    val dimension: String,
    val quote: String,
    val correction: String,
    val why: String,
    val grammarPointId: String?,
    val status: String,
    val retriedCount: Int,
    val createdAt: Long,
)

data class EvFields(
    val sessionId: String,
    val overallBand: Double,
    val dimsJson: String,
    val itemsJson: String,
    val highlightsJson: String,
    val engineVer: String,
    val promptVer: String,
    val createdAt: Long,
)

data class ProfileFields(
    val stage: String,
    val targetBand: Double,
    val streak: Int,
    val totalDurationMs: Long,
    val totalTurnCount: Int,
    val totalSessionCount: Int,
)

data class EvItemFields(
    val id: String,
    val dimension: String,
    val category: String,
    val quote: String,
    val correction: String,
    val why: String,
    val modelJson: String,
    val tRangeJson: String?,
)
