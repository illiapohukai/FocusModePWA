package io.github.illiapohukai.focusmode.core.domain.protection

import io.github.illiapohukai.focusmode.core.model.AppAvailability
import io.github.illiapohukai.focusmode.core.model.InstalledApp
import io.github.illiapohukai.focusmode.core.model.ResolvedSelectedApp
import io.github.illiapohukai.focusmode.core.model.SelectedDistractingApp
import java.text.Collator
import java.util.Locale

/**
 * Reconciles the stored selection with what PackageManager resolves right now. A stored package
 * name is never trusted as identity on its own.
 */
object AppSelectionReconciler {
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun isPlausiblePackageName(name: String): Boolean = name.length in 3..255 && PACKAGE.matches(name)

    fun reconcile(stored: List<SelectedDistractingApp>, resolve: (String) -> InstalledApp?): List<ResolvedSelectedApp> =
        stored.distinctBy { it.packageName }.map { selection ->
            val current = resolve(selection.packageName)
            when {
                current == null -> ResolvedSelectedApp(selection, AppAvailability.UNINSTALLED, selection.labelSnapshot, false)
                !current.enabled -> ResolvedSelectedApp(selection, AppAvailability.DISABLED, current.label, current.label != selection.labelSnapshot)
                else -> ResolvedSelectedApp(selection, AppAvailability.INSTALLED, current.label, current.label != selection.labelSnapshot)
            }
        }

    /** One entry per package (profiles or duplicate launcher activities collapse), sorted by label. */
    fun normalizeCatalog(apps: List<InstalledApp>, ownPackage: String): List<InstalledApp> {
        val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY }
        return apps.asSequence()
            .filter { it.packageName != ownPackage && isPlausiblePackageName(it.packageName) }
            .groupBy { it.packageName }
            .map { (_, same) -> same.firstOrNull { it.enabled } ?: same.first() }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** Packages watched during a Session: only selected apps that resolve and are enabled. */
    fun watchablePackages(resolved: List<ResolvedSelectedApp>): Set<String> =
        resolved.filter { it.availability == AppAvailability.INSTALLED }.map { it.selection.packageName }.toSet()
}
