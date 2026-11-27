package app.focusmodeplus.core.domain.deeplink

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinksTest {
    @Test fun roundTripsEveryTarget() {
        listOf(
            DeepLinkTarget.Home, DeepLinkTarget.ActiveSession, DeepLinkTarget.EndSession, DeepLinkTarget.Missions,
            DeepLinkTarget.Mission("mission_abc-1"), DeepLinkTarget.QuickFocus(), DeepLinkTarget.QuickFocus("schedule_x"),
            DeepLinkTarget.Reminder("scheduled_focus:schedule_x:2026-11-19"), DeepLinkTarget.History, DeepLinkTarget.PermissionCenter, DeepLinkTarget.Result("session_abc"),
        ).forEach { assertEquals(it, DeepLinks.parse(DeepLinks.build(it))) }
    }

    @Test fun unknownOrHostileLinksFallBackToHome() {
        listOf(
            null, "", "https://evil.example/mission/x", "focusmode://mission/", "focusmode://mission/../../etc",
            "focusmode://mission/a%2Fb", "focusmode://mission/x/y", "focusmode://unknown", "focusmode://reminder/drop table",
            "focusmode://" + "a".repeat(500), "intent://session#Intent;end",
        ).forEach { assertEquals("input=$it", DeepLinkTarget.Home, DeepLinks.parse(it)) }
        assertEquals(DeepLinkTarget.QuickFocus(null), DeepLinks.parse("focusmode://quickfocus?schedule=<script>"))
    }
}
