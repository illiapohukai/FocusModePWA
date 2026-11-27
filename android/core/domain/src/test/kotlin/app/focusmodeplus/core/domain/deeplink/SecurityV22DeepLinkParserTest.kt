package app.focusmodeplus.core.domain.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * v22 adversarial deep-link parsing. Links only ever navigate: no target can change the account,
 * delete data, finalize a Session or skip a confirmation, and an id is opaque (it is looked up,
 * never used as a path or query). Anything unexpected falls back to Home and never throws.
 */
class SecurityV22DeepLinkParserTest {
    private fun home(link: String?) = assertEquals("input=$link", DeepLinkTarget.Home, DeepLinks.parse(link))

    @Test fun unknownRoutesMissingAndMalformedIdsGoHome() {
        listOf(
            "focusmode://", "focusmode://admin", "focusmode://account/delete", "focusmode://account/switch?uid=evil",
            "focusmode://settings/data/delete", "focusmode://session/finalize", "focusmode://session/end/now", "focusmode://result/",
            "focusmode://mission", "focusmode://mission//", "focusmode://mission/ ", "focusmode://mission/a", "focusmode://mission/a b",
            "focusmode://mission/<img>", "focusmode://mission/%00", "focusmode://mission/\u0000x", "focusmode://result/x?y",
        ).forEach(::home)
    }

    @Test fun encodedSeparatorsAndTraversalNeverBecomeAPath() {
        listOf(
            "focusmode://mission/..%2F..%2Fdatabases", "focusmode://mission/%2e%2e%2f", "focusmode://mission/..\\..\\x",
            "focusmode://result/../../shared_prefs/x", "focusmode://mission/mission_a/../../b", "focusmode://reminder/../../x",
            "focusmode://mission/%252e%252e", "focusmode://mission/file:%2F%2F%2Fdata",
        ).forEach(::home)
        // ".." alone passes the shared id policy but is only ever an opaque lookup key (no Mission has it).
        assertEquals(DeepLinkTarget.Mission(".."), DeepLinks.parse("focusmode://mission/.."))
    }

    @Test fun oversizedIdsAndLinksGoHome() {
        home("focusmode://mission/" + "m".repeat(161))
        home("focusmode://result/" + "r".repeat(400))
        home("focusmode://quickfocus?schedule=" + "s".repeat(500))
        assertEquals(DeepLinkTarget.Mission("m".repeat(160)), DeepLinks.parse("focusmode://mission/" + "m".repeat(160)))
    }

    @Test fun repeatedConflictingAndUnknownParametersAreIgnoredSafely() {
        // The first schedule wins; an unsafe value means "no schedule", never an injection.
        assertEquals(DeepLinkTarget.QuickFocus("schedule_a"), DeepLinks.parse("focusmode://quickfocus?schedule=schedule_a&schedule=schedule_b"))
        assertEquals(DeepLinkTarget.QuickFocus(null), DeepLinks.parse("focusmode://quickfocus?schedule=../x&schedule=schedule_b"))
        assertEquals(DeepLinkTarget.QuickFocus(null), DeepLinks.parse("focusmode://quickfocus?uid=attacker&autostart=true&minutes=999"))
        // Query parameters never add behavior to other targets.
        assertEquals(DeepLinkTarget.History, DeepLinks.parse("focusmode://history?delete=all"))
        assertEquals(DeepLinkTarget.EndSession, DeepLinks.parse("focusmode://session/end?confirm=true&finalize=1"))
        assertEquals(DeepLinkTarget.Mission("mission_a"), DeepLinks.parse("focusmode://mission/mission_a?account=other"))
    }

    @Test fun otherSchemesAndIntentSyntaxGoHome() {
        listOf(
            "https://focusmode/mission/mission_a", "http://example.invalid/", "FOCUSMODE://mission/mission_a", "focusmode:mission/mission_a",
            "intent://mission/mission_a#Intent;scheme=focusmode;end", "javascript:alert(1)", "file:///data/data/app.focusmodeplus/databases/focus.db",
            "content://app.focusmodeplus.materialcapture/material_capture/capture.jpg", "focusmode-reminder:scheduled_focus:x",
        ).forEach(::home)
    }

    @Test fun noTargetCarriesAnAction() {
        // The complete target set: navigation destinations only (compile-time list kept in step with DeepLinkTarget).
        val targets = listOf(
            DeepLinkTarget.Home, DeepLinkTarget.ActiveSession, DeepLinkTarget.EndSession, DeepLinkTarget.Missions, DeepLinkTarget.Mission("mission_a"),
            DeepLinkTarget.QuickFocus("schedule_a"), DeepLinkTarget.Reminder("scheduled_focus:schedule_a:2026-11-19"), DeepLinkTarget.History,
            DeepLinkTarget.Result("session_a"), DeepLinkTarget.PermissionCenter,
        )
        targets.forEach { assertEquals(it, DeepLinks.parse(DeepLinks.build(it))) }
        targets.forEach { t -> assertTrue(DeepLinks.build(t).none { it.isWhitespace() }) }
    }

    /** 2 000 seeded random links: parsing is total (never throws) and bounded. */
    @Test fun seededRandomLinksNeverThrow() {
        val random = Random(22)
        val alphabet = "focusmode:/?&=%._-~\\#@!$'()*+,;[]{}<>|^`\"\u0000‮az09ÄΩ"
        repeat(2_000) {
            val length = random.nextInt(0, 420)
            val body = buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
            DeepLinks.parse(if (it % 2 == 0) "focusmode://$body" else body)
        }
    }
}
