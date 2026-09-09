package com.voxcoach.core.data.repo

import com.voxcoach.core.data.db.SeedRunner
import com.voxcoach.core.data.db.dao.DrillAttemptDao
import com.voxcoach.core.data.db.dao.EvDao
import com.voxcoach.core.data.db.dao.GrammarPointDao
import com.voxcoach.core.data.db.dao.MistakeDao
import com.voxcoach.core.data.db.dao.ProfileDao
import com.voxcoach.core.data.db.dao.SessionDao
import com.voxcoach.core.data.db.dao.TopicDao
import com.voxcoach.core.data.db.dao.TurnDao
import com.voxcoach.core.data.db.toDomain
import com.voxcoach.core.data.db.toEntity
import com.voxcoach.core.domain.model.DrillAttempt
import com.voxcoach.core.domain.model.EvResult
import com.voxcoach.core.domain.model.GrammarPoint
import com.voxcoach.core.domain.model.FeedbackItem
import com.voxcoach.core.domain.model.Mistake
import com.voxcoach.core.domain.model.Session
import com.voxcoach.core.domain.model.TodayStats
import com.voxcoach.core.domain.model.Topic
import com.voxcoach.core.domain.model.Turn
import com.voxcoach.core.domain.model.UserProfile
import com.voxcoach.core.domain.repository.DrillAttemptRepository
import com.voxcoach.core.domain.repository.EvRepository
import com.voxcoach.core.domain.repository.GrammarPointRepository
import com.voxcoach.core.domain.repository.MistakeRepository
import com.voxcoach.core.domain.repository.ProfileRepository
import com.voxcoach.core.domain.repository.SessionRepository
import com.voxcoach.core.domain.repository.TopicRepository
import com.voxcoach.core.domain.repository.TurnRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val topicDao: TopicDao,
    private val seedRunner: SeedRunner,
) : TopicRepository {
    override fun observeTopics(): Flow<List<Topic>> =
        topicDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getTopic(id: String): Topic? {
        seedRunner.ensureSeeded()
        return topicDao.get(id)?.toDomain()
    }
}

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
) : SessionRepository {
    override suspend fun create(session: Session) = sessionDao.upsert(session.toEntity())
    override suspend fun update(session: Session) = sessionDao.upsert(session.toEntity())
    override suspend fun get(id: String): Session? = sessionDao.get(id)?.toDomain()
    override fun observe(id: String): Flow<Session?> = sessionDao.observe(id).map { it?.toDomain() }
    override suspend fun listRecent(limit: Int): List<Session> =
        sessionDao.listRecent(limit).map { it.toDomain() }

    override suspend fun todayStats(dayStartMs: Long, dayEndMs: Long): TodayStats = TodayStats(
        durationMs = sessionDao.sumDuration(dayStartMs, dayEndMs),
        turnCount = sessionDao.sumTurns(dayStartMs, dayEndMs),
        sessionCount = sessionDao.countSessions(dayStartMs, dayEndMs),
    )
}

@Singleton
class TurnRepositoryImpl @Inject constructor(
    private val turnDao: TurnDao,
) : TurnRepository {
    override suspend fun insertAll(turns: List<Turn>) = turnDao.insertAll(turns.map { it.toEntity() })
    override suspend fun insert(turn: Turn) = turnDao.insert(turn.toEntity())
    override suspend fun listForSession(sessionId: String): List<Turn> =
        turnDao.listForSession(sessionId).map { it.toDomain() }
}

@Singleton
class EvRepositoryImpl @Inject constructor(
    private val evDao: EvDao,
) : EvRepository {
    override suspend fun save(result: EvResult) {
        evDao.upsert(result.toEntity())
        if (result.items.isNotEmpty()) {
            evDao.upsertItems(result.items.map { it.toEntity() })
        }
    }

    override suspend fun getBySession(sessionId: String): EvResult? {
        val entity = evDao.getBySession(sessionId) ?: return null
        val items = evDao.listItems(entity.id).map { it.toDomain() }
        return entity.toDomain(items)
    }

    override suspend fun get(evId: String): EvResult? {
        val entity = evDao.get(evId) ?: return null
        val items = evDao.listItems(entity.id).map { it.toDomain() }
        return entity.toDomain(items)
    }

    override suspend fun listFeedback(evId: String): List<FeedbackItem> =
        evDao.listItems(evId).map { it.toDomain() }

    override suspend fun markCollected(itemId: String, at: Long) =
        evDao.markCollected(itemId, at)
}

@Singleton
class MistakeRepositoryImpl @Inject constructor(
    private val mistakeDao: MistakeDao,
) : MistakeRepository {
    override suspend fun insert(mistake: Mistake) = mistakeDao.insert(mistake.toEntity())
    override suspend fun listOpen(): List<Mistake> = mistakeDao.listOpen().map { it.toDomain() }
    override fun observeOpen(): Flow<List<Mistake>> =
        mistakeDao.observeOpen().map { list -> list.map { it.toDomain() } }
}

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val seedRunner: SeedRunner,
) : ProfileRepository {
    override fun observe(): Flow<UserProfile> =
        profileDao.observe().map { entity ->
            entity?.toDomain() ?: UserProfile()
        }

    override suspend fun get(): UserProfile {
        seedRunner.ensureSeeded()
        return profileDao.get()?.toDomain() ?: UserProfile()
    }

    override suspend fun addPractice(durationMs: Long, turnCount: Int) {
        seedRunner.ensureSeeded()
        val current = profileDao.get()?.toDomain() ?: UserProfile()
        val updated = current.copy(
            totalDurationMs = current.totalDurationMs + durationMs,
            totalTurnCount = current.totalTurnCount + turnCount,
            totalSessionCount = current.totalSessionCount + 1,
            streak = current.streak + 1,
            updatedAt = System.currentTimeMillis(),
        )
        profileDao.upsert(updated.toEntity())
    }
}

@Singleton
class GrammarPointRepositoryImpl @Inject constructor(
    private val grammarPointDao: GrammarPointDao,
    private val seedRunner: SeedRunner,
) : GrammarPointRepository {
    override fun observeAll(): Flow<List<GrammarPoint>> =
        grammarPointDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): GrammarPoint? {
        seedRunner.ensureSeeded()
        return grammarPointDao.get(id)?.toDomain()
    }

    override suspend fun ensureSeeded() {
        seedRunner.ensureSeeded()
    }
}

@Singleton
class DrillAttemptRepositoryImpl @Inject constructor(
    private val drillAttemptDao: DrillAttemptDao,
) : DrillAttemptRepository {
    override suspend fun insert(attempt: DrillAttempt) =
        drillAttemptDao.insert(attempt.toEntity())

    override suspend fun listForPoint(grammarPointId: String, limit: Int): List<DrillAttempt> =
        drillAttemptDao.listForPoint(grammarPointId, limit).map { it.toDomain() }
}

