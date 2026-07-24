package com.example.gymtime.smartlog

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object SmartLogModule {
    @Provides
    @IntoSet
    fun provideDeterministicSmartLogExtractor(
        extractor: DeterministicSmartLogPromptExtractor
    ): SmartLogPromptExtractor = extractor
}
