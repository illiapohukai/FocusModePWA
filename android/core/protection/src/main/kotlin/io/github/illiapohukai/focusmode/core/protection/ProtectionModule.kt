package io.github.illiapohukai.focusmode.core.protection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.domain.platform.DoNotDisturbController
import io.github.illiapohukai.focusmode.core.domain.platform.ExactAlarmCapability
import io.github.illiapohukai.focusmode.core.domain.platform.InstalledAppCatalog
import io.github.illiapohukai.focusmode.core.domain.platform.ManagedDeviceGateway
import io.github.illiapohukai.focusmode.core.domain.platform.ManagedFocusEnforcer
import io.github.illiapohukai.focusmode.core.domain.platform.SystemSettingsNavigator
import io.github.illiapohukai.focusmode.core.domain.platform.UsageAccessGateway

@Module
@InstallIn(SingletonComponent::class)
abstract class ProtectionModule {
    @Binds abstract fun usage(impl: AndroidUsageAccessGateway): UsageAccessGateway
    @Binds abstract fun dnd(impl: AndroidDndController): DoNotDisturbController
    @Binds abstract fun managed(impl: ConsumerManagedDeviceGateway): ManagedDeviceGateway
    @Binds abstract fun enforcer(impl: NoOpManagedFocusEnforcer): ManagedFocusEnforcer
    @Binds abstract fun exactAlarms(impl: AndroidExactAlarmCapability): ExactAlarmCapability
    @Binds abstract fun catalog(impl: AndroidInstalledAppCatalog): InstalledAppCatalog
    @Binds abstract fun settings(impl: AndroidSystemSettingsNavigator): SystemSettingsNavigator
    @Binds abstract fun screen(impl: AndroidScreenState): ScreenState
}
