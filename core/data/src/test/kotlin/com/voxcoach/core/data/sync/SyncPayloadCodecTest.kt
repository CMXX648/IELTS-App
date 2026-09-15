package com.voxcoach.core.data.sync

import com.google.common.truth.Truth.assertThat
import com.voxcoach.core.data.db.entity.EvResultEntity
import com.voxcoach.core.data.db.entity.MistakeEntity
import com.voxcoach.core.data.db.entity.SessionEntity
import com.voxcoach.core.data.db.entity.UserProfileEntity
import com.voxcoach.core.domain.sync.SyncChange
import com.voxcoach.core.domain.sync.SyncOp
import com.voxcoach.core.domain.sync.SyncPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/**
 * M3 SY wire codec tests (docs/05 §4.2–4.4 alignment, AGENTS.md rule 5).
 */
class SyncPayloadCodecTest {

    private val json = Json

    @Test
    fun `updatedAt roundtrip - millis to RFC3339 and back`() {
        val ms = 1787463150000L
        val wire = SyncPayloadCodec.updatedAtToWire(ms)
        assertThat(wire).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
        assertThat(SyncPayloadCodec.updatedAtFromWire(wire)).isEqualTo(ms)
    }

    @Test
    fun `updatedAtFromWire accepts bare epoch millis`() {
        assertThat(SyncPayloadCodec.updatedAtFromWire("1787463150000")).isEqualTo(1787463150000L)
    }

    @Test
    fun `encodeChange - UPSERT carries data object and RFC3339`() {
        val change = SyncChange(
            entity = "session",
            op = SyncOp.UPSERT,
            id = "s_1",
            updatedAt = 1787463150000,
            deviceId = "d_a",
            dataJson = """{"type":"CONVERSATION","durationMs":100}""",
        )
        val obj = SyncPayloadCodec.encodeChange(change)
        assertThat(obj["op"]?.toString()).contains("UPSERT")
        assertThat(obj["data"].toString()).contains("CONVERSATION")
        val wire = json.parseToJsonElement(obj.toString()).jsonObject
        val updatedAt = wire["updatedAt"].toString().trim('"')
        assertThat(SyncPayloadCodec.updatedAtFromWire(updatedAt)).isEqualTo(1787463150000)
    }

    @Test
    fun `encodeChange - DELETE carries null data`() {
        val change = SyncChange(
            entity = "session",
            op = SyncOp.DELETE,
            id = "s_1",
            updatedAt = 1000,
            deviceId = "d_a",
        )
        val obj = SyncPayloadCodec.encodeChange(change)
        assertThat(obj["data"].toString()).isEqualTo("null")
    }

    @Test
    fun `decodeChange - roundtrip through wire`() {
        val change = SyncChange(
            entity = "mistake",
            op = SyncOp.UPSERT,
            id = "m_1",
            updatedAt = 1787463150000,
            deviceId = "d_b",
            dataJson = """{"dimension":"gra","quote":"q","correction":"c"}""",
        )
        val decoded = SyncPayloadCodec.decodeChange(SyncPayloadCodec.encodeChange(change))!!
        assertThat(decoded).isEqualTo(change)
    }

    @Test
    fun `decodeChange - rejects malformed`() {
        val bad = json.parseToJsonElement("""{"entity":"session"}""")
        assertThat(SyncPayloadCodec.decodeChange(bad)).isNull()
    }

    @Test
    fun `sessionPayload - docs aligned field names`() {
        val entity = SessionEntity(
            id = "s_1", type = "CONVERSATION", subtype = "P2", topicId = "T-p2-person",
            stage = "S1", startedAt = 1000, endedAt = 2000, durationMs = 1000,
            turnCount = 14, audioPath = "/x/y.wav", evId = "ev_1", status = "COMPLETED",
            dirty = true, updatedAt = 3000, deleted = false,
        )
        val obj = json.parseToJsonElement(SyncPayloadCodec.sessionPayload(entity)).jsonObject
        // Only whitelisted header fields cross the wire — audio never syncs.
        assertThat(obj.keys).doesNotContain("audioPath")
        assertThat(obj["type"]?.toString()).contains("CONVERSATION")
        assertThat(obj["topicId"]?.toString()).contains("T-p2-person")
        assertThat(obj["turnCount"]?.toString()).contains("14")
        assertThat(obj["deleted"]?.toString()).contains("false")
    }

    @Test
    fun `evResultPayload - schemaVer frozen and engine nested`() {
        val entity = EvResultEntity(
            id = "ev_1", sessionId = "s_1", overallBand = 7.5,
            dimsJson = """{"fc":{"score":7.0,"comment":"","evidence":[]},"lr":{"score":7.5,"comment":"","evidence":[]},"gra":{"score":7.0,"comment":"","evidence":[]},"p":{"score":8.0,"comment":"","evidence":[]}}""",
            itemsJson = "[]", highlightsJson = "[]",
            engineVer = "mimo-v2.5", promptVer = SyncPolicy.EV_PROMPT_VER, createdAt = 1000,
        )
        val obj = json.parseToJsonElement(SyncPayloadCodec.evResultPayload(entity)).jsonObject
        assertThat(obj["schemaVer"]?.toString()).contains(SyncPolicy.EV_SCHEMA_VER)
        val engine = obj["engine"]!!.jsonObject
        assertThat(engine["model"]?.toString()).contains("mimo-v2.5")
        assertThat(engine["promptVer"]?.toString()).contains(SyncPolicy.EV_PROMPT_VER)
    }

    @Test
    fun `mistakePayload - docs aligned field names`() {
        val entity = MistakeEntity(
            id = "m_1", sourceItemId = "i_1", sessionId = "s_1", dimension = "gra",
            quote = "If I know...", correction = "If I had known...", why = "w",
            grammarPointId = "gp_1", status = "OPEN", retriedCount = 0,
            lastRetriedAt = null, createdAt = 1000, updatedAt = 2000,
        )
        val obj = json.parseToJsonElement(SyncPayloadCodec.mistakePayload(entity)).jsonObject
        assertThat(obj.keys).containsExactly(
            "sessionId", "dimension", "quote", "correction", "why",
            "grammarPointId", "status", "retriedCount", "createdAt",
        )
    }

    @Test
    fun `profilePayload - docs aligned field names`() {
        val entity = UserProfileEntity(
            id = 1, version = 1, stage = "S1", targetBand = 7.5,
            dimTrendCacheJson = null, streak = 3, totalDurationMs = 10000,
            totalTurnCount = 20, totalSessionCount = 2, updatedAt = 1000,
        )
        val obj = json.parseToJsonElement(SyncPayloadCodec.profilePayload(entity)).jsonObject
        assertThat(obj.keys).containsExactly(
            "stage", "targetBand", "streak", "totalDurationMs",
            "totalTurnCount", "totalSessionCount",
        )
    }

    @Test
    fun `evItems - maps docs 4_4 items to feedback row fields`() {
        val payload = json.parseToJsonElement(
            """
            {"items":[{"id":"i_1","dim":"gra","category":"grammar_tense",
              "quote":"q","correction":"c","why":"w",
              "model":["Had I known..."],"tRange":[128000,156000]}]}
            """.trimIndent(),
        ).jsonObject
        val items = SyncPayloadCodec.evItems(payload)
        assertThat(items).hasSize(1)
        val item = items[0]
        assertThat(item.id).isEqualTo("i_1")
        assertThat(item.dimension).isEqualTo("gra")
        assertThat(item.modelJson).contains("Had I known")
        assertThat(item.tRangeJson).contains("128000")
    }
}
