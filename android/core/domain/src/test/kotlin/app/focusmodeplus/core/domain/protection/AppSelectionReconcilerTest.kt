package app.focusmodeplus.core.domain.protection

import app.focusmodeplus.core.model.AppAvailability
import app.focusmodeplus.core.model.InstalledApp
import app.focusmodeplus.core.model.SelectedDistractingApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AppSelectionReconcilerTest {
    private fun sel(pkg: String, label: String) = SelectedDistractingApp(pkg, label, Instant.EPOCH)

    @Test fun resolvesInstalledUninstalledDisabledAndRenamedApps() {
        val installed = mapOf(
            "com.example.video" to InstalledApp("com.example.video", "Video", true),
            "com.example.chat" to InstalledApp("com.example.chat", "Chat Neu", true),
            "com.example.game" to InstalledApp("com.example.game", "Spiel", false),
        )
        val r = AppSelectionReconciler.reconcile(
            listOf(sel("com.example.video", "Video"), sel("com.example.chat", "Chat"), sel("com.example.game", "Spiel"), sel("com.example.gone", "Weg")),
            installed::get,
        ).associateBy { it.selection.packageName }
        assertEquals(AppAvailability.INSTALLED, r.getValue("com.example.video").availability)
        assertTrue(r.getValue("com.example.chat").labelChanged)
        assertEquals("Chat Neu", r.getValue("com.example.chat").displayLabel)
        assertEquals(AppAvailability.DISABLED, r.getValue("com.example.game").availability)
        assertEquals(AppAvailability.UNINSTALLED, r.getValue("com.example.gone").availability)
        assertEquals("Weg", r.getValue("com.example.gone").displayLabel)
        assertEquals(setOf("com.example.video", "com.example.chat"), AppSelectionReconciler.watchablePackages(r.values.toList()))
    }

    @Test fun catalogCollapsesDuplicatesAndExcludesOwnPackage() {
        val apps = listOf(
            InstalledApp("com.example.b", "Beta", true),
            InstalledApp("com.example.a", "Äpfel", true),
            InstalledApp("com.example.b", "Beta (Profil)", false),
            InstalledApp("app.focusmodeplus", "Focus Mode+", true),
            InstalledApp("bad name", "Bad", true),
        )
        val out = AppSelectionReconciler.normalizeCatalog(apps, "app.focusmodeplus")
        assertEquals(listOf("com.example.a", "com.example.b"), out.map { it.packageName })
        assertTrue(out.single { it.packageName == "com.example.b" }.enabled)
        assertFalse(AppSelectionReconciler.isPlausiblePackageName("../etc"))
    }
}
