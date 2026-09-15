package com.voxcoach.core.data.sync

import com.voxcoach.core.data.db.dao.EvDao
import com.voxcoach.core.data.db.dao.MistakeDao
import com.voxcoach.core.data.db.dao.ProfileDao
import com.voxcoach.core.data.db.dao.SessionDao
import com.voxcoach.core.data.db.dao.SyncStateDao
import com.voxcoach.core.data.db.entity.EvResultEntity
import com.voxcoach.core.data.db.entity.FeedbackItemEntity
import com.voxcoach.core.data.db.entity.MistakeEntity
import com.voxcoach.core.data.db.entity.SessionEntity
import com.voxcoach.core.data.db.entity.SyncStateEntity
import com.voxcoach.core.data.db.entity.UserProfileEntity
import com.voxcoach.core.domain.settings.SyncSettingsRepository
import com.voxcoach.core.domain.sync.SyncChange
import com.voxcoach.core.domain.sync.SyncEntity
import com.voxcoach.core.domain.sync.SyncGateway
import com.voxcoach.core.domain.sync.SyncGatewayException
import com.voxcoach.core.domain.sync.SyncOp
import com.voxcoach.core.domain.sync.SyncResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/**
 * M3 SY orchestrator (docs/05 §4.2–4.3):
 *
 * 1. push pending local changes (entity updatedAt > sync_state.updatedAt);
 * 2. pull server change_log since the stored cursor;
 * 3. apply each pulled change via [SyncResolver.resolve] LWW (remote wins →
 *    write payload + advance sync_state; local wins → stays pending);
 * 4. advance cursor from the pull response only.
 *
 * `vocab_note` / `grammar_progress` stay whitelist-planned but have no client
 * Room tables yet (VB-04 pending) — they are skipped until those land.
 * Recordings never leave the device (docs/05 §4.2 warning).
 */
@Singleton
class SyncEngine @Inject constructor(
    private val sessionDao: SessionDao,
    private val evDao: EvDao,
    private val mistakeDao: MistakeDao,
    private val profileDao: ProfileDao,
    private val syncStateDao: SyncStateDao,
    private val gateway: SyncGateway,
    private val settingsRepository: SyncSettingsRepository,
) {

    sealed class Outcome {
        data object Disabled : Outcome()
        data object NotRegistered : Outcome()
        data class Done(
            val pushed: Int,
            val conflicts: List<String>,
            val appliedFromRemote: Int,
            val cursor: Long,
        ) : Outcome()

        data class Failed(val error: SyncGatewayException) : Outcome()
    }

    suspend fun runSync(nowMs: () -> Long = { System.currentTimeMillis() }): Outcome {
        val settings = settingsRepository.settings.first()
        if (!settings.canSync) return Outcome.Disabled
        val deviceKey = settingsRepository.deviceKey() ?: return Outcome.NotRegistered
        val deviceId = checkNotNull(settings.deviceId)
        val baseUrl = settings.serverBaseUrl

        return try {
            // 1) push local pending changes
            val pending = collectPending(deviceId)
            var pushed = 0
            var conflicts: List<String> = emptyList()
            if (pending.isNotEmpty()) {
                val result = gateway.push(baseUrl, deviceKey, pending)
                pushed = result.pushed
                conflicts = result.conflictIds
                pending.forEach { change ->
                    // LWW losers stay unsynced: only advance state for accepted rows.
                    if (change.id !in conflicts.toSet()) {
                        syncStateDao.upsert(
                            SyncStateEntity(change.entity, change.id, change.updatedAt, change.op, nowMs()),
                        )
                    }
                }
            }

            // 2) pull remote change_log
            val cursor = settingsRepository.cursor()
            val pull = gateway.pull(baseUrl, deviceKey, cursor, full = cursor <= 0L)

            // 3) apply with LWW
            var applied = 0
            for (change in pull.changes) {
                if (applyRemote(change, deviceId, nowMs())) applied++
            }

            // 4) cursor from pull response only (push newCursor may skip others' changes)
            settingsRepository.setCursor(pull.cursor)

            Outcome.Done(pushed, conflicts, applied, pull.cursor)
        } catch (e: SyncGatewayException) {
            Outcome.Failed(e)
        }
    }

    // ------------------------------------------------------------------
    // Pending collection
    // ------------------------------------------------------------------

    internal suspend fun collectPending(deviceId: String): List<SyncChange> {
        val states = syncStateDao.getAll().associateBy { it.entity to it.entityId }
        val changes = mutableListOf<SyncChange>()

        fun pending(entity: String, id: String, updatedAt: Long): Boolean =
            (states[entity to id]?.updatedAt ?: Long.MIN_VALUE) < updatedAt

        for (s in sessionDao.listAll()) {
            if (!pending(SyncEntity.SESSION, s.id, s.updatedAt)) continue
            changes += if (s.deleted) {
                SyncChange(SyncEntity.SESSION, SyncOp.DELETE, s.id, s.updatedAt, deviceId)
            } else {
                SyncChange(SyncEntity.SESSION, SyncOp.UPSERT, s.id, s.updatedAt, deviceId, SyncPayloadCodec.sessionPayload(s))
            }
        }
        for (ev in evDao.listAll()) {
            if (!pending(SyncEntity.EV_RESULT, ev.id, ev.createdAt)) continue
            changes += SyncChange(
                SyncEntity.EV_RESULT, SyncOp.UPSERT, ev.id, ev.createdAt, deviceId,
                SyncPayloadCodec.evResultPayload(ev),
            )
        }
        for (m in mistakeDao.listAll()) {
            if (!pending(SyncEntity.MISTAKE, m.id, m.updatedAt)) continue
            changes += SyncChange(
                SyncEntity.MISTAKE, SyncOp.UPSERT, m.id, m.updatedAt, deviceId,
                SyncPayloadCodec.mistakePayload(m),
            )
        }
        profileDao.get()?.let { p ->
            if (pending(SyncEntity.PROFILE, PROFILE_ID, p.updatedAt)) {
                changes += SyncChange(
                    SyncEntity.PROFILE, SyncOp.UPSERT, PROFILE_ID, p.updatedAt, deviceId,
                    SyncPayloadCodec.profilePayload(p),
                )
            }
        }
        return changes
    }

    // ------------------------------------------------------------------
    // Remote apply (LWW)
    // ------------------------------------------------------------------

    internal suspend fun applyRemote(remote: SyncChange, localDeviceId: String, nowMs: () -> Long): Boolean {
        if (!SyncResolver.isSyncable(remote.entity)) return false
        val local: SyncChange? = localVersion(remote, localDeviceId)
        val winner = SyncResolver.resolve(local, remote) ?: return false // identical

        val applied = if (winner === remote) {
            writeRemote(remote)
        } else {
            false // local wins: leave row dirty, it will be pushed next cycle
        }
        if (applied) {
            syncStateDao.upsert(
                SyncStateEntity(remote.entity, remote.id, remote.updatedAt, remote.op, nowMs()),
            )
        }
        return applied
    }

    private suspend fun localVersion(remote: SyncChange, localDeviceId: String): SyncChange? {
        val updatedAt = when (remote.entity) {
            SyncEntity.SESSION -> sessionDao.get(remote.id)?.let { it.updatedAt to it.deleted }
            SyncEntity.EV_RESULT -> evDao.get(remote.id)?.let { it.createdAt to false }
            SyncEntity.MISTAKE -> mistakeDao.get(remote.id)?.let { it.updatedAt to false }
            SyncEntity.PROFILE -> profileDao.get()?.let { it.updatedAt to false }
            else -> null
        } ?: return null
        return SyncChange(
            entity = remote.entity,
            op = if (updatedAt.second) SyncOp.DELETE else SyncOp.UPSERT,
            id = remote.id,
            updatedAt = updatedAt.first,
            deviceId = localDeviceId,
        )
    }

    private suspend fun writeRemote(remote: SyncChange): Boolean {
        val payload: JsonObject? = remote.dataJson
            ?.let { runCatching { SyncPayloadCodec.json.parseToJsonElement(it) }.getOrNull() }
            ?.let { it as? JsonObject }
        return when (remote.entity) {
            SyncEntity.SESSION -> writeRemoteSession(remote, payload)
            SyncEntity.EV_RESULT -> payload != null && writeRemoteEv(remote, payload)
            SyncEntity.MISTAKE -> payload != null && writeRemoteMistake(remote, payload)
            SyncEntity.PROFILE -> payload != null && writeRemoteProfile(remote, payload)
            else -> false
        }
    }

    private suspend fun writeRemoteSession(remote: SyncChange, payload: JsonObject?): Boolean {
        val existing = sessionDao.get(remote.id)
        if (remote.op == SyncOp.DELETE) {
            val tombstone = existing?.copy(deleted = true, updatedAt = remote.updatedAt, dirty = false)
                ?: SessionEntity(
                    id = remote.id, type = "CONVERSATION", subtype = "FREE", topicId = null,
                    stage = null, startedAt = remote.updatedAt, endedAt = null, durationMs = 0,
                    turnCount = 0, audioPath = null, evId = null, status = "COMPLETED",
                    dirty = false, updatedAt = remote.updatedAt, deleted = true,
                )
            sessionDao.upsert(tombstone)
            return true
        }
        val fields = payload?.let { SyncPayloadCodec.SessionFields(it) } ?: return false
        sessionDao.upsert(
            SessionEntity(
                id = remote.id,
                type = fields.type,
                subtype = fields.subtype,
                topicId = fields.topicId,
                stage = fields.stage,
                startedAt = fields.startedAt,
                endedAt = fields.endedAt,
                durationMs = fields.durationMs,
                turnCount = fields.turnCount,
                // Local recordings stay local; remote sessions have no audio here.
                audioPath = existing?.audioPath,
                evId = existing?.evId,
                status = existing?.status ?: "COMPLETED",
                dirty = false,
                updatedAt = remote.updatedAt,
                deleted = fields.deleted,
            ),
        )
        return true
    }

    private suspend fun writeRemoteEv(remote: SyncChange, payload: JsonObject): Boolean {
        val fields = SyncPayloadCodec.EvFields(payload)
        evDao.upsert(
            EvResultEntity(
                id = remote.id,
                sessionId = fields.sessionId,
                overallBand = fields.overallBand,
                dimsJson = fields.dimsJson,
                itemsJson = fields.itemsJson,
                highlightsJson = fields.highlightsJson,
                engineVer = fields.engineVer,
                promptVer = fields.promptVer,
                createdAt = fields.createdAt,
            ),
        )
        val items = SyncPayloadCodec.evItems(payload)
        if (items.isNotEmpty()) {
            evDao.upsertItems(
                items.map { item ->
                    val local = evDao.getItem(item.id)
                    FeedbackItemEntity(
                        id = item.id,
                        evId = remote.id,
                        dimension = item.dimension,
                        category = item.category,
                        quote = item.quote,
                        correction = item.correction,
                        why = item.why,
                        modelJson = item.modelJson,
                        tRangeJson = item.tRangeJson,
                        collectedToMistakeAt = local?.collectedToMistakeAt,
                    )
                },
            )
        }
        return true
    }

    private suspend fun writeRemoteMistake(remote: SyncChange, payload: JsonObject): Boolean {
        val fields = SyncPayloadCodec.MistakeFields(payload)
        mistakeDao.insert(
            MistakeEntity(
                id = remote.id,
                sourceItemId = null,
                sessionId = fields.sessionId,
                dimension = fields.dimension,
                quote = fields.quote,
                correction = fields.correction,
                why = fields.why,
                grammarPointId = fields.grammarPointId,
                status = fields.status,
                retriedCount = fields.retriedCount,
                lastRetriedAt = null,
                createdAt = fields.createdAt,
                updatedAt = remote.updatedAt,
            ),
        )
        return true
    }

    private suspend fun writeRemoteProfile(remote: SyncChange, payload: JsonObject): Boolean {
        val fields = SyncPayloadCodec.ProfileFields(payload)
        val existing = profileDao.get()
        profileDao.upsert(
            UserProfileEntity(
                id = 1,
                version = existing?.version ?: 1,
                stage = fields.stage,
                targetBand = fields.targetBand,
                // Local-only cache; rebuilt by PF after apply.
                dimTrendCacheJson = existing?.dimTrendCacheJson,
                streak = fields.streak,
                totalDurationMs = fields.totalDurationMs,
                totalTurnCount = fields.totalTurnCount,
                totalSessionCount = fields.totalSessionCount,
                updatedAt = remote.updatedAt,
            ),
        )
        return true
    }

    companion object {
        const val PROFILE_ID = "1"
    }
}
