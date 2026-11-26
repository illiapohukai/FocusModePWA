package io.github.illiapohukai.focusmode.core.domain.backup

import io.github.illiapohukai.focusmode.core.domain.ai.MissionMaterialStore
import io.github.illiapohukai.focusmode.core.domain.di.AppVersionName
import io.github.illiapohukai.focusmode.core.domain.mission.AdaptationRun
import io.github.illiapohukai.focusmode.core.domain.mission.GenerationRun
import io.github.illiapohukai.focusmode.core.domain.mission.MissionAdaptationOwner
import io.github.illiapohukai.focusmode.core.domain.mission.MissionGenerationOwner
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderNotifications
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderScheduler
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderSyncReason
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.RestoreTrigger
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** One consistent read of everything a backup may contain (Room transaction + preferences). */
interface BackupSnapshotSource {
    suspend fun snapshot(): BackupContent
}

/** Storage side of full data deletion (Room and the draft photo live behind it). */
interface PersonalDataStore {
    /**
     * One Room transaction: Missions, steps, the generation draft, progress entries, History,
     * the active Session row, Activity, selected apps, Geplanter Fokus, distraction counts and
     * the DND restore record. All or nothing.
     */
    suspend fun wipeRecords()
    /** Reminder rows, after the scheduler cancelled their scheduled delivery. */
    suspend fun clearReminderRows()
    /** Rows of user data still present; 0 after a complete deletion. */
    suspend fun remainingRows(): Int
}

sealed interface ExportResult {
    data class Ready(val file: BackupFile) : ExportResult
    data class Failed(val message: String) : ExportResult
}

/**
 * The one export owner. It builds the file and, before anything is written, reads it back with
 * the same validator the import uses: a backup this app could not restore is never handed out.
 * Writing the bytes to the user's chosen document happens in the UI layer (Storage Access
 * Framework, no storage permission); [markWritten] records "Letztes Backup" only afterwards.
 */
@Singleton
class BackupExportOwner @Inject constructor(
    private val source: BackupSnapshotSource,
    private val preferences: PreferencesRepository,
    private val clock: FocusClock,
    @AppVersionName private val appVersion: String,
) {
    suspend fun export(scope: BackupScope): ExportResult {
        val content = runCatching { source.snapshot() }.getOrElse { return ExportResult.Failed(EXPORT_FAILED) }
        val file = AndroidBackupExporter(appVersion).export(content, scope, clock.now(), clock.zone())
        return when (val check = PwaBackupImporter().parse(file.json.toByteArray(Charsets.UTF_8), clock.zone(), clock.now())) {
            is BackupParseResult.Rejected -> ExportResult.Failed(TOO_LARGE.takeIf { file.json.length > ImportLimits().maxBytes } ?: EXPORT_FAILED)
            is BackupParseResult.Ok -> {
                val complete = check.plan.report.integrity == IntegrityStatus.VERIFIED &&
                    check.plan.results.size == file.sessionCount && check.plan.report.missionsFound == file.missionCount
                if (complete) ExportResult.Ready(file) else ExportResult.Failed(EXPORT_FAILED)
            }
        }
    }

    suspend fun markWritten() = preferences.setLastExportAt(clock.now())

    companion object {
        const val EXPORT_FAILED = "Das Backup konnte nicht erstellt werden. Deine Daten wurden nicht verändert."
        const val TOO_LARGE = "Das Backup wäre größer als 5 MB und könnte nicht wieder importiert werden. Exportiere „Nur Missionen & Einstellungen“."
    }
}

enum class EraseOutcome { COMPLETE, INCOMPLETE }

/**
 * "Alle persönlichen und nutzergenerierten Daten löschen" (PWA `deleteAllLocalData`, adapted to
 * Android). Order matters and every step is idempotent, so a repeated tap after a failure
 * finishes the job:
 * 1. the running or frozen Session is dropped through the one Session owner (service, end alarm
 *    and DND restored), a pending AI request and adaptation are cancelled;
 * 2. one Room transaction removes every user record;
 * 3. user preferences return to their defaults (technical startup metadata stays);
 * 4. the draft photo is deleted;
 * 5. the reminder scheduler re-syncs (nothing is wanted any more, so every scheduled occurrence
 *    is cancelled), then any remaining reminder rows are removed so nothing can be delivered;
 * 6. every notification the app posted is removed and the Session state is re-derived.
 * OS-level permissions the user granted (notifications, Usage Access, DND) are system settings
 * and are not changed; the app says so.
 */
@Singleton
class PersonalDataEraser @Inject constructor(
    private val session: FocusSessionController,
    private val generation: MissionGenerationOwner,
    private val adaptation: MissionAdaptationOwner,
    private val store: PersonalDataStore,
    private val preferences: PreferencesRepository,
    private val material: MissionMaterialStore,
    private val scheduler: ReminderScheduler,
    private val notifications: ReminderNotifications,
) {
    private val mutex = Mutex()

    suspend fun eraseAll(): EraseOutcome = mutex.withLock {
        if (generation.run.value is GenerationRun.Running) generation.cancel()
        if (adaptation.run.value is AdaptationRun.Running) adaptation.cancel()
        session.abandonForDataDeletion()
        store.wipeRecords()
        preferences.resetUserPreferences()
        material.clear()
        runCatching { scheduler.sync(ReminderSyncReason.DATA_CHANGED) }
        store.clearReminderRows()
        notifications.clearAll()
        session.restore(RestoreTrigger.APP_START)
        if (store.remainingRows() == 0) EraseOutcome.COMPLETE else EraseOutcome.INCOMPLETE
    }
}
