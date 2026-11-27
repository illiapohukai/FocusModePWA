package app.focusmodeplus.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.focusmodeplus.core.data.backup.BackupImportRepository
import app.focusmodeplus.core.data.backup.RoomPersonalDataStore
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.repository.RoomMissionRepository
import app.focusmodeplus.core.data.repository.RoomSessionRepository
import app.focusmodeplus.core.domain.account.AccountLifecycle
import app.focusmodeplus.core.domain.backup.PersonalDataEraser
import app.focusmodeplus.core.domain.mission.MissionAdaptationOwner
import app.focusmodeplus.core.domain.mission.MissionGenerationOwner
import app.focusmodeplus.core.domain.sync.SyncEngine
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.SessionEndReason
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.testing.FakeAccountRepository
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeMaterialStore
import app.focusmodeplus.core.testing.FakeMissionAdapter
import app.focusmodeplus.core.testing.FakeMissionGenerator
import app.focusmodeplus.core.testing.FakeNetworkStatus
import app.focusmodeplus.core.testing.FakePlatform
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.FakeRemoteSyncStore
import app.focusmodeplus.core.testing.FakeSyncScheduler
import app.focusmodeplus.core.testing.Fixtures
import app.focusmodeplus.core.testing.IdleSessionController
import app.focusmodeplus.core.testing.NoopReminderScheduler
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
                missionLink = MissionLink(app.focusmodeplus.core.model.MissionId(missionId), app.focusmodeplus.core.model.MissionStepId(stepId)),
            )
    }
}
