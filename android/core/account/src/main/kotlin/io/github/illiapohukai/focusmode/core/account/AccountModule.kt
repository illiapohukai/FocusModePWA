package io.github.illiapohukai.focusmode.core.account

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.domain.account.AccountRepository
import io.github.illiapohukai.focusmode.core.domain.account.LocalEraseAccountHook
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteSyncStore
import io.github.illiapohukai.focusmode.core.domain.sync.SyncEngine
import io.github.illiapohukai.focusmode.core.domain.sync.SyncRepository
import io.github.illiapohukai.focusmode.core.domain.sync.SyncScheduler
import javax.inject.Provider
import javax.inject.Singleton

/**
 * One configuration boundary: a configured build gets Firebase; an unconfigured build gets the
 * guest-only implementations and never instantiates a Firebase class (the providers below are
 * only called for a usable configuration).
 */
@Module
@InstallIn(SingletonComponent::class)
object AccountProvidesModule {
    @Provides @Singleton
    fun accountRepository(runtime: FirebaseRuntime, firebase: Provider<FirebaseAccountRepository>): AccountRepository =
        if (runtime.available) firebase.get() else UnconfiguredAccountRepository

    @Provides @Singleton
    fun remoteSyncStore(runtime: FirebaseRuntime, firestore: Provider<FirestoreRemoteSyncStore>): RemoteSyncStore =
        if (runtime.available) firestore.get() else RemoteSyncStore.Unavailable
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountBindsModule {
    @Binds abstract fun scheduler(impl: AndroidSyncScheduler): SyncScheduler
    @Binds abstract fun sync(impl: SyncEngine): SyncRepository
    @Binds abstract fun localEraseHook(impl: SyncEngine): LocalEraseAccountHook
}
