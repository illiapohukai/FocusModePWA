package io.github.illiapohukai.focusmode.core.data.repository

import androidx.room.withTransaction
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.db.toEntity
import io.github.illiapohukai.focusmode.core.data.db.toModel
import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.mission.StepCompletion
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The one Mission owner (PWA `mission-repository.js` rules: max two active, safe ids, guards). */
@Singleton
class RoomMissionRepository @Inject constructor(
    private val db: FocusDatabase,
    private val clock: FocusClock,
    private val preferences: PreferencesRepository,
) : MissionRepository {

    private val dao get() = db.missionDao()

    override fun observeMissions(): Flow<List<Mission>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    override fun observeMission(id: MissionId): Flow<Mission?> = dao.observe(id.value).map { it?.toModel() }

    override suspend fun getMission(id: MissionId): Mission? = dao.get(id.value)?.toModel()

    override fun observePrimaryMission(): Flow<Mission?> =
        combine(observeMissions(), preferences.preferences) { missions, prefs ->
            val active = missions.filter { it.status == MissionStatus.ACTIVE }
            active.firstOrNull { it.id.value == prefs.lastSelectedMissionId } ?: active.firstOrNull()
        }

    override suspend fun create(mission: Mission): MissionWriteResult = db.withTransaction {
        if (!EntityIds.isSafe(mission.id.value) || mission.steps.any { !EntityIds.isSafe(it.id.value) }) {
            return@withTransaction MissionWriteResult.Rejected("unsafe_id")
        }
        if (mission.steps.map { it.id }.toSet().size != mission.steps.size) return@withTransaction MissionWriteResult.Rejected("step_id_duplicate")
        if (dao.get(mission.id.value) != null) return@withTransaction MissionWriteResult.Rejected("mission_id_duplicate")
        if (mission.status == MissionStatus.ACTIVE && dao.countActive() >= Mission.MAX_ACTIVE) {
            return@withTransaction MissionWriteResult.Rejected("active_mission_limit_reached")
        }
        dao.insert(mission.toEntity())
        dao.upsertSteps(mission.steps.map { it.toEntity() })
        MissionWriteResult.Ok(dao.get(mission.id.value)!!.toModel()!!)
    }

    override suspend fun completeStep(missionId: MissionId, stepId: MissionStepId): MissionWriteResult = db.withTransaction {
        val mission = dao.get(missionId.value)?.toModel() ?: return@withTransaction MissionWriteResult.Rejected("mission_not_found")
        when (val c = MissionRules.completeStep(mission, stepId, clock.now())) {
            is StepCompletion.Completed -> {
                dao.update(c.mission.toEntity())
                dao.upsertSteps(c.mission.steps.map { it.toEntity() })
                MissionWriteResult.Ok(c.mission)
            }
            is StepCompletion.AlreadyCompleted -> MissionWriteResult.Ok(c.mission)
            StepCompletion.StepNotFound -> MissionWriteResult.Rejected("step_not_found")
        }
    }

    override suspend fun archive(id: MissionId): MissionWriteResult = db.withTransaction {
        if (db.sessionDao().activeCountForMission(id.value) > 0) return@withTransaction MissionWriteResult.Rejected("active_session_exists")
        val mission = dao.get(id.value)?.toModel() ?: return@withTransaction MissionWriteResult.Rejected("mission_not_found")
        val archived = mission.copy(status = MissionStatus.ARCHIVED, archivedAt = clock.now(), updatedAt = clock.now(), currentStepId = null)
        dao.update(archived.toEntity())
        MissionWriteResult.Ok(archived)
    }

    override suspend fun delete(id: MissionId): MissionWriteResult = db.withTransaction {
        if (db.sessionDao().activeCountForMission(id.value) > 0) return@withTransaction MissionWriteResult.Rejected("active_session_exists")
        val mission = dao.get(id.value)?.toModel() ?: return@withTransaction MissionWriteResult.Rejected("mission_not_found")
        dao.delete(id.value) // steps cascade; History keeps the plain mission id
        MissionWriteResult.Ok(mission)
    }
}
