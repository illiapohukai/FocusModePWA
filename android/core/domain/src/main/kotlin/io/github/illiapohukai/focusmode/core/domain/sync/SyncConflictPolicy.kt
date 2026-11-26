package io.github.illiapohukai.focusmode.core.domain.sync

import io.github.illiapohukai.focusmode.core.domain.repository.MissionProgressEntry
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStep
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus

/**
 * Deterministic conflict rules (v21, see `android/docs/sync-conflict-policy-v21.md`). Pure: the
 * same inputs always produce the same output on every device.
 *
 * - Missions: last write wins by `updatedAt` (ties: the larger device mutation id), but a
 *   completed step is never undone or lost (a step the server holds as completed keeps the
 *   server's copy), and progress entries are a union by Session id.
 *   Concurrent structural edits (both sides changed the step set) are irreconcilable: the newer
 *   plan wins and a conflict record is kept.
 * - Completed Sessions: append-only by Session id (see [SessionPolicy]).
 * - Settings: last write wins by server arrival (the engine uploads a local change after
 *   downloading; the later write is the one the server keeps).
 * - Deletion: a tombstone wins over any concurrent edit; a deleted Mission is never resurrected.
 * - Active Session: never synchronized; it belongs to the device that started it.
 */
object SyncConflictPolicy {

    data class MissionMerge(val mission: Mission, val progress: List<MissionProgressEntry>, val conflict: Boolean, val remoteWins: Boolean)

    /**
     * Merges a local Mission that has an unconfirmed local change with a remote version that
     * changed since the last confirmed base.
     */
    fun mergeMission(
        local: MissionSnapshot,
        localMutationId: String?,
        remote: MissionSnapshot,
        remoteMutationId: String?,
    ): MissionMerge {
        val l = local.mission
        val r = remote.mission
        val remoteWins = when {
            r.updatedAt.isAfter(l.updatedAt) -> true
            l.updatedAt.isAfter(r.updatedAt) -> false
            else -> (remoteMutationId ?: "") > (localMutationId ?: "")
        }
        val winner = if (remoteWins) r else l
        val loser = if (remoteWins) l else r
        val structural = l.steps.map { it.id }.toSet() != r.steps.map { it.id }.toSet()
        // A step the server already holds as completed is immutable there: its copy stands.
        val serverCompleted = r.steps.filter { it.status == MissionStepStatus.COMPLETED }.associateBy { it.id }
        val steps = mergeSteps(winner, loser).map { s -> serverCompleted[s.id]?.copy(position = s.position) ?: s }
        val progress = (local.progress + remote.progress).distinctBy { it.sessionId }.sortedWith(compareBy({ it.appliedAt }, { it.sessionId.value }))
        val merged = normalize(winner.copy(steps = steps, updatedAt = maxOf(l.updatedAt, r.updatedAt)))
        return MissionMerge(merged, progress, conflict = structural, remoteWins = remoteWins)
    }

    /**
     * Completed steps from both sides survive (completion wins, earliest completion time), then
     * the winner's open steps in its order. Positions are renumbered so they stay unique.
     */
    fun mergeSteps(winner: Mission, loser: Mission): List<MissionStep> {
        val loserCompleted = loser.steps.filter { it.status == MissionStepStatus.COMPLETED }.associateBy { it.id }
        val fromWinner = winner.steps.sortedBy { it.position }.map { s ->
            val other = loserCompleted[s.id]
            when {
                other == null -> s
                s.status == MissionStepStatus.COMPLETED -> s.copy(completedAt = listOfNotNull(s.completedAt, other.completedAt).minOrNull())
                else -> other.copy(position = s.position)
            }
        }
        val missing = loserCompleted.values.filter { c -> fromWinner.none { it.id == c.id } }
        val completed = (fromWinner.filter { it.status == MissionStepStatus.COMPLETED } + missing)
            .sortedWith(compareBy<MissionStep>({ it.position }, { it.completedAt }, { it.id.value }))
        val open = fromWinner.filter { it.status != MissionStepStatus.COMPLETED }
        return (completed + open).mapIndexed { i, s -> s.copy(position = i, missionId = winner.id) }
    }

    /** Keeps the current step and status consistent with the merged step list. */
    fun normalize(m: Mission): Mission {
        val ordered = m.steps.sortedBy { it.position }
        val open = ordered.filter { it.status != MissionStepStatus.COMPLETED }
        if (m.status != MissionStatus.ACTIVE) {
            // Archived or completed: statuses stay as they are (MissionRules.archive keeps them);
            // the remembered step must still be an open one.
            return m.copy(steps = ordered, archivedStepId = m.archivedStepId?.takeIf { id -> open.any { it.id == id } })
        }
        if (ordered.isNotEmpty() && open.isEmpty()) {
            return m.copy(
                steps = ordered, status = MissionStatus.COMPLETED, currentStepId = null,
                completedAt = m.completedAt ?: ordered.mapNotNull { it.completedAt }.maxOrNull() ?: m.updatedAt,
            )
        }
        val current = m.currentStepId?.takeIf { id -> open.any { it.id == id } } ?: open.firstOrNull()?.id
        val steps = ordered.map { s ->
            when {
                s.status == MissionStepStatus.COMPLETED -> s
                s.id == current -> s.copy(status = MissionStepStatus.ACTIVE)
                else -> s.copy(status = MissionStepStatus.PENDING)
            }
        }
        return m.copy(steps = steps, currentStepId = current)
    }

    /** The two-active rule after a merge: the most recently updated active Missions stay active. */
    fun missionsToArchive(active: List<Mission>, maxActive: Int = Mission.MAX_ACTIVE): List<Mission> =
        active.sortedWith(compareByDescending<Mission> { it.updatedAt }.thenBy { it.id.value }).drop(maxActive)

    /** Completed Sessions are append-only; the only accepted change is a review arriving for a Session still awaiting it locally. */
    object SessionPolicy {
        fun acceptsRemoteReview(localAwaitingReview: Boolean): Boolean = localAwaitingReview
    }
}
