package com.voxcoach.core.data.di

import com.voxcoach.core.data.settings.DataStoreLlmSettingsRepository
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds @Singleton
    abstract fun bindSettings(impl: DataStoreLlmSettingsRepository): LlmSettingsRepository
}
