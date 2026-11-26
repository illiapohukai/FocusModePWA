package io.github.illiapohukai.focusmode.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.domain.ai.MissionAdaptationRepository
import io.github.illiapohukai.focusmode.core.domain.ai.MissionGenerationRepository
import io.github.illiapohukai.focusmode.core.domain.ai.MissionMaterialStore
import io.github.illiapohukai.focusmode.core.domain.di.WorkerBaseUrl
import io.github.illiapohukai.focusmode.core.network.WorkerApi
import io.github.illiapohukai.focusmode.core.network.WorkerClientFactory
import io.github.illiapohukai.focusmode.core.network.WorkerMissionAdaptationRepository
import io.github.illiapohukai.focusmode.core.network.attachment.AndroidMissionMaterialStore
import io.github.illiapohukai.focusmode.core.network.WorkerMissionGenerationRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun workerApi(@WorkerBaseUrl baseUrl: String): WorkerApi = WorkerClientFactory.create(baseUrl, requireHttps = true)

    @Provides @Singleton
    fun missionGeneration(api: WorkerApi): MissionGenerationRepository = WorkerMissionGenerationRepository(api)

    @Provides @Singleton
    fun missionAdaptation(api: WorkerApi): MissionAdaptationRepository = WorkerMissionAdaptationRepository(api)

    @Provides @Singleton
    fun missionMaterial(store: AndroidMissionMaterialStore): MissionMaterialStore = store
}
