package app.focusmodeplus.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Testable time source. Wall time for display/History, the monotonic `elapsedRealtime` clock for
 * measuring a running Session, and the boot counter to detect that the monotonic base was reset.
 */
interface FocusClock {
    fun now(): Instant
    /** Monotonic milliseconds since boot, including deep sleep (`SystemClock.elapsedRealtime`). */
    fun elapsedRealtimeMs(): Long
    /** Platform boot counter (`Settings.Global.BOOT_COUNT`), or null when unavailable. */
    fun bootCount(): Int?
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}
