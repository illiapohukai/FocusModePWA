package io.github.illiapohukai.focusmode.core.testing

import io.github.illiapohukai.focusmode.core.domain.platform.ExactAlarmCapability
import io.github.illiapohukai.focusmode.core.domain.platform.InstalledAppCatalog
import io.github.illiapohukai.focusmode.core.domain.platform.ManagedDeviceGateway
import io.github.illiapohukai.focusmode.core.domain.platform.UsageAccessGateway
import io.github.illiapohukai.focusmode.core.model.InstalledApp
import io.github.illiapohukai.focusmode.core.model.ManagedDeviceStatus

class FakeUsageAccess(var supported: Boolean = true, var access: Boolean = false) : UsageAccessGateway {
    override fun isSupported() = supported
    override fun hasAccess() = access
    override fun foregroundEvents(fromMs: Long, toMs: Long, watched: Set<String>) = emptyList<Pair<String, Long>>()
}

class FakeManagedDevice(var status: ManagedDeviceStatus = ManagedDeviceStatus.NOT_MANAGED) : ManagedDeviceGateway {
    override fun status() = status
}

class FakeExactAlarms(var allowed: Boolean = false) : ExactAlarmCapability {
    override fun canScheduleExactAlarms() = allowed
}

class FakeAppCatalog(var apps: List<InstalledApp> = emptyList()) : InstalledAppCatalog {
    override suspend fun launchableApps() = apps
    override fun resolve(packageName: String) = apps.firstOrNull { it.packageName == packageName }
}
