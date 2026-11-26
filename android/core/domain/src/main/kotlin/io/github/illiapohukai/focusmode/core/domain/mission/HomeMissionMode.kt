package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.SessionType

/** The four Home Mission states (PWA v17.16 Home + v11.6 carousel). */
enum class HomeMissionMode { FIRST_TIME, RETURNING_EMPTY, ONE, TWO }

/**
 * PWA `resolveHomeEmptyStateV1716`: the large onboarding card is only for a user who has never
 * created a Mission. Any stored Mission (any status) or a Mission Session in History marks the
 * user as returning; the mark is kept, so completing, archiving or deleting the last Mission never
 * brings the onboarding card back.
 */
object HomeMissionClassifier {
    fun hasMissionEvidence(missions: List<Mission>, results: List<FocusSessionResult>): Boolean =
        missions.isNotEmpty() || results.any { it.type == SessionType.MISSION }

    fun classify(missions: List<Mission>, onboardingDone: Boolean, evidence: Boolean): HomeMissionMode {
        val active = missions.count { it.status == MissionStatus.ACTIVE }
        return when {
            active >= 2 -> HomeMissionMode.TWO
            active == 1 -> HomeMissionMode.ONE
            onboardingDone || evidence -> HomeMissionMode.RETURNING_EMPTY
            else -> HomeMissionMode.FIRST_TIME
        }
    }

    /** True when the returning mark must be persisted now (evidence exists, mark missing). */
    fun shouldMarkReturning(onboardingDone: Boolean, evidence: Boolean): Boolean = !onboardingDone && evidence
}
