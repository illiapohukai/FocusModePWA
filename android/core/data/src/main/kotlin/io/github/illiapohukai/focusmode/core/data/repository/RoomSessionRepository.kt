package io.github.illiapohukai.focusmode.core.data.repository

import androidx.room.withTransaction
import io.github.illiapohukai.focusmode.core.data.db.ActivityEntity
import io.github.illiapohukai.focusmode.core.data.db.MissionProgressEntity
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.db.toEntity
import io.github.illiapohukai.focusmode.core.data.db.toModel
import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.mission.StepCompletion
import io.github.illiapohukai.focusmode.core.domain.repository.ActiveSessionLoad
import io.github.illiapohukai.focusmode.core.domain.repository.ActiveSessionStore
import io.github.illiapohukai.focusmode.core.domain.repository.FinalizeOutcome
import io.github.illiapohukai.focusmode.core.domain.repository.ReviewApplication
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.ActivityRecord
import io.github.illiapohukai.focusmode.core.model.ActivityType
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionReview
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Active Session store and History owner in one class, because finalization must clear the
 * active row and append History + Activity in a single Room transaction.
 */
@Singleton
class RoomSessionRepository @Inject constructor(
    private val db: FocusDatabase,
    private val clock: FocusClock,
) : ActiveSessionStore, SessionHistoryRepository {

    private val sessions get() = db.sessionDao()
    private val activity get() = db.activityDao()

    override suspend fun load(): ActiveSessionLoad {
        val row = sessions.getActive() ?: return ActiveSessionLoad.None
        val model = row.toModel()
        if (model != null) return ActiveSessionLoad.Loaded(model)
        sessions.deleteActive(row.sessionId)
        return ActiveSessionLoad.Corrupt("active_session_invalid")
    }

    override suspend fun save(session: FocusSession) {
        db.withTransaction {
            val existing = sessions.getActive()
            when {
                existing == null -> sessions.insertActive(session.toEntity())
                existing.sessionId == session.id.value -> sessions.updateActive(session.toEntity())
                else -> error("another_session_active")
            }
        }
    }

    override suspend fun clear(id: SessionId): Boolean = sessions.deleteActive(id.value) > 0

    override fun observeResults(): Flow<List<FocusSessionResult>> =
        sessions.observeResults().map { rows -> rows.mapNotNull { it.toModel() } }

    override fun observeResult(id: SessionId): Flow<FocusSessionResult?> = sessions.observeResult(id.value).map { it?.toModel() }

    override suspend fun getResult(id: SessionId): FocusSessionResult? = sessions.getResult(id.value)?.toModel()

    override suspend fun finalize(result: FocusSessionResult): FinalizeOutcome = db.withTransaction {
        val created = sessions.insertResult(result.toEntity(savedAt = clock.now())) != -1L
        sessions.deleteActive(result.sessionId.value)
        if (created) {
            activity.insert(ActivityEntity(type = ActivityType.SESSION_FINISHED.name, refId = result.sessionId.value, occurredAt = result.finishedAt, localDate = result.localDate))
        }
        val stored = sessions.getResult(result.sessionId.value)?.toModel() ?: error("finalize_read_back_failed")
        FinalizeOutcome(stored, created)
    }

    override suspend fun applyMissionReview(id: SessionId, review: MissionReview): ReviewApplication? =
        db.withTransaction {
            val current = sessions.getResult(id.value)?.toModel() ?: return@withTransaction null
            val link = current.missionLink
            val missionDao = db.missionDao()
            val progressDao = db.missionProgressDao()
            // Applied before (double tap, restart, retry): nothing changes a second time.
            if (current.endReason != SessionEndReason.AWAITING_USER_REVIEW || (link != null && progressDao.get(id.value) != null)) {
                return@withTransaction ReviewApplication(current, link?.let { missionDao.get(it.missionId.value)?.toModel() }, applied = false)
            }
            val now = clock.now()
            sessions.updateReview(id.value, review.outcome.wire, review.rating?.coerceIn(1, 5), SessionEndReason.USER_REVIEWED.wire, review.note)
            var mission: Mission? = null
            if (link != null) {
                mission = missionDao.get(link.missionId.value)?.toModel()
                if (mission != null) {
                    progressDao.insert(
                        MissionProgressEntity(
                            sessionId = id.value, missionId = mission.id.value, stepId = link.stepId.value, outcome = review.outcome.wire,
                            rating = review.rating?.coerceIn(1, 5), actualMinutes = current.actualMinutes, plannedMinutes = current.plannedMinutes, appliedAt = now,
                        ),
                    )
                    val next = if (review.completesStep) {
                        (MissionRules.completeStep(mission, link.stepId, now) as? StepCompletion.Completed)?.mission
                    } else {
                        MissionRules.keepStepCurrent(mission, link.stepId, now)
                    }
                    if (next != null) {
                        missionDao.update(next.toEntity())
                        missionDao.upsertSteps(next.steps.map { it.toEntity() })
                        mission = next
                        if (review.completesStep) {
                            activity.insert(
                                ActivityEntity(
                                    type = ActivityType.MISSION_STEP_COMPLETED.name,
                                    refId = "${link.missionId.value}:${link.stepId.value}",
                                    occurredAt = now,
                                    localDate = now.atZone(clock.zone()).toLocalDate(),
                                ),
                            )
                        }
                    }
                }
            }
            ReviewApplication(sessions.getResult(id.value)!!.toModel()!!, mission, applied = true)
        }

    override suspend fun pendingReview(): FocusSessionResult? = sessions.pendingReview()?.toModel()

    override fun observeActivity(limit: Int): Flow<List<ActivityRecord>> =
        activity.observeRecent(limit).map { rows -> rows.mapNotNull { it.toModel() } }
}
