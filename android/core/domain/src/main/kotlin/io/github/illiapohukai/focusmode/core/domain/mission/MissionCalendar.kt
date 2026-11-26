package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.domain.progress.ProgressCalculator
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import io.github.illiapohukai.focusmode.core.model.MissionType
import io.github.illiapohukai.focusmode.core.model.SessionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * - DEADLINE: the stored deadline date (or an explicit date inside the PWA deadline text).
 * - START: the stored preparation start; without one, the day the Mission was created (PWA).
 * - DONE: the day a Mission was completed.
 * - FOCUS: days with counted, finished Sessions of that Mission (from History).
 * Preferred weekdays never produce events: nothing is scheduled from them.
 */
enum class CalendarEventKind(val order: Int) { DEADLINE(0), DONE(1), START(2), FOCUS(3) }

data class CalendarEvent(
    val missionId: MissionId,
    val title: String,
    val theme: MissionTheme,
    val missionType: MissionType,
    val status: MissionStatus,
    val kind: CalendarEventKind,
    val date: LocalDate,
    /** START: true when it is a stored preparation start (not the creation day). */
    val preparation: Boolean = false,
    val sessions: Int = 0,
    val minutes: Int = 0,
)

/** One month cell, Monday first. Leading and trailing days of the neighbouring months are outside. */
data class CalendarCell(val date: LocalDate, val inMonth: Boolean)

/** A rendered day: its Mission dates, whether it can be tapped, and its full spoken label. */
data class CalendarDayInfo(val date: LocalDate, val inMonth: Boolean, val events: List<CalendarEvent>, val enabled: Boolean, val label: String)

/**
 * The one Mission calendar owner (PWA `mission-calendar.js`). Read-only derivation from Missions
 * and History; it stores nothing. Home and the Mission Creation planner both use it.
 */
object MissionCalendar {

    fun events(missions: List<Mission>, results: List<FocusSessionResult>, themes: Map<MissionId, MissionTheme>, zone: ZoneId): List<CalendarEvent> {
        val visible = missions.filter { it.status != MissionStatus.ARCHIVED }
        val byId = visible.associateBy { it.id }
        val out = ArrayList<CalendarEvent>()
        visible.forEach { m ->
            val theme = themes[m.id] ?: MissionTheme.AUBERGINE
            fun event(kind: CalendarEventKind, date: LocalDate, preparation: Boolean = false) =
                CalendarEvent(m.id, m.title, theme, m.type, m.status, kind, date, preparation)
            deadlineOf(m)?.let { out += event(CalendarEventKind.DEADLINE, it) }
            val prep = m.preparationStartDate
            if (prep != null) out += event(CalendarEventKind.START, prep, preparation = true)
            else out += event(CalendarEventKind.START, m.createdAt.atZone(zone).toLocalDate())
            if (m.status == MissionStatus.COMPLETED) m.completedAt?.let { out += event(CalendarEventKind.DONE, it.atZone(zone).toLocalDate()) }
        }
        results.asSequence()
            .filter { it.type == SessionType.MISSION && ProgressCalculator.isCounted(it) }
            .mapNotNull { r -> r.missionLink?.missionId?.let { id -> byId[id]?.let { m -> m to r } } }
            .groupBy { (m, r) -> m.id to r.localDate }
            .forEach { (key, list) ->
                val m = list.first().first
                out += CalendarEvent(
                    m.id, m.title, themes[m.id] ?: MissionTheme.AUBERGINE, m.type, m.status, CalendarEventKind.FOCUS, key.second,
                    sessions = list.size, minutes = list.sumOf { it.second.actualMinutes.coerceAtLeast(0) },
                )
            }
        return out
    }

    fun byDate(events: List<CalendarEvent>): Map<LocalDate, List<CalendarEvent>> =
        events.groupBy { it.date }.mapValues { (_, list) -> list.sortedWith(compareBy<CalendarEvent>({ it.kind.order }, { it.title })) }

    /** Existing commitments shown in the Mission Creation planner (PWA `plannerEvents`). */
    fun plannerEvents(events: List<CalendarEvent>): Map<LocalDate, List<CalendarEvent>> =
        byDate(events.filter { (it.status == MissionStatus.ACTIVE && (it.kind == CalendarEventKind.DEADLINE || it.kind == CalendarEventKind.START)) || it.kind == CalendarEventKind.DONE })

    /** PWA `nextDeadline`: the nearest active deadline today or later. */
    fun nextDeadline(events: List<CalendarEvent>, today: LocalDate): CalendarEvent? =
        events.filter { it.kind == CalendarEventKind.DEADLINE && it.status == MissionStatus.ACTIVE && !it.date.isBefore(today) }.minByOrNull { it.date }

    /** Whole weeks, Monday first (PWA `monthCells`). */
    fun monthCells(month: YearMonth): List<CalendarCell> {
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val total = ((lead + month.lengthOfMonth() + 6) / 7) * 7
        val start = first.minusDays(lead.toLong())
        return (0 until total).map { i -> start.plusDays(i.toLong()).let { CalendarCell(it, YearMonth.from(it) == month) } }
    }

    private val LONG_DATE = java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", java.util.Locale.GERMAN)
    private val DAY_TITLE = java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM", java.util.Locale.GERMAN)
    private val SHORT_DATE = java.time.format.DateTimeFormatter.ofPattern("d. MMM", java.util.Locale.GERMAN)

    /** "Heute · 25. November" or "Freitag, 27. November" (PWA detail title). */
    fun dayTitle(date: LocalDate, today: LocalDate): String =
        if (date == today) "Heute · " + java.time.format.DateTimeFormatter.ofPattern("d. MMMM", java.util.Locale.GERMAN).format(date) else DAY_TITLE.format(date)

    fun shortDate(date: LocalDate): String = SHORT_DATE.format(date)

    /**
     * Cells of [month] with their events and PWA `cellLabel` descriptions. [extra] adds the new
     * Mission's own dates in the planner; [selectable] decides interactivity (days with events are
     * always focusable so their details can be read).
     */
    fun monthDays(
        month: YearMonth,
        byDate: Map<LocalDate, List<CalendarEvent>>,
        today: LocalDate,
        selectable: (LocalDate) -> Boolean = { true },
        extra: (LocalDate) -> List<String> = { emptyList() },
    ): List<CalendarDayInfo> = monthCells(month).map { cell ->
        val events = if (cell.inMonth) byDate[cell.date].orEmpty() else emptyList()
        val canSelect = selectable(cell.date)
        val parts = mutableListOf(LONG_DATE.format(cell.date))
        if (cell.date == today) parts += "Heute"
        parts += extra(cell.date)
        events.forEach { parts += chip(it) + ": " + it.title }
        if (!canSelect && events.isEmpty()) parts += "nicht wählbar"
        CalendarDayInfo(cell.date, cell.inMonth, events, cell.inMonth && (canSelect || events.isNotEmpty()), parts.joinToString(", "))
    }

    /** PWA `selectableFor`: never a past day; a preparation start never after the deadline. */
    fun selectable(date: LocalDate, today: LocalDate, choosingStart: Boolean, deadline: LocalDate?): Boolean {
        if (date.isBefore(today)) return false
        if (choosingStart) return deadline != null && !date.isAfter(deadline)
        return true
    }

    /** PWA `relative`. */
    fun relative(date: LocalDate, today: LocalDate): String {
        val d = ChronoUnit.DAYS.between(today, date)
        return when {
            d == 0L -> "heute"
            d == 1L -> "morgen"
            d == -1L -> "gestern"
            d > 0 -> "in $d Tagen"
            else -> "vor ${-d} Tagen"
        }
    }

    /** PWA `deadlineLabel`. */
    fun deadlineLabel(type: MissionType): String = when (type) {
        MissionType.EXAM -> "Prüfung"
        MissionType.ASSIGNMENT -> "Abgabe"
        else -> "Frist"
    }

    fun chip(e: CalendarEvent): String = when (e.kind) {
        CalendarEventKind.DEADLINE -> deadlineLabel(e.missionType)
        CalendarEventKind.START -> "Start"
        CalendarEventKind.DONE -> "Abgeschlossen"
        CalendarEventKind.FOCUS -> "Fokus"
    }

    fun detail(e: CalendarEvent, today: LocalDate): String = when (e.kind) {
        CalendarEventKind.DEADLINE -> relative(e.date, today)
        CalendarEventKind.START -> if (e.preparation) "Vorbereitung beginnt" else "Mission erstellt"
        CalendarEventKind.DONE -> "Mission abgeschlossen"
        CalendarEventKind.FOCUS -> (if (e.sessions == 1) "1 Sitzung" else "${e.sessions} Sitzungen") + " · ${e.minutes} Min."
    }

    fun deadlineOf(m: Mission): LocalDate? = m.deadlineDate ?: parseDeadlineText(m.deadlineText)

    private val MONTHS = listOf("januar", "februar", "märz", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember")
    private val MONTHS_EN = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val ALIASES = mapOf(
        "maerz" to 2, "marz" to 2, "jan" to 0, "feb" to 1, "mär" to 2, "mrz" to 2, "apr" to 3, "jun" to 5, "jul" to 6, "aug" to 7,
        "sep" to 8, "sept" to 8, "okt" to 9, "oct" to 9, "nov" to 10, "dez" to 11, "dec" to 11,
    )

    private fun monthIndex(word: String): Int {
        val w = word.lowercase().removeSuffix(".")
        return MONTHS.indexOf(w).takeIf { it >= 0 } ?: MONTHS_EN.indexOf(w).takeIf { it >= 0 } ?: ALIASES[w] ?: -1
    }

    private fun valid(y: Int, m0: Int, d: Int): LocalDate? =
        if (y in 1900..2200 && m0 in 0..11 && d in 1..31) runCatching { LocalDate.of(y, m0 + 1, d) }.getOrNull() else null

    /** PWA `parseDeadlineText`: only one explicit, complete calendar date; relative text is not placed. */
    fun parseDeadlineText(text: String?): LocalDate? {
        val s = text?.trim().orEmpty()
        if (s.isEmpty()) return null
        Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b").find(s)?.let { m -> return valid(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt()) }
        Regex("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}|\\d{2})\\b").find(s)?.let { m ->
            val y = m.groupValues[3].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            return valid(y, m.groupValues[2].toInt() - 1, m.groupValues[1].toInt())
        }
        Regex("\\b(\\d{1,2})\\.?\\s+([A-Za-zÄÖÜäöü]{3,10}\\.?)\\s+(\\d{4})\\b").find(s)?.let { m ->
            val mi = monthIndex(m.groupValues[2])
            if (mi >= 0) return valid(m.groupValues[3].toInt(), mi, m.groupValues[1].toInt())
        }
        Regex("\\b([A-Za-z]{3,10})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})\\b").find(s)?.let { m ->
            val mi = monthIndex(m.groupValues[1])
            if (mi >= 0) return valid(m.groupValues[3].toInt(), mi, m.groupValues[2].toInt())
        }
        return null
    }
}
