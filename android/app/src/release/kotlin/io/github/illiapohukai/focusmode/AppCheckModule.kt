package io.github.illiapohukai.focusmode

import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.account.AppCheckProviderSource

/**
 * Release builds: Firebase App Check with Play Integrity. Tokens are attached to Firebase calls;
 * enforcement stays a Firebase Console decision and remains off until the metrics show that
 * genuine installs pass (android/docs/firebase-app-check-v21.md). Used only when Firebase is configured.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppCheckModule {
    @Provides
    fun appCheckProvider(): AppCheckProviderSource = AppCheckProviderSource { PlayIntegrityAppCheckProviderFactory.getInstance() }
}
