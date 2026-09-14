package com.voxcoach.core.domain.sync

/**
 * M3 SY pure sync logic (docs/04 §6.1, docs/05 §4.2–4.4, docs/06 M3).
 *
 * Rules encoded here (no Android, no network):
 * - whitelist: session / ev_result / mistake / vocab_note / profile / grammar_progress.
 * - turns + wav recordings NEVER sync (docs/05 §4.2 warning).
 * - LWW: newer updatedAt wins; ties break by larger deviceId (docs/05 §4.3).
 * - DELETE is a tombstone until a newer UPSERT covers it.
 * - server CHANGE_LOG seq is the pull cursor; cursor=0/full=true = full pull.
 *
 * Data payloads stay as opaque JSON strings so domain never depends on Room/DTOs.
 */
object SyncPolicy {
    val WHITELIST = setOf(
        SyncEntity.SESSION,
        SyncEntity.EV_RESULT,
        SyncEntity.MISTAKE,
        SyncEntity.VOCAB_NOTE,
        SyncEntity.PROFILE,
        SyncEntity.GRAMMAR_PROGRESS,
    )

    val NEVER_SYNC = setOf("turn", "turns", "recording", "audio", "wav")

    /** Push body guard from docs/05 §6 (also applied to sync payloads). */
    const val MAX_BODY_BYTES = 64 * 1024

    /** EV contract id shared by client + server (docs/05 §4.4). */
    const val EV_SCHEMA_VER = "ev.v1"

    /** EV rubric id shared by client + server (docs/05 §4.4). */
    const val EV_PROMPT_VER = "rubric-2026.09"
}

object SyncEntity {
    const val SESSION = "session"
    const val EV_RESULT = "ev_result"
    const val MISTAKE = "mistake"
    const val VOCAB_NOTE = "vocab_note"
    const val PROFILE = "profile"
    const val GRAMMAR_PROGRESS = "grammar_progress"
}

object SyncOp {
    const val UPSERT = "UPSERT"
    const val DELETE = "DELETE"
}

data class SyncChange(
    val entity: String,
    val op: String = SyncOp.UPSERT,
    val id: String,
    val updatedAt: Long,
    val deviceId: String,
    val dataJson: String? = null,
)

data class SyncLogEntry(
    val seq: Long,
    val change: SyncChange,
)

object SyncResolver {
    fun isSyncable(entity: String): Boolean =
        SyncPolicy.WHITELIST.contains(entity) && !SyncPolicy.NEVER_SYNC.contains(entity)

    /**
     * LWW winner between local and remote versions of the same (entity,id).
     * Returns the winning change, or null when both sides are identical.
     */
    fun resolve(local: SyncChange?, remote: SyncChange?): SyncChange? {
        if (local == null) return remote
        if (remote == null) return local
        require(local.entity == remote.entity && local.id == remote.id) {
            "LWW requires same (entity,id): $local vs $remote"
        }
        return when {
            remote.updatedAt > local.updatedAt -> remote
            local.updatedAt > remote.updatedAt -> local
            remote.deviceId > local.deviceId -> remote
            local.deviceId > remote.deviceId -> local
            else -> if (remote.op == local.op && remote.dataJson == local.dataJson) null else remote
        }
    }

    /** Cursor filter for pull: seq > cursor, or everything when full pull. */
    fun pullSince(log: List<SyncLogEntry>, cursor: Long, full: Boolean = false): List<SyncLogEntry> {
        if (full || cursor <= 0) return log.sortedBy { it.seq }
        return log.filter { it.seq > cursor }.sortedBy { it.seq }
    }

    /** Next cursor after applying [pulled] (monotonic, never moves backwards). */
    fun nextCursor(cursor: Long, pulled: List<SyncLogEntry>): Long {
        val max = pulled.maxOfOrNull { it.seq } ?: return cursor
        return maxOf(cursor, max)
    }
}
