package com.voxcoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.voxcoach.core.data.db.entity.DrillAttemptEntity
import com.voxcoach.core.data.db.entity.EvResultEntity
import com.voxcoach.core.data.db.entity.GrammarPointEntity
import com.voxcoach.core.data.db.entity.FeedbackItemEntity
import com.voxcoach.core.data.db.entity.MistakeEntity
import com.voxcoach.core.data.db.entity.SessionEntity
import com.voxcoach.core.data.db.entity.TopicEntity
import com.voxcoach.core.data.db.entity.TurnEntity
import com.voxcoach.core.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics ORDER BY title")
    fun observeAll(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    suspend fun get(id: String): TopicEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(topics: List<TopicEntity>)

    @Query("SELECT COUNT(*) FROM topics")
    suspend fun count(): Int
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun get(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<SessionEntity?>

    @Query(
        "SELECT * FROM sessions WHERE deleted = 0 ORDER BY startedAt DESC LIMIT :limit",
    )
    suspend fun listRecent(limit: Int): List<SessionEntity>

    @Query(
        """
        SELECT COALESCE(SUM(durationMs), 0) FROM sessions
        WHERE deleted = 0 AND startedAt >= :dayStart AND startedAt < :dayEnd
        """,
    )
    suspend fun sumDuration(dayStart: Long, dayEnd: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(turnCount), 0) FROM sessions
        WHERE deleted = 0 AND startedAt >= :dayStart AND startedAt < :dayEnd
        """,
    )
    suspend fun sumTurns(dayStart: Long, dayEnd: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM sessions
        WHERE deleted = 0 AND startedAt >= :dayStart AND startedAt < :dayEnd
        """,
    )
    suspend fun countSessions(dayStart: Long, dayEnd: Long): Int
}

@Dao
interface TurnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(turn: TurnEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(turns: List<TurnEntity>)

    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY seq ASC")
    suspend fun listForSession(sessionId: String): List<TurnEntity>
}

@Dao
interface EvDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ev: EvResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<FeedbackItemEntity>)

    @Query("SELECT * FROM ev_results WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBySession(sessionId: String): EvResultEntity?

    @Query("SELECT * FROM ev_results WHERE id = :id LIMIT 1")
    suspend fun get(id: String): EvResultEntity?

    @Query("SELECT * FROM ev_results ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<EvResultEntity>

    @Query("SELECT * FROM feedback_items WHERE evId = :evId")
    suspend fun listItems(evId: String): List<FeedbackItemEntity>

    @Query("SELECT * FROM feedback_items WHERE id = :id LIMIT 1")
    suspend fun getItem(id: String): FeedbackItemEntity?

    @Query("UPDATE feedback_items SET collectedToMistakeAt = :at WHERE id = :id")
    suspend fun markCollected(id: String, at: Long)
}

@Dao
interface MistakeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mistake: MistakeEntity)

    @Query("SELECT * FROM mistakes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): MistakeEntity?

    @Query("SELECT * FROM mistakes WHERE status = 'OPEN' ORDER BY createdAt DESC")
    suspend fun listOpen(): List<MistakeEntity>

    @Query("SELECT * FROM mistakes WHERE status = 'OPEN' ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<MistakeEntity>>

    @Query("UPDATE mistakes SET status = 'MASTERED' WHERE id = :id")
    suspend fun markMastered(id: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface GrammarPointDao {
    @Query("SELECT * FROM grammar_points ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<GrammarPointEntity>>

    @Query("SELECT * FROM grammar_points WHERE id = :id LIMIT 1")
    suspend fun get(id: String): GrammarPointEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<GrammarPointEntity>)

    @Query("SELECT COUNT(*) FROM grammar_points")
    suspend fun count(): Int
}

@Dao
interface DrillAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: DrillAttemptEntity)

    @Query(
        """
        SELECT * FROM drill_attempts
        WHERE grammarPointId = :grammarPointId
        ORDER BY triedAt DESC LIMIT :limit
        """,
    )
    suspend fun listForPoint(grammarPointId: String, limit: Int): List<DrillAttemptEntity>
}

