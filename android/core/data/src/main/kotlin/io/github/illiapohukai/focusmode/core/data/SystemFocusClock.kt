package io.github.illiapohukai.focusmode.core.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Platform clock: wall time, `elapsedRealtime` (monotonic, includes sleep) and the boot counter. */
@Singleton
class SystemFocusClock @Inject constructor(@ApplicationContext private val context: Context) : FocusClock {
    override fun now(): Instant = Instant.now()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun bootCount(): Int? =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
