package com.voxcoach.core.domain.repository

import com.voxcoach.core.domain.model.EvResult
import com.voxcoach.core.domain.model.DrillAttempt
import com.voxcoach.core.domain.model.FeedbackItem
import com.voxcoach.core.domain.model.GrammarPoint
import com.voxcoach.core.domain.model.Mistake
import com.voxcoach.core.domain.model.Session
import com.voxcoach.core.domain.model.TodayStats
import com.voxcoach.core.domain.model.Topic
import com.voxcoach.core.domain.model.Turn
import com.voxcoach.core.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface TopicRepository {
    fun observeTopics(): Flow<List<Topic>>
    suspend fun getTopic(id: String): Topic?
}

interface SessionRepository {
    suspend fun create(session: Session)
    suspend fun update(session: Session)
    suspend fun get(id: String): Session?
    fun observe(id: String): Flow<Session?>
    suspend fun listRecent(limit: Int = 20): List<Session>
    suspend fun todayStats(dayStartMs: Long, dayEndMs: Long): TodayStats
}

interface TurnRepository {
    suspend fun insertAll(turns: List<Turn>)
    suspend fun insert(turn: Turn)
    suspend fun listForSession(sessionId: String): List<Turn>
}

interface EvRepository {
    suspend fun save(result: EvResult)
    suspend fun getBySession(sessionId: String): EvResult?
    suspend fun get(evId: String): EvResult?
    suspend fun listFeedback(evId: String): List<FeedbackItem>
    suspend fun markCollected(itemId: String, at: Long)
}

interface MistakeRepository {
    suspend fun insert(mistake: Mistake)
    suspend fun listOpen(): List<Mistake>
    fun observeOpen(): Flow<List<Mistake>>
}

interface ProfileRepository {
    fun observe(): Flow<UserProfile>
    suspend fun get(): UserProfile
    suspend fun addPractice(durationMs: Long, turnCount: Int)
}

interface GrammarPointRepository {
    fun observeAll(): Flow<List<GrammarPoint>>
    suspend fun get(id: String): GrammarPoint?
    suspend fun ensureSeeded()
}

interface DrillAttemptRepository {
    suspend fun insert(attempt: DrillAttempt)
    suspend fun listForPoint(grammarPointId: String, limit: Int = 20): List<DrillAttempt>
}

