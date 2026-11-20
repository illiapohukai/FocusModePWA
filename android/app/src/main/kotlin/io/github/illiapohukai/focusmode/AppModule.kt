package io.github.illiapohukai.focusmode

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.data.prefs.FeatureFlagDefaults
import io.github.illiapohukai.focusmode.core.domain.di.WorkerBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @WorkerBaseUrl
    fun workerBaseUrl(): String = BuildConfig.WORKER_BASE_URL

    /** Development-only features default to off in release builds. */
    @Provides
    fun featureFlagDefaults(): FeatureFlagDefaults = FeatureFlagDefaults(pwaImportEnabled = BuildConfig.DEBUG)
}
