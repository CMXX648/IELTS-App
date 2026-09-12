package com.voxcoach.core.speech.di

import com.voxcoach.core.domain.speech.AsrEngine
import com.voxcoach.core.domain.speech.SessionAudioCapture
import com.voxcoach.core.domain.speech.TtsEngine
import com.voxcoach.core.speech.asr.MimoAsrEngine
import com.voxcoach.core.speech.recording.MediaRecorderSessionAudioCapture
import com.voxcoach.core.speech.tts.MimoTtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechModule {
    @Binds @Singleton
    abstract fun bindAsr(engine: MimoAsrEngine): AsrEngine

    @Binds @Singleton
    abstract fun bindTts(engine: MimoTtsEngine): TtsEngine

    @Binds @Singleton
    abstract fun bindSessionAudio(capture: MediaRecorderSessionAudioCapture): SessionAudioCapture
}
