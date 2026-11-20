package io.github.illiapohukai.focusmode.core.domain.session

import io.github.illiapohukai.focusmode.core.model.EndNote
import io.github.illiapohukai.focusmode.core.model.ProtectionMode

/**
 * End barrier of a Session. Protected Focus asks for a short pause and a reason before an early
 * end, but the emergency exit is always available immediately. Nothing traps the user.
 */
data class EndBarrier(
    val delaySeconds: Int,
    val reasonRequired: Boolean,
    val reasons: List<EndNote>,
    val emergencyExitAvailable: Boolean = true,
) {
    companion object {
        const val PROTECTED_DELAY_SECONDS = 10

        fun forMode(mode: ProtectionMode): EndBarrier = when (mode) {
            ProtectionMode.STANDARD -> EndBarrier(0, reasonRequired = false, reasons = emptyList())
            ProtectionMode.PROTECTED, ProtectionMode.STRICT_MANAGED -> EndBarrier(
                delaySeconds = PROTECTED_DELAY_SECONDS,
                reasonRequired = true,
                reasons = listOf(EndNote.TASK_DONE, EndNote.NEED_BREAK, EndNote.DISTRACTED, EndNote.OTHER),
            )
        }
    }

    fun canConfirm(secondsWaited: Int, note: EndNote?): Boolean =
        secondsWaited >= delaySeconds && (!reasonRequired || note != null)
}
