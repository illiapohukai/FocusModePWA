package io.github.illiapohukai.focusmode.core.account

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Build-time Firebase configuration (v21). [configured] is true only when the build found the
 * owner's `android/app/google-services.json` (never committed). [serverClientId] is the public
 * OAuth web client id from that file, needed by Sign in with Google. Neither value is a secret.
 */
data class FirebaseBuildConfig(val configured: Boolean, val serverClientId: String?) {
    val usable: Boolean get() = configured && !serverClientId.isNullOrBlank()

    companion object {
        val UNCONFIGURED = FirebaseBuildConfig(configured = false, serverClientId = null)
    }
}

/**
 * Firebase App Check provider boundary. The app supplies Play Integrity in release builds and the
 * debug provider (which prints a per-install debug secret to logcat for registration in the
 * Firebase Console; no token is stored in the repository) in debug builds.
 */
fun interface AppCheckProviderSource {
    fun factory(): AppCheckProviderFactory?
}

/**
 * Initializes Firebase only when this build is configured. The automatic FirebaseInitProvider
 * is removed from the manifest, so an unconfigured build never touches Firebase at all.
 * Firestore uses a memory-only cache: Room is the local store, user data is not duplicated on
 * disk, and no write can sit hidden in an SDK queue across process death.
 */
@Singleton
class FirebaseRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: FirebaseBuildConfig,
    private val appCheck: AppCheckProviderSource,
) {
    val serverClientId: String? get() = config.serverClientId

    val app: FirebaseApp? by lazy {
        if (!config.usable) return@lazy null
        val options = FirebaseOptions.fromResource(context) ?: return@lazy null
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME } ?: FirebaseApp.initializeApp(context, options)
        // App Check: tokens are attached once a provider is installed; enforcement is a Console
        // decision that stays off until the metrics are verified (android/docs/firebase-app-check-v21.md).
        runCatching { appCheck.factory()?.let { FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(it) } }
        app
    }

    /** True only for a configured build whose Firebase options load. Never initializes Firebase in an unconfigured build. */
    val available: Boolean get() = config.usable && app != null

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(checkNotNull(app)).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
    }
}
