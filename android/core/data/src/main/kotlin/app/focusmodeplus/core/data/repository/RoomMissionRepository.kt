package app.focusmodeplus.core.data.repository

import androidx.room.withTransaction
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.sync.SyncOutboxWriter
import app.focusmodeplus.core.data.db.stepEntities
import app.focusmodeplus.core.data.db.toEntity
import app.focusmodeplus.core.data.db.toModel
import app.focusmodeplus.core.domain.ai.AdaptedStep
import app.focusmodeplus.core.domain.mission.MissionDraft
import app.focusmodeplus.core.domain.mission.MissionDraftPhase
import app.focusmodeplus.core.domain.mission.MissionRules
import app.focusmodeplus.core.domain.mission.StepCompletion
import app.focusmodeplus.core.domain.repository.MissionProgressEntry
import app.focusmodeplus.core.domain.repository.MissionRepository
import app.focusmodeplus.core.domain.repository.MissionWriteResult
import app.focusmodeplus.core.domain.repository.PreferencesRepository
import app.focusmodeplus.core.domain.time.FocusClock
import app.focusmodeplus.core.model.EntityIds
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.SyncEntityType
import app.focusmodeplus.core.model.SyncOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one Mission owner (PWA `mission-repository.js` rules: max two active, safe ids, guards).
 * Every write that touches more than one row runs in one Room transaction. v21: every Mission
 * change also writes its sync outbox mutation inside that transaction (only for account-bound data).
 */
@Singleton
class RoomMissionRepository @Inject constructor(
    private val db: FocusDatabase,
    private val clock: FocusClock,
    private val preferences: PreferencesRepository,
) : MissionRepository {

    private val dao get() = db.missionDao()
    private val drafts get() = db.missionDraftDao()
    private val outbox = SyncOutboxWriter(db)

    /** Inside the caller's transaction. */
    private suspend fun recordUpsert(id: MissionId) = outbox.record(SyncEntityType.MISSION, id.value, SyncOperation.UPSERT, clock.now())

    override fun observeMissions(): Flow<List<Mission>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    override fun observeMission(id: MissionId): Flow<Mission?> = dao.observe(id.value).map { it?.toModel() }

    override suspend fun getMission(id: MissionId): Mission? = dao.get(id.value)?.toModel()

    override fun observePrimaryMission(): Flow<Mission?> =
        combine(observeMissions(), preferences.preferences) { missions, prefs ->
            val active = missions.filter { it.status == MissionStatus.ACTIVE }
            active.firstOrNull { it.id.value == prefs.lastSelectedMissionId } ?: active.firstOrNull()
        }

    override suspend fun create(mission: Mission): MissionWriteResult = db.withTransaction { insertLocked(mission) }

    /** Must run inside a transaction. */
    private suspend fun insertLocked(mission: Mission): MissionWriteResult {
        if (!EntityIds.isSafe(mission.id.value) || mission.steps.any { !EntityIds.isSafe(it.id.value) }) return MissionWriteResult.Rejected("unsafe_id")
        if (mission.steps.map { it.id }.toSet().size != mission.steps.size) return MissionWriteResult.Rejected("step_id_duplicate")
        if (mission.steps.map { it.position }.toSet().size != mission.steps.size) return MissionWriteResult.Rejected("step_order_invalid")
        if (dao.get(mission.id.value) != null) return MissionWriteResult.Rejected("mission_id_duplicate")
        if (mission.steps.isNotEmpty() && dao.countStepIds(mission.steps.map { it.id.value }) > 0) return MissionWriteResult.Rejected("step_id_duplicate")
        if (mission.status == MissionStatus.ACTIVE && !MissionRules.canCreateActive(dao.countActive())) {
            return MissionWriteResult.Rejected("active_mission_limit_reached")
        }
        dao.insert(mission.toEntity())
        dao.insertSteps(mission.steps.map { it.toEntity() })
        recordUpsert(mission.id)
        return MissionWriteResult.Ok(dao.get(mission.id.value)!!.toModel()!!)
    }

    override suspend fun completeStep(missionId: MissionId, stepId: MissionStepId): MissionWriteResult = db.withTransaction {
        val mission = dao.get(missionId.value)?.toModel() ?: return@withTransaction MissionWriteResult.Rejected("mission_not_found")
        when (val c = MissionRules.completeStep(mission, stepId, clock.now())) {
            is StepCompletion.Completed -> {
                dao.update(c.mission.toEntity())
                dao.upsertSteps(c.mission.steps.map { it.toEntity() })
                recordUpsert(missionId)
                MissionWriteResult.Ok(c.mission)
            }
            is StepCompletion.AlreadyCompleted -> MissionWriteResult.Ok(c.mission)
            StepCompletion.StepNotFound -> MissionWriteResult.Rejected("step_not_found")
        }
    }

    override suspend fun archive(id: MissionId): MissionWriteResult = db.withTransaction {
        if (db.sessionDao().activeCountForMission(id.value) > 0) return@withTransaction MissionWriteResult.Rejected("active_session_exists")
        val mission = dao.get(id.value)?.toModel() ?: return@withTransaction MissionWriteResult.Rejected("mission_not_found")
        if (mission.status == MissionStatus.ARCHIVED) return@withTransaction MissionWriteResult.Ok(mission)
        val archived = MissionRules.archive(mission, clock.now())
        dao.update(archived.toEntity())
        recordUpsert(id)
        MissionWriteResult.Ok(archived)
    }.also { clearSelectionIf(id, it) }

    override suspend fun restore(id: MissionId): MissionWriteResult = db.withTransaction {
        val mission = dao.get(id.value)?.toModel() ?: return@withTransaction MissionWriteResult.Rejected("mission_not_found")
        val restored = MissionRules.restore(mission, clock.now()) ?: return@withTransaction MissionWriteResult.Rejected("mission_not_archived")
        if (restored.status == MissionStatus.ACTIVE && !MissionRules.canCreateActive(dao.countActive())) {
            return@withTransaction MissionWriteResult.Rejected("active_mission_limit_reached")
        }
        dao.update(restored.toEntity())
        dao.upsertSteps(restored.steps.map { it.toEntity() })
        recordUpsert(id)
        MissionWriteResult.Ok(restored)
    }

    override suspend fun delete(id: MissionId): MissionWriteResult = db.withTransaction {
        // A running or frozen Session references the Mission (RESTRICT); it is never deleted under it.
        if (db.sessionDao().activeCountForMission(id.value) > 0) return@withTransaction MissionWriteResult.Rejected("active_session_exists")
        val mission = dao.get(id.value)?.toModel() ?: return@withTransaction MissionWriteResult.Rejected("mission_not_found")
        dao.delete(id.value) // steps and progress entries cascade; History keeps the plain mission id
        outbox.record(SyncEntityType.MISSION, id.value, SyncOperation.DELETE, clock.now()) // tombstone: never resurrected by a download
        MissionWriteResult.Ok(mission)
    }.also { clearSelectionIf(id, it) }

    /** PWA `renameMissionV116`: one row update; the id (identity colour, steps, History, reminders) stays. */
    override suspend fun rename(id: MissionId, title: String): MissionWriteResult = db.withTransaction {
        val mission = dao.get(id.value)?.toModel() ?: return@withTransaction MissionWriteResult.Rejected("mission_not_found")
        val renamed = MissionRules.rename(mission, title, clock.now()) ?: return@withTransaction MissionWriteResult.Rejected("title_invalid")
        if (renamed != mission) {
            dao.update(renamed.toEntity())
            recordUpsert(id)
        }
        MissionWriteResult.Ok(renamed)
    }

    /** The primary/selected Mission preference never points at an inactive or deleted Mission. */
    private suspend fun clearSelectionIf(id: MissionId, result: MissionWriteResult) {
        if (result is MissionWriteResult.Ok && preferences.current().lastSelectedMissionId == id.value) preferences.setLastSelectedMission(null)
    }

    // ---------------------------------------------------------------- draft

    override fun observeDraft(): Flow<MissionDraft?> = drafts.observe().map { it?.toModel() }

    override suspend fun getDraft(): MissionDraft? = drafts.get()?.toModel()

    override suspend fun saveDraft(draft: MissionDraft) = db.withTransaction { writeDraftLocked(draft) }

    private suspend fun writeDraftLocked(draft: MissionDraft) {
        drafts.upsert(draft.toEntity())
        drafts.clearSteps()
        val steps = draft.stepEntities()
        if (steps.isNotEmpty()) drafts.insertSteps(steps)
    }

    override suspend fun saveDraftIf(draft: MissionDraft, expectedPhase: MissionDraftPhase): Boolean = db.withTransaction {
        val current = drafts.get()?.toModel() ?: return@withTransaction false
        if (current.draftId != draft.draftId || current.phase != expectedPhase) return@withTransaction false
        writeDraftLocked(draft)
        true
    }

    override suspend fun discardDraft() = db.withTransaction { drafts.clear() } // steps cascade

    override suspend fun acceptDraft(draftId: String): MissionWriteResult = db.withTransaction {
        val draft = drafts.get()?.toModel()
        if (draft == null || draft.draftId != draftId) {
            return@withTransaction MissionWriteResult.Rejected("draft_missing")
        }
        // Double tap or process restart after a successful accept: the same Mission, never a second one.
        dao.get(draft.missionId.value)?.toModel()?.let { existing ->
            drafts.clear()
            return@withTransaction MissionWriteResult.Ok(existing)
        }
        val plan = draft.plan
        if (draft.phase != MissionDraftPhase.BRIEFING || plan == null) return@withTransaction MissionWriteResult.Rejected("plan_missing")
        val mission = runCatching { MissionRules.missionFromPlan(draft, plan, clock.now()) }.getOrNull()
            ?: return@withTransaction MissionWriteResult.Rejected("plan_invalid")
        val inserted = insertLocked(mission)
        if (inserted is MissionWriteResult.Ok) drafts.clear()
        inserted
    }

    // ---------------------------------------------------------------- adaptation and progress

    override suspend fun replaceRemainingSteps(id: MissionId, steps: List<AdaptedStep>, expectedUpdatedAt: Instant): MissionWriteResult = db.withTransaction {
        val mission = dao.get(id.value)?.toModel() ?: return@withTransaction MissionWriteResult.Rejected("mission_not_found")
        if (mission.updatedAt != expectedUpdatedAt) return@withTransaction MissionWriteResult.Rejected("mission_changed")
        if (db.sessionDao().activeCountForMission(id.value) > 0) return@withTransaction MissionWriteResult.Rejected("active_session_exists")
        val updated = MissionRules.replaceRemainingSteps(mission, steps, clock.now()) ?: return@withTransaction MissionWriteResult.Rejected("adaptation_invalid")
        val fresh = updated.steps.filter { s -> mission.steps.none { it.id == s.id } }
        if (dao.countStepIds(fresh.map { it.id.value }) > 0) return@withTransaction MissionWriteResult.Rejected("step_id_duplicate")
        dao.deleteOpenSteps(id.value)
        dao.insertSteps(fresh.map { it.toEntity() })
        dao.update(updated.toEntity())
        recordUpsert(id)
        MissionWriteResult.Ok(dao.get(id.value)!!.toModel()!!)
    }

    override suspend fun progressHistory(id: MissionId): List<MissionProgressEntry> =
        db.missionProgressDao().forMission(id.value).mapNotNull { it.toModel() }
}
