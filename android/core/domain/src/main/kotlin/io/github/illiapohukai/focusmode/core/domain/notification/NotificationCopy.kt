package io.github.illiapohukai.focusmode.core.domain.notification

import io.github.illiapohukai.focusmode.core.model.NotificationDetailLevel

/**
 * Text of one notification. [publicTitle]/[publicBody] replace the content on a lock screen
 * that hides sensitive content; null public text means "do not show on the lock screen".
 */
data class NotificationText(
    val title: String,
    val body: String,
    val publicTitle: String? = title,
    val publicBody: String? = body,
)

/** Concise German Focus Mode+ notification templates. No motivational filler, no app names. */
object NotificationCopy {

    fun minutesPhrase(minutes: Int): String = if (minutes == 1) "1 Minute" else "$minutes Minuten"

    fun activeSession(remainingMinutes: Int, task: String, detail: NotificationDetailLevel): NotificationText {
        val remaining = "Noch ${minutesPhrase(remainingMinutes.coerceAtLeast(1))}"
        return when (detail) {
            NotificationDetailLevel.FULL_TASK -> NotificationText("Fokus läuft", "$remaining · ${task.take(80)}", "Fokus läuft", remaining)
            NotificationDetailLevel.GENERIC -> NotificationText("Fokus läuft", remaining)
            NotificationDetailLevel.HIDDEN -> NotificationText("Focus Mode+", "Sitzung aktiv", null, null)
        }
    }

    fun sessionFinished(minutes: Int, task: String, detail: NotificationDetailLevel): NotificationText {
        val focused = "${minutesPhrase(minutes)} fokussiert"
        return when (detail) {
            NotificationDetailLevel.FULL_TASK -> NotificationText("Fokus abgeschlossen", "$focused · ${task.take(80)}", "Fokus abgeschlossen", focused)
            NotificationDetailLevel.GENERIC -> NotificationText("Fokus abgeschlossen", focused)
            NotificationDetailLevel.HIDDEN -> NotificationText("Focus Mode+", "Sitzung beendet", null, null)
        }
    }

    fun sessionFrozen(): NotificationText = NotificationText(
        "Fokus-Sitzung angehalten",
        "Dein Gerät wurde neu gestartet. Setze die Sitzung fort oder speichere deine Fokuszeit.",
    )

    fun distractionHint(remainingMinutes: Int): NotificationText = NotificationText(
        "Dein Fokus läuft noch",
        "Noch ${minutesPhrase(remainingMinutes.coerceAtLeast(1))} – zurück zu deiner Aufgabe?",
    )

    fun preparationStart(missionTitle: String): NotificationText = NotificationText(
        "Deine Vorbereitung beginnt heute",
        "${missionTitle.take(60)} · Der erste Schritt ist bereit.",
        "Deine Vorbereitung beginnt heute",
        "Der erste Schritt ist bereit.",
    )

    fun deadline(days: Int, missionTitle: String, openSteps: Int): NotificationText {
        val title = if (days == 1) "Deadline morgen" else "Deadline in $days Tagen"
        val steps = if (openSteps == 1) "ist noch 1 Schritt offen" else "sind noch $openSteps Schritte offen"
        return NotificationText(title, "Für „${missionTitle.take(60)}“ $steps.", title, "Für eine Mission $steps.")
    }

    fun scheduledFocus(minutes: Int, label: String): NotificationText = NotificationText(
        "Bereit für deinen Fokus?",
        "${minutesPhrase(minutes)} · ${label.take(60)}",
        "Bereit für deinen Fokus?",
        minutesPhrase(minutes),
    )

    fun dailyPlanning(): NotificationText = NotificationText(
        "Zeit für deinen Plan",
        "Lege fest, woran du als Nächstes fokussiert arbeitest.",
    )
}
