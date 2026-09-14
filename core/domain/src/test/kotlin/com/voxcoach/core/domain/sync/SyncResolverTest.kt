package com.voxcoach.core.domain.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncResolverTest {
    private fun change(
        id: String = "s1",
        entity: String = SyncEntity.SESSION,
        op: String = SyncOp.UPSERT,
        updatedAt: Long = 100L,
        deviceId: String = "d_pixel",
    ) = SyncChange(entity = entity, op = op, id = id, updatedAt = updatedAt, deviceId = deviceId)

    @Test
    fun whitelist_matchesDocsWhiteList() {
        assertThat(SyncResolver.isSyncable(SyncEntity.SESSION)).isTrue()
        assertThat(SyncResolver.isSyncable(SyncEntity.EV_RESULT)).isTrue()
        assertThat(SyncResolver.isSyncable(SyncEntity.MISTAKE)).isTrue()
        assertThat(SyncResolver.isSyncable(SyncEntity.VOCAB_NOTE)).isTrue()
        assertThat(SyncResolver.isSyncable(SyncEntity.PROFILE)).isTrue()
        assertThat(SyncResolver.isSyncable(SyncEntity.GRAMMAR_PROGRESS)).isTrue()
    }

    @Test
    fun turnsAndRecordings_neverSync() {
        assertThat(SyncResolver.isSyncable("turn")).isFalse()
        assertThat(SyncResolver.isSyncable("turns")).isFalse()
        assertThat(SyncResolver.isSyncable("recording")).isFalse()
        assertThat(SyncResolver.isSyncable("audio")).isFalse()
    }

    @Test
    fun lww_newerUpdatedAtWins() {
        val local = change(updatedAt = 100L, deviceId = "d_a")
        val remote = change(updatedAt = 200L, deviceId = "d_a")
        assertThat(SyncResolver.resolve(local, remote)).isEqualTo(remote)
        assertThat(SyncResolver.resolve(remote, local)).isEqualTo(remote)
    }

    @Test
    fun lww_tieBreaksByDeviceId() {
        val local = change(updatedAt = 100L, deviceId = "d_a")
        val remote = change(updatedAt = 100L, deviceId = "d_b")
        assertThat(SyncResolver.resolve(local, remote)).isEqualTo(remote)
        assertThat(SyncResolver.resolve(remote, local)).isEqualTo(remote)
    }

    @Test
    fun tombstone_beatsOlderUpsert() {
        val local = change(op = SyncOp.UPSERT, updatedAt = 100L)
        val remote = change(op = SyncOp.DELETE, updatedAt = 150L)
        assertThat(SyncResolver.resolve(local, remote)).isEqualTo(remote)
    }

    @Test
    fun pullSince_supportsCursorAndFull() {
        val log = listOf(
            SyncLogEntry(1, change(id = "a")),
            SyncLogEntry(2, change(id = "b")),
            SyncLogEntry(3, change(id = "c")),
        )
        assertThat(SyncResolver.pullSince(log, cursor = 1).map { it.seq })
            .containsExactly(2L, 3L).inOrder()
        assertThat(SyncResolver.pullSince(log, cursor = 99)).isEmpty()
        assertThat(SyncResolver.pullSince(log, cursor = 99, full = true)).hasSize(3)
        assertThat(SyncResolver.nextCursor(1, SyncResolver.pullSince(log, 1))).isEqualTo(3L)
        assertThat(SyncResolver.nextCursor(5, emptyList())).isEqualTo(5L)
    }

    @Test
    fun evVersions_matchBackendContract() {
        assertThat(SyncPolicy.EV_SCHEMA_VER).isEqualTo("ev.v1")
        assertThat(SyncPolicy.EV_PROMPT_VER).isEqualTo("rubric-2026.09")
    }
}
