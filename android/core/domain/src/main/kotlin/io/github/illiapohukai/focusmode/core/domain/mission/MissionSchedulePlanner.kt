package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.domain.ai.GenerationSchedule
import io.github.illiapohukai.focusmode.core.model.MissionSchedule
import io.github.illiapohukai.focusmode.core.model.ScheduleStartMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ScheduleNoticeLevel { ERROR, WARN, INFO }

data class ScheduleValidation(val valid: Boolean, val level: ScheduleNoticeLevel? = null, val message: String? = null)

/**
 * The one Zeitplan owner, a pure port of the PWA `mission-renderer.js` schedule functions. All
 * values are local calendar dates (no UTC conversion, so no day shift). `today` always comes from
 * the injected clock in the device zone.
 */
object MissionSchedulePlanner {

    /** PWA `getRecommendedStartMode`. */
    fun recommendedStartMode(deadline: LocalDate?, today: LocalDate): ScheduleStartMode {
        deadline ?: return ScheduleStartMode.IMMEDIATE
        val days = ChronoUnit.DAYS.between(today, deadline)
        return when {
            days <= 7 -> ScheduleStartMode.IMMEDIATE
            days <= 21 -> ScheduleStartMode.ONE_WEEK_BEFORE
            days <= 60 -> ScheduleStartMode.TWO_WEEKS_BEFORE
            else -> ScheduleStartMode.CUSTOM
        }
    }

    /** PWA `recomputePreparationStartDate`. */
    fun recompute(s: ScheduleDraft, today: LocalDate): ScheduleDraft {
        val deadline = s.deadline ?: return s.copy(preparationStart = null, startClamped = false)
        return when (s.startMode) {
            ScheduleStartMode.IMMEDIATE -> s.copy(preparationStart = today, startClamped = false)
            ScheduleStartMode.ONE_WEEK_BEFORE, ScheduleStartMode.TWO_WEEKS_BEFORE -> {
                val candidate = deadline.minusDays(if (s.startMode == ScheduleStartMode.ONE_WEEK_BEFORE) 7 else 14)
                if (candidate.isBefore(today)) s.copy(preparationStart = today, startClamped = true)
                else s.copy(preparationStart = candidate, startClamped = false)
            }
            ScheduleStartMode.CUSTOM -> {
                if (s.preparationStart != null) s.copy(startClamped = false)
                else {
                    val fallback = deadline.minusDays(14)
                    s.copy(preparationStart = if (!fallback.isBefore(today)) fallback else today, startClamped = false)
                }
            }
        }
    }

    /** PWA `previewStartDateForMode`: what each start option would produce (display only). */
    fun previewStart(s: ScheduleDraft, mode: ScheduleStartMode, today: LocalDate): LocalDate? {
        val deadline = s.deadline ?: return null
        return when (mode) {
            ScheduleStartMode.IMMEDIATE -> today
            ScheduleStartMode.ONE_WEEK_BEFORE, ScheduleStartMode.TWO_WEEKS_BEFORE -> {
                val c = deadline.minusDays(if (mode == ScheduleStartMode.ONE_WEEK_BEFORE) 7 else 14)
                if (c.isBefore(today)) today else c
            }
            ScheduleStartMode.CUSTOM -> if (s.startMode == ScheduleStartMode.CUSTOM) s.preparationStart else null
        }
    }

    /** PWA `selectDeadlineDate`: a past date is refused; an unchosen start mode follows the recommendation. */
    fun selectDeadline(s: ScheduleDraft, date: LocalDate, today: LocalDate): ScheduleDraft? {
        if (date.isBefore(today)) return null
        val recommended = recommendedStartMode(date, today)
        val mode = if (s.startModeChosen) s.startMode else if (recommended == ScheduleStartMode.CUSTOM) ScheduleStartMode.TWO_WEEKS_BEFORE else recommended
        // A custom start after the new deadline would be invalid; it is re-derived.
        val start = if (mode == ScheduleStartMode.CUSTOM && s.preparationStart != null && s.preparationStart.isAfter(date)) null else s.preparationStart
        return recompute(s.copy(deadline = date, startMode = mode, preparationStart = start), today)
    }

    /** PWA `selectMissionPreparationStartDate` (custom start chosen in the inline calendar). */
    fun selectPreparationStart(s: ScheduleDraft, date: LocalDate, today: LocalDate): ScheduleDraft? {
        if (date.isBefore(today)) return null
        if (s.deadline == null || date.isAfter(s.deadline)) return null
        return s.copy(startModeChosen = true, startMode = ScheduleStartMode.CUSTOM, preparationStart = date, startClamped = false)
    }

    /** PWA `selectMissionStartMode`. */
    fun selectStartMode(s: ScheduleDraft, mode: ScheduleStartMode, today: LocalDate): ScheduleDraft {
        val next = s.copy(startModeChosen = true, startMode = mode)
        return if (mode == ScheduleStartMode.CUSTOM && s.preparationStart != null) next else recompute(next, today)
    }

    /** PWA `clearMissionDeadline`. Weekday and duration preferences are kept. */
    fun clearDeadline(s: ScheduleDraft): ScheduleDraft =
        s.copy(deadline = null, preparationStart = null, startMode = ScheduleStartMode.IMMEDIATE, startModeChosen = false, startClamped = false)

    fun toggleWeekday(s: ScheduleDraft, day: DayOfWeek): ScheduleDraft =
        s.copy(preferredWeekdays = if (day in s.preferredWeekdays) s.preferredWeekdays - day else s.preferredWeekdays + day)

    val WEEKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun selectSessionMinutes(s: ScheduleDraft, minutes: Int?): ScheduleDraft =
        s.copy(preferredSessionMinutes = minutes?.takeIf { it in MissionSchedule.SESSION_MINUTES })

    /** PWA `hasMatchingWeekdayInRange` (looks at most one week ahead). */
    fun hasMatchingWeekday(start: LocalDate, end: LocalDate, weekdays: Set<DayOfWeek>): Boolean {
        if (weekdays.isEmpty()) return true
        val span = ChronoUnit.DAYS.between(start, end)
        if (span < 0) return false
        for (i in 0..minOf(span, 7)) if (start.plusDays(i).dayOfWeek in weekdays) return true
        return false
    }

    /** PWA `validateMissionScheduleState` with its German messages. */
    fun validate(s: ScheduleDraft, today: LocalDate): ScheduleValidation {
        val deadline = s.deadline ?: return ScheduleValidation(true)
        val daysToDeadline = ChronoUnit.DAYS.between(today, deadline)
        if (daysToDeadline < 0) return ScheduleValidation(false, ScheduleNoticeLevel.ERROR, "Das Prüfungs- oder Abgabedatum darf nicht in der Vergangenheit liegen.")
        val start = s.preparationStart ?: today
        val prepSpan = ChronoUnit.DAYS.between(start, deadline)
        if (prepSpan < 0) return ScheduleValidation(false, ScheduleNoticeLevel.ERROR, "Der Vorbereitungsstart muss vor der Prüfung oder Abgabe liegen.")
        if (start.isBefore(today)) return ScheduleValidation(false, ScheduleNoticeLevel.ERROR, "Der Vorbereitungsstart darf nicht in der Vergangenheit liegen.")
        if (!hasMatchingWeekday(start, deadline, s.preferredWeekdays)) {
            return ScheduleValidation(false, ScheduleNoticeLevel.ERROR, "Im gewählten Zeitraum liegt kein ausgewählter Lerntag.")
        }
        if (daysToDeadline == 0L) return ScheduleValidation(true, ScheduleNoticeLevel.INFO, "Der Zeitplan beginnt heute.")
        if (s.startClamped) return ScheduleValidation(true, ScheduleNoticeLevel.INFO, "Der Zeitplan beginnt heute.")
        if (prepSpan <= 2) return ScheduleValidation(true, ScheduleNoticeLevel.WARN, "Sehr wenig Vorbereitungszeit.")
        return ScheduleValidation(true)
    }

    /** PWA `formatWeekdayList` / `briefWeekdays`. */
    fun weekdayLabel(days: Set<DayOfWeek>): String = when {
        days.isEmpty() -> "Flexibel"
        days == WEEKDAYS -> "Unter der Woche"
        days == WEEKEND -> "Wochenende"
        else -> days.sortedBy { it.value }.joinToString(", ") { SHORT[it.value - 1] }
    }

    val SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    /** PWA `buildMissionSchedulePayload`: null without a deadline (the AI then plans freely). */
    fun payload(s: ScheduleDraft, today: LocalDate, zone: ZoneId): GenerationSchedule? {
        val deadline = s.deadline ?: return null
        return GenerationSchedule(
            currentLocalDate = today,
            deadlineDate = deadline,
            preparationStartDate = s.preparationStart ?: today,
            startMode = s.startMode.wire,
            timezone = zone.id,
            preferredWeekdays = s.preferredWeekdays.map { it.value }.sorted(),
            preferredSessionMinutes = s.preferredSessionMinutes,
        )
    }

    /** The schedule stored with an accepted Mission. */
    fun toMissionSchedule(s: ScheduleDraft): MissionSchedule = MissionSchedule(
        startMode = if (s.deadline != null) s.startMode else null,
        preferredWeekdays = s.preferredWeekdays,
        preferredSessionMinutes = s.preferredSessionMinutes,
    )
}
