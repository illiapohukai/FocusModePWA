package app.focusmodeplus.core.domain.history

import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionId
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rows, sections and labels shared by History and Home Activity (one formatting owner). */
object HistoryFormat {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

    fun time(result: FocusSessionResult, zone: ZoneId): String = TIME.format(ZonedDateTime.ofInstant(result.startedAt, zone))

    fun timeRange(result: FocusSessionResult, zone: ZoneId): String =
        TIME.format(ZonedDateTime.ofInstant(result.startedAt, zone)) + "–" + TIME.format(ZonedDateTime.ofInstant(result.finishedAt, zone))

    /** PWA `activityDayLabelV1716` / History day headers: Heute, Gestern, otherwise "Montag, 3. März". */
    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> FocusInsights.dayLong(date)
    }

    /** PWA `activityDayLabelV1716` (Home Activity): Heute, Gestern, otherwise "Mo., 3. März". */
    fun activityDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> FocusInsights.dayShort(date)
    }

    /** Home Activity context: the Mission title (PWA `mission?.title || 'Focus Mission'`) or "Quick Focus". */
    fun activityContext(result: FocusSessionResult, missions: Map<MissionId, Mission>): String {
        val link = result.missionLink ?: return "Quick Focus"
        return missions[link.missionId]?.title ?: "Focus Mission"
    }

    /** "< 1 Min." for a Session below one minute (PWA `createHistoryCard`). */
    fun minutes(result: FocusSessionResult): String = if (result.actualMinutes <= 0) "< 1 Min." else HistoryQueries.formatMinutes(result.actualMinutes)

    fun missionContext(result: FocusSessionResult, missions: Map<MissionId, Mission>): String? {
        val link = result.missionLink ?: return null
        val mission = missions[link.missionId] ?: return "Focus Mission"
        val index = mission.steps.indexOfFirst { it.id == link.stepId }
        return if (index < 0) mission.title else mission.title + " · Schritt " + (index + 1).toString().padStart(2, '0')
    }
}

