package app.focusmodeplus.core.protection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.focusmodeplus.core.domain.platform.DoNotDisturbController
import app.focusmodeplus.core.domain.platform.ExactAlarmCapability
import app.focusmodeplus.core.domain.platform.InstalledAppCatalog
import app.focusmodeplus.core.domain.platform.ManagedDeviceGateway
import app.focusmodeplus.core.domain.platform.ManagedFocusEnforcer
import app.focusmodeplus.core.domain.platform.SystemSettingsNavigator
import app.focusmodeplus.core.domain.platform.UsageAccessGateway

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
