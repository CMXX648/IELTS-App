package com.voxcoach.core.data.di

import android.content.Context
import com.voxcoach.core.data.db.SeedRunner
import com.voxcoach.core.data.db.VoxDatabase
import com.voxcoach.core.data.db.dao.DrillAttemptDao
import com.voxcoach.core.data.db.dao.EvDao
import com.voxcoach.core.data.db.dao.GrammarPointDao
import com.voxcoach.core.data.db.dao.MistakeDao
import com.voxcoach.core.data.db.dao.ProfileDao
import com.voxcoach.core.data.db.dao.SessionDao
import com.voxcoach.core.data.db.dao.TopicDao
import com.voxcoach.core.data.db.dao.TurnDao
import com.voxcoach.core.data.repo.DrillAttemptRepositoryImpl
import com.voxcoach.core.data.repo.EvRepositoryImpl
import com.voxcoach.core.data.repo.GrammarPointRepositoryImpl
import com.voxcoach.core.data.repo.MistakeRepositoryImpl
import com.voxcoach.core.data.repo.ProfileRepositoryImpl
import com.voxcoach.core.data.repo.SessionRepositoryImpl
import com.voxcoach.core.data.repo.TopicRepositoryImpl
import com.voxcoach.core.data.repo.TurnRepositoryImpl
import com.voxcoach.core.data.settings.DataStoreLlmSettingsRepository
import com.voxcoach.core.data.settings.DataStoreOnboardingRepository
import com.voxcoach.core.domain.repository.DrillAttemptRepository
import com.voxcoach.core.domain.repository.EvRepository
import com.voxcoach.core.domain.repository.GrammarPointRepository
import com.voxcoach.core.domain.repository.MistakeRepository
import com.voxcoach.core.domain.repository.ProfileRepository
import com.voxcoach.core.domain.repository.SessionRepository
import com.voxcoach.core.domain.repository.TopicRepository
import com.voxcoach.core.domain.repository.TurnRepository
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import com.voxcoach.core.domain.settings.OnboardingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindModule {
    @Binds @Singleton
    abstract fun bindSettings(impl: DataStoreLlmSettingsRepository): LlmSettingsRepository

    @Binds @Singleton
    abstract fun bindOnboarding(impl: DataStoreOnboardingRepository): OnboardingRepository

    @Binds @Singleton
    abstract fun bindTopic(impl: TopicRepositoryImpl): TopicRepository

    @Binds @Singleton
    abstract fun bindSession(impl: SessionRepositoryImpl): SessionRepository

    @Binds @Singleton
    abstract fun bindTurn(impl: TurnRepositoryImpl): TurnRepository

    @Binds @Singleton
    abstract fun bindEv(impl: EvRepositoryImpl): EvRepository

    @Binds @Singleton
    abstract fun bindMistake(impl: MistakeRepositoryImpl): MistakeRepository

    @Binds @Singleton
    abstract fun bindProfile(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds @Singleton
    abstract fun bindGrammarPoint(impl: GrammarPointRepositoryImpl): GrammarPointRepository

    @Binds @Singleton
    abstract fun bindDrillAttempt(impl: DrillAttemptRepositoryImpl): DrillAttemptRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvideModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VoxDatabase =
        VoxDatabase.build(context)

    @Provides fun provideTopicDao(db: VoxDatabase): TopicDao = db.topicDao()
    @Provides fun provideSessionDao(db: VoxDatabase): SessionDao = db.sessionDao()
    @Provides fun provideTurnDao(db: VoxDatabase): TurnDao = db.turnDao()
    @Provides fun provideEvDao(db: VoxDatabase): EvDao = db.evDao()
    @Provides fun provideMistakeDao(db: VoxDatabase): MistakeDao = db.mistakeDao()
    @Provides fun provideProfileDao(db: VoxDatabase): ProfileDao = db.profileDao()
    @Provides fun provideGrammarPointDao(db: VoxDatabase): GrammarPointDao = db.grammarPointDao()
    @Provides fun provideDrillAttemptDao(db: VoxDatabase): DrillAttemptDao = db.drillAttemptDao()

    @Provides
    @Singleton
    fun provideSeedRunner(
        topicDao: TopicDao,
        profileDao: ProfileDao,
        grammarPointDao: GrammarPointDao,
        db: VoxDatabase,
    ): SeedRunner {
        val runner = SeedRunner(topicDao, profileDao, grammarPointDao)
        // Eager seed on first open
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            db.openHelper.writableDatabase
            runner.ensureSeeded()
        }
        return runner
    }
}
