package app.focusmodeplus.core.domain.deeplink

import app.focusmodeplus.core.domain.reminder.ReminderKeys
import app.focusmodeplus.core.model.EntityIds

/** In-app destinations reachable from notifications and reminders. Navigation only, never actions. */
sealed interface DeepLinkTarget {
    data object Home : DeepLinkTarget
    data object ActiveSession : DeepLinkTarget
    /** Opens the Session with the end confirmation (safe end flow, never an instant end). */
    data object EndSession : DeepLinkTarget
    data object Missions : DeepLinkTarget
    data class Mission(val missionId: String) : DeepLinkTarget
    data class QuickFocus(val scheduleId: String? = null) : DeepLinkTarget
    data class Reminder(val key: String) : DeepLinkTarget
    data object History : DeepLinkTarget
    data class Result(val sessionId: String) : DeepLinkTarget
    data object PermissionCenter : DeepLinkTarget
}

/**
 * `focusmode://` links. URLs carry only opaque ids (never task text). Parsing is strict:
 * anything unknown, malformed or with an unsafe id falls back to Home and never crashes.
 */
object DeepLinks {
    const val SCHEME = "focusmode"
    private const val PREFIX = "$SCHEME://"

    fun build(target: DeepLinkTarget): String = PREFIX + when (target) {
        DeepLinkTarget.Home -> "home"
        DeepLinkTarget.ActiveSession -> "session"
        DeepLinkTarget.EndSession -> "session/end"
        DeepLinkTarget.Missions -> "missions"
        is DeepLinkTarget.Mission -> "mission/${target.missionId}"
        is DeepLinkTarget.QuickFocus -> if (target.scheduleId == null) "quickfocus" else "quickfocus?schedule=${target.scheduleId}"
        is DeepLinkTarget.Reminder -> "reminder/${target.key}"
        DeepLinkTarget.History -> "history"
        is DeepLinkTarget.Result -> "result/${target.sessionId}"
        DeepLinkTarget.PermissionCenter -> "settings/permissions"
    }

    fun parse(uri: String?): DeepLinkTarget {
        if (uri == null || uri.length > 400 || !uri.startsWith(PREFIX)) return DeepLinkTarget.Home
        val rest = uri.removePrefix(PREFIX)
        val path = rest.substringBefore('?').trimEnd('/')
        val query = rest.substringAfter('?', "")
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when {
            segments == listOf("home") || segments.isEmpty() -> DeepLinkTarget.Home
            segments == listOf("session") -> DeepLinkTarget.ActiveSession
            segments == listOf("session", "end") -> DeepLinkTarget.EndSession
            segments == listOf("missions") -> DeepLinkTarget.Missions
            segments.size == 2 && segments[0] == "mission" && EntityIds.isSafe(segments[1]) -> DeepLinkTarget.Mission(segments[1])
            segments == listOf("quickfocus") -> {
                val schedule = query.split('&').firstOrNull { it.startsWith("schedule=") }?.removePrefix("schedule=")
                DeepLinkTarget.QuickFocus(schedule?.takeIf(EntityIds::isSafe))
            }
            segments.size >= 2 && segments[0] == "reminder" -> {
                val key = path.removePrefix("reminder/")
                if (ReminderKeys.isValid(key)) DeepLinkTarget.Reminder(key) else DeepLinkTarget.Home
            }
            segments == listOf("history") -> DeepLinkTarget.History
            segments.size == 2 && segments[0] == "result" && EntityIds.isSafe(segments[1]) -> DeepLinkTarget.Result(segments[1])
            segments == listOf("settings", "permissions") -> DeepLinkTarget.PermissionCenter
            else -> DeepLinkTarget.Home
        }
    }
}
