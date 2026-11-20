package io.github.illiapohukai.focusmode.feature.reminders

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.Schedule
import io.github.illiapohukai.focusmode.core.model.ScheduleId
import io.github.illiapohukai.focusmode.core.model.ScheduleZone
import io.github.illiapohukai.focusmode.core.notifications.NotificationCapabilityReader
import io.github.illiapohukai.focusmode.core.protection.BatteryOptimizationReader
import io.github.illiapohukai.focusmode.core.protection.ProtectionCapabilityOwner
import io.github.illiapohukai.focusmode.core.testing.FakeAppCatalog
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakeExactAlarms
import io.github.illiapohukai.focusmode.core.testing.FakeManagedDevice
import io.github.illiapohukai.focusmode.core.testing.FakePlatform
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.FakeScheduleRepository
import io.github.illiapohukai.focusmode.core.testing.FakeSelectedAppRepository
import io.github.illiapohukai.focusmode.core.testing.FakeUsageAccess
import io.github.illiapohukai.focusmode.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class SchedulesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = FakeClock(wall = Instant.parse("2026-11-19T08:00:00Z"))
    private val schedules = FakeScheduleRepository()

    private fun owner(scope: kotlinx.coroutines.CoroutineScope, exact: Boolean = false) = ProtectionCapabilityOwner(
        FakeUsageAccess(), FakePlatform(), FakeManagedDevice(), FakeExactAlarms(exact), BatteryOptimizationReader(context),
        NotificationCapabilityReader(context), FakeAppCatalog(), FakePreferencesRepository(), FakeSelectedAppRepository(), scope,
    )

    @Test fun deliveryStatementIsHonestWhenNotificationsAreNotAllowed() = runTest {
        schedules.upsert(Schedule(ScheduleId("schedule_a"), "Mathe", setOf(DayOfWeek.THURSDAY), LocalTime.of(16, 0), LocalTime.of(16, 45),
            ScheduleZone.FollowDevice, ProtectionMode.STANDARD, emptySet(), 10, null, true, clock.now(), clock.now()))
        val vm = SchedulesViewModel(schedules, owner(backgroundScope), clock)
        vm.uiState.launchIn(backgroundScope)
        val s = vm.uiState.first { !it.loading && it.rows.isNotEmpty() && it.delivery.isNotEmpty() }
        assertFalse(s.deliverable) // POST_NOTIFICATIONS not granted
        assertTrue(s.delivery.contains("werden nicht angezeigt"))
        assertEquals("Do., 19. Nov. · 15:50", s.rows.single().next)
    }

    @Test fun editorValidatesBeforeSaving() = runTest {
        val vm = ScheduleEditorViewModel(SavedStateHandle(), schedules, owner(backgroundScope), clock)
        vm.update { it.copy(label = "") }
        vm.save()
        assertEquals("Bitte gib einen Namen ein.", vm.state.first { it.error != null }.error)
        vm.update { it.copy(label = "Lineare Funktionen", weekdays = emptySet()) }
        vm.save()
        assertEquals("Wähle mindestens einen Wochentag.", vm.state.first { it.error != null }.error)
        vm.update { it.copy(weekdays = setOf(DayOfWeek.MONDAY)) }
        vm.save()
        assertEquals("Lineare Funktionen", schedules.schedules.first { it.isNotEmpty() }.single().label)
    }
}
