package app.focusmodeplus.core.data.backup

import androidx.room.withTransaction
import app.focusmodeplus.core.data.db.ActivityEntity
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.db.MissionProgressEntity
import app.focusmodeplus.core.data.db.toEntity
import app.focusmodeplus.core.data.sync.SyncOutboxWriter
import app.focusmodeplus.core.domain.backup.BackupImportGateway
import app.focusmodeplus.core.domain.backup.BackupParseResult
import app.focusmodeplus.core.domain.backup.ImportApplyResult
import app.focusmodeplus.core.domain.backup.ImportPreview
import app.focusmodeplus.core.domain.backup.ImportPreviewResult
import app.focusmodeplus.core.domain.backup.ImportPlan
import app.focusmodeplus.core.domain.backup.PwaBackupImporter
import app.focusmodeplus.core.domain.time.FocusClock
import app.focusmodeplus.core.model.ActivityType
import app.focusmodeplus.core.model.EntityIds
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.domain.mission.MissionRules
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.SyncEntityType
import app.focusmodeplus.core.model.SyncOperation
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PWA backup migration path: preview first, then one Room transaction. Any failure rolls the
 * whole import back. Existing records are never overwritten (ids already present are skipped).
 */
@Singleton
class BackupImportRepository @Inject constructor(
    private val db: FocusDatabase,
    private val clock: FocusClock,
) : BackupImportGateway {
    private val importer = PwaBackupImporter()
    private val outbox = SyncOutboxWriter(db)

    override suspend fun preview(bytes: ByteArray): ImportPreviewResult {
        // v22 (V22-F05): parsing and the integrity check run off the caller's (main) thread.
        val parsed = withContext(Dispatchers.Default) { importer.parse(bytes, clock.zone(), clock.now()) }
        val plan = when (val r = parsed) {
            is BackupParseResult.Rejected -> return ImportPreviewResult.Rejected(r.message)
            is BackupParseResult.Ok -> r.plan
        }
        val existingMissions = db.missionDao().allIds().toSet()
        val existingSessions = db.sessionDao().allResultIds().toSet()
        val newMissions = plan.missions.filter { it.id.value !in existingMissions }
        return ImportPreviewResult.Ready(
            ImportPreview(
                plan = plan,
                newMissions = newMissions.size,
                duplicateMissions = plan.missions.size - newMissions.size,
                newSessions = plan.results.count { it.sessionId.value !in existingSessions },
                duplicateSessions = plan.results.count { it.sessionId.value in existingSessions },
                missionsArchivedByLimit = (newMissions.count { it.status == MissionStatus.ACTIVE } - (Mission.MAX_ACTIVE - db.missionDao().countActive()).coerceAtLeast(0)).coerceAtLeast(0),
            ),
        )
    }

    override suspend fun apply(plan: ImportPlan): ImportApplyResult = db.withTransaction {
        val now = clock.now()
        val today = now.atZone(clock.zone()).toLocalDate()
        val existing = db.missionDao().allIds().toSet()
        var activeSlots = (Mission.MAX_ACTIVE - db.missionDao().countActive()).coerceAtLeast(0)
        var missions = 0
        // PWA step ids are only unique inside one Mission ("session-1" repeats across Missions), but
        // Android step ids are global keys. A colliding id gets a fresh local id, and the imported
        // Sessions that point at it follow, so no existing step is ever overwritten or moved.
        val usedInBatch = HashSet<String>()
        val remap = HashMap<Pair<String, String>, MissionStepId>()
        plan.missions.filter { it.id.value !in existing }.forEach { source ->
            val ids = source.steps.map { it.id.value }
            val taken = (if (ids.isEmpty()) emptyList() else db.missionDao().existingStepIds(ids)).toSet() + ids.filter { it in usedInBatch }
            val m = if (taken.isEmpty()) source else {
                val map = source.steps.filter { it.id.value in taken }.associate { it.id to EntityIds.newStepId() }
                map.forEach { (old, new) -> remap[source.id.value to old.value] = new }
                source.copy(
                    steps = source.steps.map { s -> map[s.id]?.let { s.copy(id = it) } ?: s },
                    currentStepId = source.currentStepId?.let { map[it] ?: it },
                    archivedStepId = source.archivedStepId?.let { map[it] ?: it },
                )
            }
            usedInBatch += m.steps.map { it.id.value }
            val keepActive = m.status == MissionStatus.ACTIVE && activeSlots > 0
            if (keepActive) activeSlots--
            val mission = if (m.status == MissionStatus.ACTIVE && !keepActive) MissionRules.archive(m, now) else m
            db.missionDao().insert(mission.toEntity())
            db.missionDao().insertSteps(mission.steps.map { it.toEntity() })
            db.activityDao().insert(ActivityEntity(type = ActivityType.MISSION_IMPORTED.name, refId = mission.id.value, occurredAt = now, localDate = today))
            outbox.record(SyncEntityType.MISSION, mission.id.value, SyncOperation.UPSERT, now) // only when this dataset is linked to an account
            missions++
        }
        // Applied Mission results of the Missions inserted above, following any step-id remap.
        val inserted = HashSet<String>().apply { plan.missions.filter { it.id.value !in existing }.forEach { add(it.id.value) } }
        plan.progress.filter { it.missionId.value in inserted }.forEach { p ->
            val step = remap[p.missionId.value to p.stepId.value] ?: p.stepId
            db.missionProgressDao().insert(
                MissionProgressEntity(p.sessionId.value, p.missionId.value, step.value, p.outcome.wire, p.rating, p.actualMinutes, p.plannedMinutes, p.appliedAt),
            )
        }
        val results = plan.results.map { r ->
            val link = r.missionLink ?: return@map r
            remap[link.missionId.value to link.stepId.value]?.let { r.copy(missionLink = link.copy(stepId = it)) } ?: r
        }
        val rowIds = db.sessionDao().insertResults(results.map { it.toEntity(savedAt = now) })
        val sessions = rowIds.count { it != -1L }
        // Imported History synchronizes as ordinary, unverified History (never verified League data).
        results.zip(rowIds).filter { it.second != -1L }.forEach { (r, _) -> outbox.record(SyncEntityType.SESSION, r.sessionId.value, SyncOperation.UPSERT, now) }
        if (missions + sessions > 0) {
            db.activityDao().insert(ActivityEntity(type = ActivityType.DATA_IMPORTED.name, refId = "import:${now.toEpochMilli()}", occurredAt = now, localDate = today))
        }
        ImportApplyResult(missions, sessions)
    }
}
