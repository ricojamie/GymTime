package com.example.gymtime.ai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import com.example.gymtime.smartlog.SmartLogPromptExtractor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindOnDeviceAiClient(implementation: MlKitOnDeviceAiClient): OnDeviceAiClient

    @Binds
    @IntoSet
    abstract fun bindMlKitSmartLogPromptExtractor(
        implementation: MlKitSmartLogPromptExtractor
    ): SmartLogPromptExtractor
}
