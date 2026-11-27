package app.focusmodeplus.feature.reminders

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.Schedule
import app.focusmodeplus.core.model.ScheduleId
import app.focusmodeplus.core.model.ScheduleZone
import app.focusmodeplus.core.notifications.NotificationCapabilityReader
import app.focusmodeplus.core.protection.BatteryOptimizationReader
import app.focusmodeplus.core.protection.ProtectionCapabilityOwner
import app.focusmodeplus.core.testing.FakeAppCatalog
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeExactAlarms
import app.focusmodeplus.core.testing.FakeManagedDevice
import app.focusmodeplus.core.testing.FakePlatform
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.FakeScheduleRepository
import app.focusmodeplus.core.testing.FakeSelectedAppRepository
import app.focusmodeplus.core.testing.FakeUsageAccess
import app.focusmodeplus.core.testing.MainDispatcherRule
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
