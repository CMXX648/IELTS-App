package com.voxcoach.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.voxcoach.core.data.db.dao.DrillAttemptDao
import com.voxcoach.core.data.db.dao.EvDao
import com.voxcoach.core.data.db.dao.GrammarPointDao
import com.voxcoach.core.data.db.dao.MistakeDao
import com.voxcoach.core.data.db.dao.ProfileDao
import com.voxcoach.core.data.db.dao.SessionDao
import com.voxcoach.core.data.db.dao.TopicDao
import com.voxcoach.core.data.db.dao.TurnDao
import com.voxcoach.core.data.db.entity.DrillAttemptEntity
import com.voxcoach.core.data.db.entity.EvResultEntity
import com.voxcoach.core.data.db.entity.GrammarPointEntity
import com.voxcoach.core.data.db.entity.FeedbackItemEntity
import com.voxcoach.core.data.db.entity.MistakeEntity
import com.voxcoach.core.data.db.entity.SessionEntity
import com.voxcoach.core.data.db.entity.TopicEntity
import com.voxcoach.core.data.db.entity.TurnEntity
import com.voxcoach.core.data.db.entity.UserProfileEntity

@Database(
    entities = [
        TopicEntity::class,
        SessionEntity::class,
        TurnEntity::class,
        EvResultEntity::class,
        FeedbackItemEntity::class,
        MistakeEntity::class,
        UserProfileEntity::class,
        GrammarPointEntity::class,
        DrillAttemptEntity::class,
    ],
    version = VoxDatabase.SCHEMA_VERSION,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class VoxDatabase : RoomDatabase() {
    abstract fun topicDao(): TopicDao
    abstract fun sessionDao(): SessionDao
    abstract fun turnDao(): TurnDao
    abstract fun evDao(): EvDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun profileDao(): ProfileDao
    abstract fun grammarPointDao(): GrammarPointDao
    abstract fun drillAttemptDao(): DrillAttemptDao

    companion object {
        const val SCHEMA_VERSION = 2
        const val NAME = "voxcoach.db"

        fun build(context: Context): VoxDatabase =
            Room.databaseBuilder(context, VoxDatabase::class.java, NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed runs via SeedRunner after open (see DataModule).
                    }
                })
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        fun buildInMemory(context: Context): VoxDatabase =
            Room.inMemoryDatabaseBuilder(context, VoxDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}

object TopicSeeds {
    val hometown = TopicEntity(
        id = "T-hometown",
        code = "hometown",
        title = "Hometown",
        titleZh = "家乡",
        groupName = "part1",
    )
    val dailyRoutine = TopicEntity(
        id = "T-daily_routine",
        code = "daily_routine",
        title = "Daily routine",
        titleZh = "日常生活",
        groupName = "part1",
    )
    val friends = TopicEntity(
        id = "T-friends",
        code = "friends",
        title = "Friends",
        titleZh = "朋友",
        groupName = "part1",
    )

    val p2Person = TopicEntity(
        id = "T-p2-person",
        code = "p2_person",
        title = "Part 2 · Person",
        titleZh = "Part 2 · 人物",
        groupName = "part2",
    )
    val p2Place = TopicEntity(
        id = "T-p2-place",
        code = "p2_place",
        title = "Part 2 · Place",
        titleZh = "Part 2 · 地点",
        groupName = "part2",
    )
    val p2Object = TopicEntity(
        id = "T-p2-object",
        code = "p2_object",
        title = "Part 2 · Object",
        titleZh = "Part 2 · 物品",
        groupName = "part2",
    )
    val p2Experience = TopicEntity(
        id = "T-p2-experience",
        code = "p2_experience",
        title = "Part 2 · Experience",
        titleZh = "Part 2 · 经历",
        groupName = "part2",
    )
    val p2Opinion = TopicEntity(
        id = "T-p2-opinion",
        code = "p2_opinion",
        title = "Part 2 · Opinion",
        titleZh = "Part 2 · 观点",
        groupName = "part2",
    )


    val p3Person = TopicEntity(
        id = "T-p3-person",
        code = "p3_person",
        title = "Part 3 · Person",
        titleZh = "Part 3 · 人物讨论",
        groupName = "part3",
    )
    val p3Place = TopicEntity(
        id = "T-p3-place",
        code = "p3_place",
        title = "Part 3 · Place",
        titleZh = "Part 3 · 地点讨论",
        groupName = "part3",
    )
    val p3Object = TopicEntity(
        id = "T-p3-object",
        code = "p3_object",
        title = "Part 3 · Object",
        titleZh = "Part 3 · 物品讨论",
        groupName = "part3",
    )
    val p3Experience = TopicEntity(
        id = "T-p3-experience",
        code = "p3_experience",
        title = "Part 3 · Experience",
        titleZh = "Part 3 · 经历讨论",
        groupName = "part3",
    )
    val p3Opinion = TopicEntity(
        id = "T-p3-opinion",
        code = "p3_opinion",
        title = "Part 3 · Opinion",
        titleZh = "Part 3 · 观点讨论",
        groupName = "part3",
    )

    fun all(): List<TopicEntity> = listOf(
        hometown, dailyRoutine, friends,
        p2Person, p2Place, p2Object, p2Experience, p2Opinion,
        p3Person, p3Place, p3Object, p3Experience, p3Opinion,
    )
}

class SeedRunner(
    private val topicDao: TopicDao,
    private val profileDao: ProfileDao,
    private val grammarPointDao: GrammarPointDao,
) {
    suspend fun ensureSeeded() {
        // IGNORE conflict: backfill Part1/Part2/Part3 themes on existing installs
        topicDao.insertAll(TopicSeeds.all())
        if (grammarPointDao.count() == 0) {
            grammarPointDao.insertAll(GrammarSeeds.all())
        }
        if (profileDao.get() == null) {
            profileDao.upsert(
                UserProfileEntity(
                    id = 1,
                    version = 1,
                    stage = "S0",
                    targetBand = 7.5,
                    dimTrendCacheJson = null,
                    streak = 0,
                    totalDurationMs = 0,
                    totalTurnCount = 0,
                    totalSessionCount = 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
