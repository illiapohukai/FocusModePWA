package io.github.illiapohukai.focusmode.core.data.backup

import androidx.room.withTransaction
import io.github.illiapohukai.focusmode.core.data.db.ActivityEntity
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.db.toEntity
import io.github.illiapohukai.focusmode.core.domain.backup.BackupImportGateway
import io.github.illiapohukai.focusmode.core.domain.backup.BackupParseResult
import io.github.illiapohukai.focusmode.core.domain.backup.ImportApplyResult
import io.github.illiapohukai.focusmode.core.domain.backup.ImportPreview
import io.github.illiapohukai.focusmode.core.domain.backup.ImportPreviewResult
import io.github.illiapohukai.focusmode.core.domain.backup.ImportPlan
import io.github.illiapohukai.focusmode.core.domain.backup.PwaBackupImporter
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.ActivityType
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import java.time.LocalDate
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

    override suspend fun preview(bytes: ByteArray): ImportPreviewResult {
        val plan = when (val r = importer.parse(bytes, clock.zone(), clock.now())) {
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
        plan.missions.filter { it.id.value !in existing }.forEach { m ->
            val keepActive = m.status == MissionStatus.ACTIVE && activeSlots > 0
            if (keepActive) activeSlots--
            val mission = if (m.status == MissionStatus.ACTIVE && !keepActive) m.copy(status = MissionStatus.ARCHIVED, archivedAt = now, currentStepId = null) else m
            db.missionDao().insert(mission.toEntity())
            db.missionDao().upsertSteps(mission.steps.map { it.toEntity() })
            db.activityDao().insert(ActivityEntity(type = ActivityType.MISSION_IMPORTED.name, refId = mission.id.value, occurredAt = now, localDate = today))
            missions++
        }
        val inserted = db.sessionDao().insertResults(plan.results.map { it.toEntity(savedAt = now) }).count { it != -1L }
        if (missions + inserted > 0) {
            db.activityDao().insert(ActivityEntity(type = ActivityType.DATA_IMPORTED.name, refId = "import:${now.toEpochMilli()}", occurredAt = now, localDate = today))
        }
        ImportApplyResult(missions, inserted)
    }
}
