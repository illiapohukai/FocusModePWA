package app.focusmodeplus.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.focusmodeplus.core.domain.ai.MissionAdaptationRepository
import app.focusmodeplus.core.domain.ai.MissionGenerationRepository
import app.focusmodeplus.core.domain.ai.MissionMaterialStore
import app.focusmodeplus.core.domain.di.WorkerBaseUrl
import app.focusmodeplus.core.network.WorkerApi
import app.focusmodeplus.core.network.WorkerClientFactory
import app.focusmodeplus.core.network.WorkerMissionAdaptationRepository
import app.focusmodeplus.core.network.attachment.AndroidMissionMaterialStore
import app.focusmodeplus.core.network.WorkerMissionGenerationRepository
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
