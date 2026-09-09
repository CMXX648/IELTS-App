package com.voxcoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey val id: String,
    val code: String,
    val title: String,
    val titleZh: String,
    val groupName: String,
)

@Entity(
    tableName = "sessions",
    indices = [Index("topicId"), Index("startedAt")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val subtype: String,
    val topicId: String?,
    val stage: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val durationMs: Long,
    val turnCount: Int,
    val audioPath: String?,
    val evId: String?,
    val status: String,
    val dirty: Boolean,
    val updatedAt: Long,
    val deleted: Boolean,
)

@Entity(
    tableName = "turns",
    indices = [Index("sessionId")],
)
data class TurnEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val text: String,
    val textSource: String,
    val seq: Int,
    val partialMs: Long?,
    val startMs: Long?,
    val endMs: Long?,
    val llmMetaJson: String?,
)

@Entity(
    tableName = "ev_results",
    indices = [Index("sessionId")],
)
data class EvResultEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val overallBand: Double,
    val dimsJson: String,
    val itemsJson: String,
    val highlightsJson: String,
    val engineVer: String,
    val promptVer: String,
    val createdAt: Long,
)

@Entity(
    tableName = "feedback_items",
    indices = [Index("evId")],
)
data class FeedbackItemEntity(
    @PrimaryKey val id: String,
    val evId: String,
    val dimension: String,
    val category: String,
    val quote: String,
    val correction: String,
    val why: String,
    val modelJson: String,
    val tRangeJson: String?,
    val collectedToMistakeAt: Long?,
)

@Entity(tableName = "mistakes")
data class MistakeEntity(
    @PrimaryKey val id: String,
    val sourceItemId: String?,
    val sessionId: String?,
    val dimension: String,
    val quote: String,
    val correction: String,
    val why: String = "",
    val grammarPointId: String?,
    val status: String,
    val retriedCount: Int,
    val lastRetriedAt: Long?,
    val createdAt: Long,
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val version: Int,
    val stage: String,
    val targetBand: Double,
    val dimTrendCacheJson: String?,
    val streak: Int,
    val totalDurationMs: Long,
    val totalTurnCount: Int,
    val totalSessionCount: Int,
    val updatedAt: Long,
)

@Entity(tableName = "grammar_points")
data class GrammarPointEntity(
    @PrimaryKey val id: String,
    val code: String,
    val groupCode: String,
    val groupTitle: String,
    val title: String,
    val titleZh: String,
    val rule: String,
    val examplesJson: String,
    val skeleton: String,
    val topicHint: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "drill_attempts",
    indices = [Index("grammarPointId"), Index("triedAt")],
)
data class DrillAttemptEntity(
    @PrimaryKey val id: String,
    val grammarPointId: String,
    val promptId: String,
    val userSentence: String,
    val hit: Boolean,
    val feedbackJson: String,
    val triedAt: Long,
)

