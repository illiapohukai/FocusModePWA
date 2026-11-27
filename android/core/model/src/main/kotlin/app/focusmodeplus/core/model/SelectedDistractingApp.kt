package app.focusmodeplus.core.model

import java.time.Instant

/**
 * A user-selected distracting app. Only the package name, a label snapshot and an optional icon
 * cache reference are stored. Stays on the device: never uploaded, never sent to the AI Worker,
 * never part of analytics (there are none). A package name is not an identity until the current
 * PackageManager resolves it.
 */
data class SelectedDistractingApp(
    val packageName: String,
    val labelSnapshot: String,
    val selectedAt: Instant,
    val iconCacheRef: String? = null,
)

/** A launchable app the platform let us see through the narrow launcher `<queries>` intent. */
data class InstalledApp(val packageName: String, val label: String, val enabled: Boolean)

enum class AppAvailability { INSTALLED, DISABLED, UNINSTALLED }

data class ResolvedSelectedApp(
    val selection: SelectedDistractingApp,
    val availability: AppAvailability,
    /** Current label when resolvable, else the snapshot. */
    val displayLabel: String,
    val labelChanged: Boolean,
)
