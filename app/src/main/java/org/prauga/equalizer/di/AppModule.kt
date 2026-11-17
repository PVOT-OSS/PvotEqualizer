package org.prauga.equalizer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.prauga.equalizer.audio.AudioEngine
import org.prauga.equalizer.audio.DynamicsProcessingEngine

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAudioEngine(): AudioEngine = DynamicsProcessingEngine()
}
