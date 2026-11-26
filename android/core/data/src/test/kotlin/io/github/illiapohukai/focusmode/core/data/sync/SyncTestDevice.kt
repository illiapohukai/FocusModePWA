package io.github.illiapohukai.focusmode.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.illiapohukai.focusmode.core.data.backup.BackupImportRepository
import io.github.illiapohukai.focusmode.core.data.backup.RoomPersonalDataStore
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.repository.RoomMissionRepository
import io.github.illiapohukai.focusmode.core.data.repository.RoomSessionRepository
import io.github.illiapohukai.focusmode.core.domain.account.AccountLifecycle
import io.github.illiapohukai.focusmode.core.domain.backup.PersonalDataEraser
import io.github.illiapohukai.focusmode.core.domain.mission.MissionAdaptationOwner
import io.github.illiapohukai.focusmode.core.domain.mission.MissionGenerationOwner
import io.github.illiapohukai.focusmode.core.domain.sync.SyncEngine
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionType
import io.github.illiapohukai.focusmode.core.testing.FakeAccountRepository
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakeMaterialStore
import io.github.illiapohukai.focusmode.core.testing.FakeMissionAdapter
import io.github.illiapohukai.focusmode.core.testing.FakeMissionGenerator
import io.github.illiapohukai.focusmode.core.testing.FakeNetworkStatus
import io.github.illiapohukai.focusmode.core.testing.FakePlatform
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.FakeRemoteSyncStore
import io.github.illiapohukai.focusmode.core.testing.FakeSyncScheduler
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import io.github.illiapohukai.focusmode.core.testing.IdleSessionController
import io.github.illiapohukai.focusmode.core.testing.NoopReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.time.LocalDate

/**
 * One simulated device: its own Room database, repositories, account owner and sync engine, all
 * talking to a shared [FakeRemoteSyncStore]. [restartProcess] keeps the database ("disk") and
 * replaces every in-memory owner, like process death and a cold start.
 */
class SyncTestDevice(val remote: FakeRemoteSyncStore, val clock: FakeClock = FakeClock()) {
    val db: FocusDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FocusDatabase::class.java)
        .allowMainThreadQueries().build()
    val prefs = FakePreferencesRepository()
    val accounts = FakeAccountRepository()
    val scheduler = FakeSyncScheduler()
    val network = FakeNetworkStatus(true)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val missions = RoomMissionRepository(db, clock, prefs)
    val sessions = RoomSessionRepository(db, clock)
    val importer = BackupImportRepository(db, clock)
    var store = RoomSyncLocalStore(db, clock); private set
    var engine = newEngine(); private set
    val idle = IdleSessionController()

    private fun newEngine() = SyncEngine(accounts, store, remote, scheduler, prefs, network, clock)

    fun restartProcess() {
        store = RoomSyncLocalStore(db, clock)
        engine = newEngine()
    }

    fun eraser(): PersonalDataEraser {
        val material = FakeMaterialStore()
        return PersonalDataEraser(
            idle,
            MissionGenerationOwner(missions, FakeMissionGenerator(), material, prefs, network, clock, scope),
            MissionAdaptationOwner(missions, sessions, FakeMissionAdapter(), network, scope),
            RoomPersonalDataStore(db), prefs, material, NoopReminderScheduler(), FakePlatform(),
            accountHook = engine,
        )
    }

    fun lifecycle() = AccountLifecycle(accounts, engine, eraser(), remote, store, scheduler)

    fun count(table: String): Int = db.query("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    fun close() {
        scope.cancel()
        db.close()
    }

    companion object {
        fun quick(id: String, day: String, minutes: Int = 25): FocusSessionResult = Fixtures.result(id, LocalDate.parse(day), minutes)

        fun awaitingReview(id: String, missionId: String, stepId: String, day: String = "2026-11-20"): FocusSessionResult =
            quick(id, day).copy(
                type = SessionType.MISSION,
                endReason = SessionEndReason.AWAITING_USER_REVIEW,
                missionLink = MissionLink(io.github.illiapohukai.focusmode.core.model.MissionId(missionId), io.github.illiapohukai.focusmode.core.model.MissionStepId(stepId)),
            )
    }
}
