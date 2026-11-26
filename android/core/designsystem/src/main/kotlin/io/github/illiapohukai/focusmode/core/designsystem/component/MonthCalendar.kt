package io.github.illiapohukai.focusmode.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CalendarMarkKind { DEADLINE, START, DONE, FOCUS }

/** An existing Mission date on a day (PWA `.hc-mark`), drawn in the Mission's identity tone. */
@Immutable data class CalendarMark(val kind: CalendarMarkKind, val color: Color)

/**
 * One day cell. [label] is the full spoken description (date, "Heute", every Mission date on it,
 * "nicht wählbar"); [enabled] decides whether the cell is interactive at all.
 */
@Immutable
data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val marks: List<CalendarMark> = emptyList(),
    val deadlineColor: Color? = null,
    val enabled: Boolean = true,
    val label: String = "",
)

/** The new Mission's chosen dates in the Creation planner (PWA `is-new-deadline`, `is-new-start`, range). */
@Immutable data class CalendarSelection(val deadline: LocalDate? = null, val start: LocalDate? = null)

val GermanMonthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/**
 * German, Monday-first month grid shared by the Home Mission calendar and the Mission Creation
 * planner (PWA `mission-calendar.js` markup). It only renders: the owning screen derives the
 * cells from the one calendar-event owner and decides what a tap means.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    days: List<CalendarDay>,
    today: LocalDate,
    focused: LocalDate?,
    onDay: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = true,
    selection: CalendarSelection = CalendarSelection(),
    subtitle: String? = null,
) {
    Column(modifier.fillMaxWidth().testTag("month_calendar")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    GermanMonthTitle.format(month).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                    style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                )
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 2) }
            }
            NavButton(FocusIcons.ChevronLeft, "Vorheriger Monat", previousEnabled, onPrevious, "calendar_prev")
            Spacer(Modifier.width(4.dp))
            NavButton(FocusIcons.ChevronRight, "Nächster Monat", true, onNext, "calendar_next")
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            WEEKDAYS.forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day -> DayCell(day, today, focused, selection, onDay, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    Box(
        Modifier.size(44.dp).clip(Radii.control).background(FocusPalette.SurfaceMuted.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) FocusPalette.Graphite else FocusPalette.GraphiteSoft.copy(alpha = 0.5f), modifier = Modifier.size(20.dp)) }
}

@Composable
private fun DayCell(day: CalendarDay, today: LocalDate, focused: LocalDate?, selection: CalendarSelection, onDay: (LocalDate) -> Unit, modifier: Modifier) {
    if (!day.inMonth) {
        Box(modifier.heightIn(min = 46.dp).clearAndSetSemantics { }, contentAlignment = Alignment.TopCenter) {
            Text("${day.date.dayOfMonth}", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft.copy(alpha = 0.35f), modifier = Modifier.padding(top = 9.dp))
        }
        return
    }
    val isToday = day.date == today
    val isNewDeadline = selection.deadline == day.date
    val isNewStart = selection.start != null && selection.start == day.date && selection.start != selection.deadline
    val inRange = selection.start != null && selection.deadline != null && day.date.isAfter(selection.start) && day.date.isBefore(selection.deadline)
    val isFocused = focused == day.date
    val past = day.date.isBefore(today)
    val numberColor = when {
        isNewDeadline -> Color.White
        !day.enabled && day.marks.isEmpty() -> FocusPalette.GraphiteSoft.copy(alpha = 0.45f)
        past -> FocusPalette.GraphiteSoft
        else -> FocusPalette.Graphite
    }
    Column(
        modifier
            .heightIn(min = 46.dp)
            .then(if (inRange) Modifier.background(FocusPalette.PurpleTint) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .then(if (isFocused && !isNewDeadline) Modifier.background(FocusPalette.SurfaceMuted) else Modifier)
            .clickable(enabled = day.enabled, role = Role.Button) { onDay(day.date) }
            .semantics {
                contentDescription = day.label
                selected = isNewDeadline || isNewStart || isFocused
                if (isToday) stateDescription = "Heute"
            }
            .testTag("calendar_day_${day.date}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .then(
                    when {
                        isNewDeadline -> Modifier.background(FocusPalette.Purple)
                        isNewStart -> Modifier.border(BorderStroke(2.dp, FocusPalette.Purple), CircleShape)
                        day.deadlineColor != null -> Modifier.border(BorderStroke(1.5.dp, day.deadlineColor), CircleShape)
                        isToday -> Modifier.border(BorderStroke(1.dp, FocusPalette.OutlineStrong), CircleShape)
                        else -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.date.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isToday || isNewDeadline) FontWeight.Black else FontWeight.SemiBold),
                color = numberColor,
            )
        }
        Row(Modifier.height(8.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            day.marks.distinct().take(3).forEach { mark ->
                val shape = if (mark.kind == CalendarMarkKind.DEADLINE) RoundedCornerShape(1.dp) else CircleShape
                Box(
                    Modifier.size(if (mark.kind == CalendarMarkKind.FOCUS) 4.dp else 5.dp).clip(shape)
                        .then(if (mark.kind == CalendarMarkKind.START) Modifier.border(BorderStroke(1.dp, mark.color), shape) else Modifier.background(mark.color)),
                )
            }
            if (day.marks.distinct().size > 3) Text("+${day.marks.distinct().size - 3}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.sp), color = FocusPalette.GraphiteSoft)
        }
    }
}
