package io.github.illiapohukai.focusmode.core.data.sync

import androidx.room.withTransaction
import io.github.illiapohukai.focusmode.core.data.db.AccountBindingEntity
import io.github.illiapohukai.focusmode.core.data.db.ActivityEntity
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.db.MissionProgressEntity
import io.github.illiapohukai.focusmode.core.data.db.SyncConflictEntity
import io.github.illiapohukai.focusmode.core.data.db.SyncEntityStateEntity
import io.github.illiapohukai.focusmode.core.data.db.SyncOutboxEntity
import io.github.illiapohukai.focusmode.core.data.db.SyncTombstoneEntity
import io.github.illiapohukai.focusmode.core.data.db.toEntity
import io.github.illiapohukai.focusmode.core.data.db.toModel
import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.sync.AccountBinding
import io.github.illiapohukai.focusmode.core.domain.sync.ApplyReport
import io.github.illiapohukai.focusmode.core.domain.sync.EntityState
import io.github.illiapohukai.focusmode.core.domain.sync.LocalDataSummary
import io.github.illiapohukai.focusmode.core.domain.sync.MigrationScope
import io.github.illiapohukai.focusmode.core.domain.sync.MissionSnapshot
import io.github.illiapohukai.focusmode.core.domain.sync.OutboxEntry
import io.github.illiapohukai.focusmode.core.domain.sync.OutboxState
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteChangeSet
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteMission
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteSchema
import io.github.illiapohukai.focusmode.core.domain.sync.SyncConflictPolicy
import io.github.illiapohukai.focusmode.core.domain.sync.SyncCursors
import io.github.illiapohukai.focusmode.core.domain.sync.SyncLocalStore
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.ActivityType
import io.github.illiapohukai.focusmode.core.model.BindingStatus
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SyncEntityType
import io.github.illiapohukai.focusmode.core.model.SyncFailure
import io.github.illiapohukai.focusmode.core.model.SyncOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room side of v21 synchronization. Every write is one transaction that first re-checks the
 * binding: data of one account can never be written into a dataset bound to another account,
 * or into guest data after a sign-out removed the binding.
 */
@Singleton
class RoomSyncLocalStore @Inject constructor(
    private val db: FocusDatabase,
    private val clock: FocusClock,
) : SyncLocalStore {

    private val dao get() = db.syncDao()
    private val outbox = SyncOutboxWriter(db)

    override fun observeBinding(): Flow<AccountBinding?> = dao.observeBinding().map { it?.toModel() }.distinctUntilChanged()
    override suspend fun binding(): AccountBinding? = dao.binding()?.toModel()
    override fun observePendingCount(): Flow<Int> = dao.observePendingCount().distinctUntilChanged()
    override fun observeParkedCount(): Flow<Int> = dao.observeParkedCount().distinctUntilChanged()
    override fun observeHasLocalData(): Flow<Boolean> = dao.observeHasLocalData().distinctUntilChanged()

    override suspend fun localSummary(): LocalDataSummary = db.withTransaction {
        val missions = db.missionDao().getAll().mapNotNull { it.toModel() }
        val sessionIds = dao.finalizedSessionIds()
        val validMissions = missions.count { m -> RemoteSchema.isDocumentId(m.id.value) && m.steps.all { RemoteSchema.isDocumentId(it.id.value) } }
        val validSessions = sessionIds.count(RemoteSchema::isDocumentId)
        LocalDataSummary(
            missions = missions.size,
            missionSteps = dao.stepCount(),
            sessions = sessionIds.size,
            awaitingReview = dao.awaitingReviewCount(),
            historyFrom = dao.historyFrom()?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            historyTo = dao.historyTo()?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            focusMinutes = dao.focusMinutes(),
            activeMissions = missions.count { it.status == MissionStatus.ACTIVE },
            notSyncable = (missions.size - validMissions) + (sessionIds.size - validSessions),
        )
    }

    override suspend fun hasActiveSession(): Boolean = db.sessionDao().getActive() != null

    override suspend fun activate(uid: String, now: Instant) = db.withTransaction {
        if (dao.binding() == null) dao.putBinding(newBinding(uid, BindingStatus.ACTIVE, null, now))
    }

    override suspend fun beginMigration(uid: String, migrationId: String, now: Instant): Boolean = db.withTransaction {
        val b = dao.binding()
        if (b != null && b.uid != uid) return@withTransaction false
        if (b != null && (b.status == BindingStatus.ACTIVE.wire || (b.status == BindingStatus.MIGRATING.wire && b.migrationId == migrationId))) return@withTransaction true
        if (b != null && b.status == BindingStatus.DELETION_PENDING.wire) return@withTransaction false
        dao.putBinding(newBinding(uid, BindingStatus.MIGRATING, migrationId, now).copy(migrationStartedAt = now))
        val scope = scopeLocked()
        scope.missionIds.forEach { outbox.insert(uid, SyncEntityType.MISSION, it, SyncOperation.UPSERT, now) }
        scope.sessionIds.forEach { outbox.insert(uid, SyncEntityType.SESSION, it, SyncOperation.UPSERT, now) }
        true
    }

    override suspend fun migrationScope(): MigrationScope = db.withTransaction { scopeLocked() }

    private suspend fun scopeLocked(): MigrationScope {
        val missions = db.missionDao().getAll().mapNotNull { it.toModel() }
            .filter { m -> RemoteSchema.isDocumentId(m.id.value) && m.steps.all { RemoteSchema.isDocumentId(it.id.value) } }
            .map { it.id.value }.toSet()
        val sessions = dao.finalizedSessionIds().filter(RemoteSchema::isDocumentId).toSet()
        return MigrationScope(missions, sessions)
    }

    override suspend fun requeue(uid: String, missionIds: Collection<String>, sessionIds: Collection<String>, now: Instant) = db.withTransaction {
        if (dao.binding()?.uid != uid) return@withTransaction
        missionIds.forEach { outbox.insert(uid, SyncEntityType.MISSION, it, SyncOperation.UPSERT, now) }
        sessionIds.forEach { outbox.insert(uid, SyncEntityType.SESSION, it, SyncOperation.UPSERT, now) }
    }

    override suspend fun completeMigration(uid: String, migrationId: String, now: Instant): Boolean = db.withTransaction {
        val b = dao.binding() ?: return@withTransaction false
        if (b.uid != uid) return@withTransaction false
        if (b.status == BindingStatus.ACTIVE.wire) return@withTransaction true
        if (b.status != BindingStatus.MIGRATING.wire || b.migrationId != migrationId) return@withTransaction false
        dao.putBinding(b.copy(status = BindingStatus.ACTIVE.wire, migrationCompletedAt = now))
        true
    }

    override suspend fun markDeletionPending(uid: String): Boolean = db.withTransaction {
        val b = dao.binding() ?: return@withTransaction false
        if (b.uid != uid) return@withTransaction false
        dao.putBinding(b.copy(status = BindingStatus.DELETION_PENDING.wire))
        true
    }

    override suspend fun clearAccountSyncState() = db.withTransaction {
        dao.clearOutbox()
        dao.clearEntityStates()
        dao.clearTombstones()
        dao.clearConflicts()
        dao.clearBinding()
    }

    override suspend fun recordSuccess(uid: String, now: Instant) = db.withTransaction {
        dao.binding()?.takeIf { it.uid == uid }?.let { dao.putBinding(it.copy(lastSyncAt = now, lastFailure = null)) }
        Unit
    }

    override suspend fun recordFailure(uid: String, failure: SyncFailure) = db.withTransaction {
        dao.binding()?.takeIf { it.uid == uid }?.let { dao.putBinding(it.copy(lastFailure = failure.wire)) }
        Unit
    }

    override suspend fun recordSettings(uid: String, fingerprint: String, remoteMs: Long?) = db.withTransaction {
        dao.binding()?.takeIf { it.uid == uid }?.let { dao.putBinding(it.copy(settingsFingerprint = fingerprint, settingsRemoteMs = remoteMs ?: it.settingsRemoteMs)) }
        Unit
    }

    override suspend fun dueMutations(uid: String, now: Instant, limit: Int): List<OutboxEntry> = dao.due(uid, now, limit).mapNotNull { it.toModel() }
    override suspend fun pendingCount(uid: String): Int = dao.countForAccount(uid)
    override suspend fun mutationsFor(uid: String, type: SyncEntityType, id: String): List<OutboxEntry> = dao.outboxFor(uid, type.wire, id).mapNotNull { it.toModel() }

    override suspend fun loadMission(id: String): MissionSnapshot? = db.withTransaction {
        val mission = db.missionDao().get(id)?.toModel() ?: return@withTransaction null
        MissionSnapshot(mission, db.missionProgressDao().forMission(id).mapNotNull { it.toModel() })
    }

    override suspend fun loadSession(id: String): FocusSessionResult? = db.sessionDao().getResult(id)?.toModel()

    override suspend fun entityState(type: SyncEntityType, id: String): EntityState? = dao.entityState(type.wire, id)?.toModel()

    override suspend fun markUploaded(uid: String, mutationIds: Collection<String>, states: Collection<EntityState>): Boolean = db.withTransaction {
        if (dao.binding()?.uid != uid) return@withTransaction false
        mutationIds.chunked(500).forEach { dao.deleteOutbox(it) }
        if (states.isNotEmpty()) dao.putEntityStates(states.map { it.toEntity() })
        true
    }

    override suspend fun markFailed(mutationIds: Collection<String>, failure: SyncFailure, nextAttemptAt: Instant, park: Boolean) = db.withTransaction {
        mutationIds.chunked(500).forEach { dao.markFailed(it, failure.wire, nextAttemptAt, if (park) STATE_PARKED else STATE_PENDING) }
    }

    override suspend fun retryParked(uid: String, now: Instant) = dao.retryParked(uid, now)

    // ------------------------------------------------------------------------------ remote apply

    override suspend fun applyRemote(uid: String, changes: RemoteChangeSet, now: Instant, cursors: SyncCursors?): ApplyReport = db.withTransaction {
        val b = dao.binding()
        if (b == null || b.uid != uid || b.status == BindingStatus.DELETION_PENDING.wire) return@withTransaction ApplyReport(accepted = false)
        val r = applyLocked(uid, changes, now)
        val current = dao.binding()!!
        dao.putBinding(
            current.copy(
                cursorMissionsMs = if (cursors != null && r.deferredMissions == 0) cursors.missionsMs ?: current.cursorMissionsMs else current.cursorMissionsMs,
                cursorSessionsMs = cursors?.sessionsMs ?: current.cursorSessionsMs,
                cursorTombstonesMs = if (cursors != null && r.deferredTombstones == 0) cursors.tombstonesMs ?: current.cursorTombstonesMs else current.cursorTombstonesMs,
                conflictsResolved = current.conflictsResolved + r.report.conflicts,
            ),
        )
        r.report
    }

    override suspend fun replaceWithRemote(uid: String, changes: RemoteChangeSet, now: Instant, cursors: SyncCursors?): ApplyReport = db.withTransaction {
        if (db.sessionDao().getActive() != null) return@withTransaction ApplyReport(accepted = false)
        val p = db.personalDataDao()
        p.clearMissionProgress()
        p.clearHistory()
        p.clearMissionSteps()
        p.clearMissions()
        p.clearActivity()
        clearAccountSyncState()
        dao.putBinding(
            newBinding(uid, BindingStatus.ACTIVE, null, now).copy(
                cursorMissionsMs = cursors?.missionsMs, cursorSessionsMs = cursors?.sessionsMs, cursorTombstonesMs = cursors?.tombstonesMs,
            ),
        )
        applyLocked(uid, changes, now).report
    }

    private class ApplyResult(val report: ApplyReport, val deferredMissions: Int, val deferredTombstones: Int)

    private suspend fun applyLocked(uid: String, changes: RemoteChangeSet, now: Instant): ApplyResult {
        val missions = db.missionDao()
        val sessions = db.sessionDao()
        val progress = db.missionProgressDao()
        val activity = db.activityDao()
        var missionsApplied = 0
        var sessionsApplied = 0
        var tombstonesApplied = 0
        var conflicts = 0
        var deferredMissions = 0
        var deferredTombstones = 0
        val states = ArrayList<SyncEntityStateEntity>()

        // 1. Deletions win: a tombstoned Mission is removed here and never re-inserted.
        changes.tombstones.filter { it.entityType == SyncEntityType.MISSION }.forEach { t ->
            if (sessions.activeCountForMission(t.entityId) > 0) { deferredTombstones++; return@forEach } // the running Session keeps it until it ends
            dao.putTombstone(SyncTombstoneEntity(SyncEntityType.MISSION.wire, t.entityId, uid, t.deletedAt, SyncOutboxWriter.ORIGIN_REMOTE))
            states += SyncEntityStateEntity(SyncEntityType.MISSION.wire, t.entityId, uid, t.syncVersion, "", t.deviceMutationId)
            val pending = dao.outboxFor(uid, SyncEntityType.MISSION.wire, t.entityId)
            if (pending.isNotEmpty()) {
                dao.deleteOutbox(pending.map { it.mutationId })
                if (pending.any { it.operation == SyncOperation.UPSERT.wire }) {
                    conflicts++
                    dao.insertConflict(SyncConflictEntity(entityType = SyncEntityType.MISSION.wire, entityId = t.entityId, detectedAt = now, resolution = RESOLUTION_DELETED_REMOTELY))
                }
            }
            if (missions.get(t.entityId) != null) {
                missions.delete(t.entityId) // steps and progress cascade; History keeps its Sessions
                tombstonesApplied++
            }
        }

        // 2. Missions.
        changes.missions.forEach { rm ->
            val id = rm.snapshot.mission.id.value
            if (dao.tombstone(SyncEntityType.MISSION.wire, id) != null) return@forEach
            if (sessions.activeCountForMission(id) > 0) { deferredMissions++; return@forEach } // never change a Mission under a running Session
            val clash = dao.stepIdsOwnedByOthers(rm.stepIds.ifEmpty { listOf("") }, id)
            if (clash.isNotEmpty()) {
                conflicts++
                dao.insertConflict(SyncConflictEntity(entityType = SyncEntityType.MISSION.wire, entityId = id, detectedAt = now, resolution = RESOLUTION_STEP_ID_CLASH))
                return@forEach
            }
            val local = loadMissionLocked(id)
            val state = dao.entityState(SyncEntityType.MISSION.wire, id)
            if (local == null) {
                writeMission(rm.snapshot, now)
                states += rm.toState(uid)
                missionsApplied++
                return@forEach
            }
            if (state != null && rm.syncVersion <= state.remoteVersion) return@forEach // already have it (or newer)
            var pending = dao.outboxFor(uid, SyncEntityType.MISSION.wire, id)
            // Our own earlier write whose acknowledgement was lost: confirmed now.
            val ack = pending.indexOfFirst { it.mutationId == rm.deviceMutationId }
            if (ack >= 0) {
                dao.deleteOutbox(pending.take(ack + 1).map { it.mutationId })
                pending = pending.drop(ack + 1)
            }
            if (pending.isEmpty()) {
                writeMission(rm.snapshot, now)
                missionsApplied++
            } else {
                val merge = SyncConflictPolicy.mergeMission(local, pending.last().mutationId, rm.snapshot, rm.deviceMutationId)
                val merged = MissionSnapshot(merge.mission, merge.progress)
                writeMission(merged, now)
                if (merged.mission == rm.snapshot.mission && merged.progress.map { it.sessionId }.toSet() == rm.snapshot.progress.map { it.sessionId }.toSet()) {
                    dao.deleteOutbox(pending.map { it.mutationId }) // nothing left to upload
                }
                if (merge.conflict) {
                    conflicts++
                    dao.insertConflict(
                        SyncConflictEntity(entityType = SyncEntityType.MISSION.wire, entityId = id, detectedAt = now, resolution = if (merge.remoteWins) RESOLUTION_REMOTE_KEPT else RESOLUTION_LOCAL_KEPT),
                    )
                }
                missionsApplied++
            }
            states += rm.toState(uid)
        }

        // 3. Finalized Sessions: append-only by Session id.
        changes.sessions.forEach { rs ->
            val r = rs.result
            val existing = sessions.getResult(r.sessionId.value)?.toModel()
            when {
                existing == null -> {
                    sessions.insertResult(r.toEntity(savedAt = now))
                    activity.insert(ActivityEntity(type = ActivityType.SESSION_FINISHED.name, refId = r.sessionId.value, occurredAt = r.finishedAt, localDate = r.localDate))
                    sessionsApplied++
                }
                SyncConflictPolicy.SessionPolicy.acceptsRemoteReview(existing.endReason == SessionEndReason.AWAITING_USER_REVIEW) && r.endReason == SessionEndReason.USER_REVIEWED -> {
                    sessions.updateReview(r.sessionId.value, r.outcome.wire, r.rating, r.endReason.wire, r.reviewNote)
                    sessionsApplied++
                }
            }
            // Progress entry of a reviewed Mission Session, if its Mission is here and lacks it.
            val link = r.missionLink
            if (link != null && r.endReason == SessionEndReason.USER_REVIEWED && missions.get(link.missionId.value) != null && progress.get(r.sessionId.value) == null) {
                progress.insert(MissionProgressEntity(r.sessionId.value, link.missionId.value, link.stepId.value, r.outcome.wire, r.rating, r.actualMinutes, r.plannedMinutes, r.finishedAt))
            }
            states += SyncEntityStateEntity(SyncEntityType.SESSION.wire, r.sessionId.value, uid, 1, "", rs.deviceMutationId)
        }

        if (states.isNotEmpty()) dao.putEntityStates(states)

        // 4. The two-active rule after merging: the most recently updated active Missions stay active.
        val active = missions.getAll().mapNotNull { it.toModel() }.filter { it.status == MissionStatus.ACTIVE }
        val pinned = active.filter { sessions.activeCountForMission(it.id.value) > 0 }
        val candidates = active - pinned.toSet()
        val toArchive = SyncConflictPolicy.missionsToArchive(candidates, (MissionRules.MAX_ACTIVE - pinned.size).coerceAtLeast(0))
        toArchive.forEach { m ->
            missions.update(MissionRules.archive(m, now).toEntity())
            outbox.record(SyncEntityType.MISSION, m.id.value, SyncOperation.UPSERT, now)
        }

        return ApplyResult(
            ApplyReport(missionsApplied, sessionsApplied, tombstonesApplied, conflicts, deferredMissions + deferredTombstones, toArchive.size),
            deferredMissions,
            deferredTombstones,
        )
    }

    private suspend fun loadMissionLocked(id: String): MissionSnapshot? {
        val mission = db.missionDao().get(id)?.toModel() ?: return null
        return MissionSnapshot(mission, db.missionProgressDao().forMission(id).mapNotNull { it.toModel() })
    }

    /** Writes a Mission exactly as given (steps replaced as a set, progress as a union) and its step-completion Activity. */
    private suspend fun writeMission(snapshot: MissionSnapshot, now: Instant) {
        val missions = db.missionDao()
        val m = snapshot.mission
        if (missions.get(m.id.value) == null) missions.insert(m.toEntity()) else missions.update(m.toEntity())
        dao.deleteSteps(m.id.value) // as a set: positions may move, the unique (mission, position) index must hold
        if (m.steps.isNotEmpty()) missions.insertSteps(m.steps.map { it.toEntity() })
        snapshot.progress.forEach { p ->
            db.missionProgressDao().insert(MissionProgressEntity(p.sessionId.value, m.id.value, p.stepId.value, p.outcome.wire, p.rating, p.actualMinutes, p.plannedMinutes, p.appliedAt))
        }
        m.steps.filter { it.status == MissionStepStatus.COMPLETED && it.completedAt != null }.forEach { s ->
            val at = s.completedAt!!
            db.activityDao().insert(
                ActivityEntity(type = ActivityType.MISSION_STEP_COMPLETED.name, refId = "${m.id.value}:${s.id.value}", occurredAt = at, localDate = at.atZone(clock.zone()).toLocalDate()),
            )
        }
    }

    private fun RemoteMission.toState(uid: String) =
        SyncEntityStateEntity(SyncEntityType.MISSION.wire, snapshot.mission.id.value, uid, syncVersion, stepIds.joinToString("\n"), deviceMutationId)

    private fun newBinding(uid: String, status: BindingStatus, migrationId: String?, now: Instant) = AccountBindingEntity(
        uid = uid, status = status.wire, migrationId = migrationId, boundAt = now, migrationStartedAt = null, migrationCompletedAt = null,
        lastSyncAt = null, lastFailure = null, settingsFingerprint = null, settingsRemoteMs = null,
        cursorMissionsMs = null, cursorSessionsMs = null, cursorTombstonesMs = null,
    )

    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_PARKED = "parked"
        const val RESOLUTION_REMOTE_KEPT = "newer_remote_plan_kept"
        const val RESOLUTION_LOCAL_KEPT = "newer_local_plan_kept"
        const val RESOLUTION_DELETED_REMOTELY = "deleted_on_other_device"
        const val RESOLUTION_STEP_ID_CLASH = "step_id_clash_not_applied"
    }
}

// ---------------------------------------------------------------------------------- mappers

internal fun AccountBindingEntity.toModel(): AccountBinding? {
    val status = BindingStatus.fromWire(status) ?: return null
    return AccountBinding(
        uid = uid, status = status, migrationId = migrationId, boundAt = boundAt,
        migrationStartedAt = migrationStartedAt, migrationCompletedAt = migrationCompletedAt, lastSyncAt = lastSyncAt,
        lastFailure = SyncFailure.fromWire(lastFailure), settingsFingerprint = settingsFingerprint, settingsRemoteMs = settingsRemoteMs,
        cursorMissionsMs = cursorMissionsMs, cursorSessionsMs = cursorSessionsMs, cursorTombstonesMs = cursorTombstonesMs,
        conflictsResolved = conflictsResolved,
    )
}

internal fun SyncOutboxEntity.toModel(): OutboxEntry? {
    val type = SyncEntityType.fromWire(entityType) ?: return null
    val op = SyncOperation.fromWire(operation) ?: return null
    return OutboxEntry(
        mutationId, accountUid, type, entityId, op, schemaVersion, localVersion, createdAt, retryCount,
        SyncFailure.fromWire(lastFailure), nextAttemptAt, if (state == RoomSyncLocalStore.STATE_PARKED) OutboxState.PARKED else OutboxState.PENDING,
    )
}

internal fun SyncEntityStateEntity.toModel(): EntityState? {
    val type = SyncEntityType.fromWire(entityType) ?: return null
    return EntityState(type, entityId, accountUid, remoteVersion, remoteStepIds.split('\n').filter { it.isNotEmpty() }, lastMutationId)
}

internal fun EntityState.toEntity() = SyncEntityStateEntity(entityType.wire, entityId, accountUid, remoteVersion, remoteStepIds.joinToString("\n"), lastMutationId)
