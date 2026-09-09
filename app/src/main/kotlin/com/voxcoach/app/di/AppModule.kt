package com.voxcoach.app.di

import com.voxcoach.core.domain.llm.LlmClient
import com.voxcoach.core.domain.model.LlmEndpointConfig
import com.voxcoach.core.domain.settings.LlmSettingsRepository
import com.voxcoach.core.llm.client.OpenAiCompatibleLlmClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OpenAiCompatibleLlmClient.defaultClient()

    @Provides
    @Singleton
    fun provideLlmClient(
        settings: LlmSettingsRepository,
        okHttpClient: OkHttpClient,
    ): LlmClient {
        val provider: () -> LlmEndpointConfig = {
            runBlocking { settings.config.first() }
        }
        return OpenAiCompatibleLlmClient(
            configProvider = provider,
            httpClient = okHttpClient,
        )
    }
}
