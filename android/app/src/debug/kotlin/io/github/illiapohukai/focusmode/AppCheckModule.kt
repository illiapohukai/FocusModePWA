package io.github.illiapohukai.focusmode

import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.account.AppCheckProviderSource

/**
 * Debug builds: the Firebase App Check debug provider. On first use it prints a per-install debug
 * secret to logcat, which the owner registers in the Firebase Console (App Check > Apps > Manage
 * debug tokens). No debug token is stored in the repository or the APK. Used only when Firebase
 * is configured.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppCheckModule {
    @Provides
    fun appCheckProvider(): AppCheckProviderSource = AppCheckProviderSource { DebugAppCheckProviderFactory.getInstance() }
}
