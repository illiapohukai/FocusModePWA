package io.github.illiapohukai.focusmode

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.account.FirebaseBuildConfig
import io.github.illiapohukai.focusmode.core.data.prefs.FeatureFlagDefaults
import io.github.illiapohukai.focusmode.core.domain.di.AppVersionName
import io.github.illiapohukai.focusmode.core.domain.di.WorkerBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @WorkerBaseUrl
    fun workerBaseUrl(): String = BuildConfig.WORKER_BASE_URL

    @Provides @AppVersionName
    fun appVersionName(): String = BuildConfig.VERSION_NAME

    /** Development-only features default to off in release builds. */
    @Provides
    fun featureFlagDefaults(): FeatureFlagDefaults = FeatureFlagDefaults(pwaImportEnabled = BuildConfig.DEBUG)

    /** v21: true only when the owner's google-services.json was present at build time. */
    @Provides
    fun firebaseBuildConfig(): FirebaseBuildConfig =
        FirebaseBuildConfig(configured = BuildConfig.FIREBASE_CONFIGURED, serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID.ifBlank { null })
}
