package com.voxcoach.core.data.db

import com.voxcoach.core.data.db.entity.DrillAttemptEntity
import com.voxcoach.core.data.db.entity.EvResultEntity
import com.voxcoach.core.data.db.entity.GrammarPointEntity
import com.voxcoach.core.data.db.entity.FeedbackItemEntity
import com.voxcoach.core.data.db.entity.MistakeEntity
import com.voxcoach.core.data.db.entity.SessionEntity
import com.voxcoach.core.data.db.entity.TopicEntity
import com.voxcoach.core.data.db.entity.TurnEntity
import com.voxcoach.core.data.db.entity.UserProfileEntity
import com.voxcoach.core.domain.model.BandDims
import com.voxcoach.core.domain.model.DimScore
import com.voxcoach.core.domain.model.DrillAttempt
import com.voxcoach.core.domain.model.EvResult
import com.voxcoach.core.domain.model.GrammarPoint
import com.voxcoach.core.domain.model.FeedbackItem
import com.voxcoach.core.domain.model.Highlight
import com.voxcoach.core.domain.model.Mistake
import com.voxcoach.core.domain.model.MistakeStatus
import com.voxcoach.core.domain.model.Session
import com.voxcoach.core.domain.model.SessionStatus
import com.voxcoach.core.domain.model.SessionSubtype
import com.voxcoach.core.domain.model.SessionType
import com.voxcoach.core.domain.model.TextSource
import com.voxcoach.core.domain.model.Topic
import com.voxcoach.core.domain.model.Turn
import com.voxcoach.core.domain.model.TurnRole
import com.voxcoach.core.domain.model.UserProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun TopicEntity.toDomain() = Topic(
    id = id,
    code = code,
    title = title,
    titleZh = titleZh,
    group = groupName,
)

fun Session.toEntity() = SessionEntity(
    id = id,
    type = type.name,
    subtype = subtype.name,
    topicId = topicId,
    stage = stage,
    startedAt = startedAt,
    endedAt = endedAt,
    durationMs = durationMs,
    turnCount = turnCount,
    audioPath = audioPath,
    evId = evId,
    status = status.name,
    dirty = dirty,
    updatedAt = updatedAt,
    deleted = deleted,
)

fun SessionEntity.toDomain() = Session(
    id = id,
    type = SessionType.valueOf(type),
    subtype = SessionSubtype.valueOf(subtype),
    topicId = topicId,
    stage = stage,
    startedAt = startedAt,
    endedAt = endedAt,
    durationMs = durationMs,
    turnCount = turnCount,
    audioPath = audioPath,
    evId = evId,
    status = SessionStatus.valueOf(status),
    dirty = dirty,
    updatedAt = updatedAt,
    deleted = deleted,
)

fun Turn.toEntity() = TurnEntity(
    id = id,
    sessionId = sessionId,
    role = role.name,
    text = text,
    textSource = textSource.name,
    seq = seq,
    partialMs = partialMs,
    startMs = startMs,
    endMs = endMs,
    llmMetaJson = llmMetaJson,
)

fun TurnEntity.toDomain() = Turn(
    id = id,
    sessionId = sessionId,
    role = TurnRole.valueOf(role),
    text = text,
    textSource = TextSource.valueOf(textSource),
    seq = seq,
    partialMs = partialMs,
    startMs = startMs,
    endMs = endMs,
    llmMetaJson = llmMetaJson,
)

@Serializable
private data class DimScoreDto(val score: Double, val comment: String = "", val evidence: List<String> = emptyList())

@Serializable
private data class BandDimsDto(val fc: DimScoreDto, val lr: DimScoreDto, val gra: DimScoreDto, val p: DimScoreDto)

@Serializable
private data class HighlightDto(val dim: String, val quote: String, val note: String = "")

fun BandDims.toJson(): String = json.encodeToString(
    BandDimsDto(
        fc = DimScoreDto(fc.score, fc.comment, fc.evidence),
        lr = DimScoreDto(lr.score, lr.comment, lr.evidence),
        gra = DimScoreDto(gra.score, gra.comment, gra.evidence),
        p = DimScoreDto(p.score, p.comment, p.evidence),
    ),
)

fun String.toBandDims(): BandDims {
    val dto = json.decodeFromString<BandDimsDto>(this)
    fun DimScoreDto.toDomain() = DimScore(score, comment, evidence)
    return BandDims(fc = dto.fc.toDomain(), lr = dto.lr.toDomain(), gra = dto.gra.toDomain(), p = dto.p.toDomain())
}

fun List<Highlight>.toJson(): String =
    json.encodeToString(map { HighlightDto(it.dim, it.quote, it.note) })

fun String.toHighlights(): List<Highlight> =
    json.decodeFromString<List<HighlightDto>>(this).map { Highlight(it.dim, it.quote, it.note) }

fun EvResult.toEntity() = EvResultEntity(
    id = id,
    sessionId = sessionId,
    overallBand = overallBand,
    dimsJson = dims.toJson(),
    itemsJson = "[]", // items stored in feedback_items table
    highlightsJson = highlights.toJson(),
    engineVer = engineVer,
    promptVer = promptVer,
    createdAt = createdAt,
)

fun EvResultEntity.toDomain(items: List<FeedbackItem>) = EvResult(
    id = id,
    sessionId = sessionId,
    overallBand = overallBand,
    dims = dimsJson.toBandDims(),
    items = items,
    highlights = highlightsJson.toHighlights(),
    engineVer = engineVer,
    promptVer = promptVer,
    createdAt = createdAt,
)

fun FeedbackItem.toEntity() = FeedbackItemEntity(
    id = id,
    evId = evId,
    dimension = dimension,
    category = category,
    quote = quote,
    correction = correction,
    why = why,
    modelJson = json.encodeToString(model),
    tRangeJson = tRange?.let { json.encodeToString(it) },
    collectedToMistakeAt = collectedToMistakeAt,
)

fun FeedbackItemEntity.toDomain() = FeedbackItem(
    id = id,
    evId = evId,
    dimension = dimension,
    category = category,
    quote = quote,
    correction = correction,
    why = why,
    model = json.decodeFromString(modelJson),
    tRange = tRangeJson?.let { json.decodeFromString(it) },
    collectedToMistakeAt = collectedToMistakeAt,
)

fun Mistake.toEntity() = MistakeEntity(
    id = id,
    sourceItemId = sourceItemId,
    sessionId = sessionId,
    dimension = dimension,
    quote = quote,
    correction = correction,
    grammarPointId = grammarPointId,
    status = status.name,
    retriedCount = retriedCount,
    lastRetriedAt = lastRetriedAt,
    createdAt = createdAt,
)

fun MistakeEntity.toDomain() = Mistake(
    id = id,
    sourceItemId = sourceItemId,
    sessionId = sessionId,
    dimension = dimension,
    quote = quote,
    correction = correction,
    grammarPointId = grammarPointId,
    status = MistakeStatus.valueOf(status),
    retriedCount = retriedCount,
    lastRetriedAt = lastRetriedAt,
    createdAt = createdAt,
)

fun UserProfileEntity.toDomain() = UserProfile(
    version = version,
    stage = stage,
    targetBand = targetBand,
    dimTrendCacheJson = dimTrendCacheJson,
    streak = streak,
    totalDurationMs = totalDurationMs,
    totalTurnCount = totalTurnCount,
    totalSessionCount = totalSessionCount,
    updatedAt = updatedAt,
)

fun UserProfile.toEntity() = UserProfileEntity(
    id = 1,
    version = version,
    stage = stage,
    targetBand = targetBand,
    dimTrendCacheJson = dimTrendCacheJson,
    streak = streak,
    totalDurationMs = totalDurationMs,
    totalTurnCount = totalTurnCount,
    totalSessionCount = totalSessionCount,
    updatedAt = updatedAt,
)

fun GrammarPointEntity.toDomain() = GrammarPoint(
    id = id,
    code = code,
    groupCode = groupCode,
    groupTitle = groupTitle,
    title = title,
    titleZh = titleZh,
    rule = rule,
    examplesJson = examplesJson,
    skeleton = skeleton,
    topicHint = topicHint,
    sortOrder = sortOrder,
)

fun DrillAttempt.toEntity() = DrillAttemptEntity(
    id = id,
    grammarPointId = grammarPointId,
    promptId = promptId,
    userSentence = userSentence,
    hit = hit,
    feedbackJson = feedbackJson,
    triedAt = triedAt,
)

fun DrillAttemptEntity.toDomain() = DrillAttempt(
    id = id,
    grammarPointId = grammarPointId,
    promptId = promptId,
    userSentence = userSentence,
    hit = hit,
    feedbackJson = feedbackJson,
    triedAt = triedAt,
)

