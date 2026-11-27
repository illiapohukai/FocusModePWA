package app.focusmodeplus.core.domain.protection

import app.focusmodeplus.core.model.ManagedDeviceStatus
import app.focusmodeplus.core.model.PermissionKind
import app.focusmodeplus.core.model.PermissionSnapshot
import app.focusmodeplus.core.model.ProtectionCapability
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.ProtectionPermissionState
import app.focusmodeplus.core.model.ProtectionPermissionState.DENIED
import app.focusmodeplus.core.model.ProtectionPermissionState.GRANTED
import app.focusmodeplus.core.model.ProtectionPermissionState.NOT_REQUESTED
import app.focusmodeplus.core.model.SettingsTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityResolverTest {
    private fun snapshot(
        notifications: ProtectionPermissionState = NOT_REQUESTED,
        usage: ProtectionPermissionState = DENIED,
        dnd: ProtectionPermissionState = DENIED,
        managed: ManagedDeviceStatus = ManagedDeviceStatus.NOT_MANAGED,
    ) = PermissionSnapshot(notifications, usage, dnd, ProtectionPermissionState.DENIED, false, managed)

    private fun resolve(s: PermissionSnapshot = snapshot(), apps: Int = 0, monitoring: Boolean = false, dnd: Boolean = false, revoked: Boolean = false) =
        CapabilityResolver.resolve(ProtectionInputs(s, apps, monitoring, dnd, revoked))

    @Test fun noPermissionsStillAllowsStandardAndProtectedButNeverStrict() {
        val o = resolve()
        assertEquals(ProtectionCapability.NOTIFICATION_PERMISSION_NEEDED, o.softProtection.capability)
        assertEquals(ProtectionCapability.USAGE_ACCESS_NEEDED, o.usageMonitoring.capability)
        assertEquals(ProtectionCapability.DO_NOT_DISTURB_ACCESS_NEEDED, o.doNotDisturb.capability)
        assertEquals(ProtectionCapability.MANAGED_DEVICE_REQUIRED, o.managed.capability)
        assertEquals(ProtectionCapability.ACCESSIBILITY_NOT_AVAILABLE, o.accessibility.capability)
        assertEquals(setOf(ProtectionMode.STANDARD, ProtectionMode.PROTECTED), o.availableModes)
        assertEquals(ProtectionMode.PROTECTED, CapabilityResolver.effectiveMode(ProtectionMode.STRICT_MANAGED, o))
    }

    @Test fun notificationPermissionEnablesSoftProtection() {
        assertEquals(ProtectionCapability.READY_FOR_SOFT_PROTECTION, resolve(snapshot(notifications = GRANTED)).softProtection.capability)
    }

    @Test fun usageMonitoringNeedsAccessSelectionAndOptIn() {
        assertEquals(ProtectionCapability.NOT_CONFIGURED, resolve(snapshot(usage = GRANTED), apps = 0, monitoring = true).usageMonitoring.capability)
        assertEquals(ProtectionCapability.NOT_CONFIGURED, resolve(snapshot(usage = GRANTED), apps = 3, monitoring = false).usageMonitoring.capability)
        val ready = resolve(snapshot(usage = GRANTED), apps = 3, monitoring = true).usageMonitoring
        assertEquals(ProtectionCapability.READY_FOR_USAGE_MONITORING, ready.capability)
        assertEquals(SettingsTarget.USAGE_ACCESS_SETTINGS, resolve(snapshot(), apps = 3, monitoring = true).usageMonitoring.settingsTarget)
    }

    @Test fun revocationDuringSessionIsTemporarilyUnavailable() {
        assertEquals(ProtectionCapability.TEMPORARILY_UNAVAILABLE, resolve(snapshot(usage = DENIED), apps = 2, monitoring = true, revoked = true).usageMonitoring.capability)
    }

    @Test fun unsupportedDevicesAreReportedAsUnsupported() {
        val o = resolve(snapshot(usage = ProtectionPermissionState.UNSUPPORTED, dnd = ProtectionPermissionState.UNSUPPORTED))
        assertEquals(ProtectionCapability.UNSUPPORTED, o.usageMonitoring.capability)
        assertEquals(ProtectionCapability.UNSUPPORTED, o.doNotDisturb.capability)
    }

    @Test fun dndAccessGrantedIsReadyOnlyWhenEnabled() {
        assertEquals(ProtectionCapability.NOT_CONFIGURED, resolve(snapshot(dnd = GRANTED), dnd = false).doNotDisturb.capability)
        assertEquals(ProtectionCapability.READY_FOR_SOFT_PROTECTION, resolve(snapshot(dnd = GRANTED), dnd = true).doNotDisturb.capability)
    }

    @Test fun managedDeviceCapabilityIsHonest() {
        assertEquals(ProtectionCapability.TEMPORARILY_UNAVAILABLE, resolve(snapshot(managed = ManagedDeviceStatus.OWNER_DETECTED_NOT_SUPPORTED)).managed.capability)
        val fakeManaged = resolve(snapshot(managed = ManagedDeviceStatus.MANAGED_ENFORCEMENT_READY))
        assertEquals(ProtectionCapability.READY_FOR_MANAGED_BLOCKING, fakeManaged.managed.capability)
        assertTrue(ProtectionMode.STRICT_MANAGED in fakeManaged.availableModes)
    }

    @Test fun userFacingTextNeverClaimsBlockingOnConsumerDevices() {
        val forbidden = listOf("werden blockiert", "100 %", "Unumgehbar", "Strikte Sperre aktiv", "ablenkungsfrei")
        val o = resolve(snapshot(GRANTED, GRANTED, GRANTED), apps = 3, monitoring = true, dnd = true)
        val texts = listOf(o.softProtection, o.usageMonitoring, o.doNotDisturb, o.managed, o.accessibility)
            .flatMap { listOf(it.title, it.explanation, it.limitation, it.nextAction.orEmpty()) } +
            o.permissionRows.flatMap { listOf(it.title, it.purpose, it.limitation, it.stateLabel) } +
            ProtectionMode.entries.map { o.summaryFor(it, 3) }
        texts.forEach { t -> forbidden.forEach { f -> assertFalse("'$t' contains '$f'", t.contains(f, ignoreCase = true)) } }
        assertEquals("Strikte Sperre auf diesem Gerät nicht verfügbar", o.summaryFor(ProtectionMode.STRICT_MANAGED, 3))
    }

    @Test fun permissionCenterListsEveryPermissionWithDataStatement() {
        val rows = resolve().permissionRows
        assertEquals(PermissionKind.entries.toSet(), rows.map { it.kind }.toSet())
        rows.forEach { assertTrue(it.purpose.isNotBlank() && it.dataAccessed.isNotBlank() && it.leavesDevice.isNotBlank()) }
        assertTrue(rows.single { it.kind == PermissionKind.NOTIFICATIONS }.requestInApp)
        assertFalse(rows.single { it.kind == PermissionKind.USAGE_ACCESS }.requestInApp)
    }
}
