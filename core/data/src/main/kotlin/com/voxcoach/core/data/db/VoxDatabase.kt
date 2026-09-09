package com.voxcoach.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.voxcoach.core.data.db.dao.EvDao
import com.voxcoach.core.data.db.dao.MistakeDao
import com.voxcoach.core.data.db.dao.ProfileDao
import com.voxcoach.core.data.db.dao.SessionDao
import com.voxcoach.core.data.db.dao.TopicDao
import com.voxcoach.core.data.db.dao.TurnDao
import com.voxcoach.core.data.db.entity.EvResultEntity
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

    companion object {
        const val SCHEMA_VERSION = 1
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

    fun all(): List<TopicEntity> = listOf(hometown)
}

class SeedRunner(
    private val topicDao: TopicDao,
    private val profileDao: ProfileDao,
) {
    suspend fun ensureSeeded() {
        if (topicDao.count() == 0) {
            topicDao.insertAll(TopicSeeds.all())
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
