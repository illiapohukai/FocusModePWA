package app.focusmodeplus.core.model

import java.time.Instant

enum class ReminderType { PREPARATION_START, MISSION_DEADLINE, SCHEDULED_FOCUS, DAILY_PLANNING }

/**
 * How a reminder is delivered.
 * - DEFERRABLE: WorkManager; may shift with Doze/battery policy (day-level reminders).
 * - INEXACT_ALARM: AlarmManager window; usually within minutes (scheduled Focus without exact access).
 * - EXACT_ALARM: only when the platform grants exact alarms (never requested by default).
 */
enum class ReminderPrecision { DEFERRABLE, INEXACT_ALARM, EXACT_ALARM }

/**
 * One planned reminder occurrence. [key] is stable and unique per event occurrence
 * (`type:source:date`), so scheduling the same event twice replaces instead of duplicating.
 */
data class Reminder(
    val key: String,
    val type: ReminderType,
    val sourceId: String,
    val triggerAt: Instant,
    val title: String,
    val body: String,
    val precision: ReminderPrecision,
    val deepLink: String,
    val deliveredAt: Instant? = null,
)
