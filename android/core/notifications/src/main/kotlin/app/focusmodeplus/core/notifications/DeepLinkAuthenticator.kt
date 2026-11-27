package app.focusmodeplus.core.notifications

import android.content.Context
import android.content.Intent
import app.focusmodeplus.core.domain.deeplink.DeepLinkTarget
import app.focusmodeplus.core.domain.deeplink.DeepLinks
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v22 (V22-F06): only the app's own notifications may navigate inside the app.
 *
 * The launcher activity has to be exported, so any installed app can send it an explicit
 * `focusmode://` VIEW intent. Navigation was always validated and never performs an action, but
 * a foreign app could still drive the UI (for example open the end-of-Session confirmation over
 * and over). Every route this app puts into a PendingIntent now carries an HMAC-SHA256 tag made
 * with a random per-install key in `noBackupFilesDir` (never backed up, never logged, never
 * leaves the device). A route without a valid tag is ignored: the activity opens as from the
 * launcher. The tag is not a secret the user sees and protects navigation only.
 */
@Singleton
class DeepLinkAuthenticator internal constructor(private val keyFile: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.noBackupFilesDir, KEY_FILE))

    @Volatile private var cached: ByteArray? = null

    private fun key(): ByteArray = cached ?: synchronized(this) {
        cached ?: (readKey() ?: createKey()).also { cached = it }
    }

    private fun readKey(): ByteArray? = runCatching { keyFile.takeIf { it.isFile }?.readBytes() }.getOrNull()?.takeIf { it.size == KEY_BYTES }

    private fun createKey(): ByteArray {
        val bytes = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        runCatching {
            keyFile.parentFile?.mkdirs()
            val tmp = File(keyFile.parentFile, "$KEY_FILE.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(keyFile)) tmp.delete()
        }
        // If the key could not be persisted it still works for this process; after a restart old
        // notifications simply open the app without navigating.
        return bytes
    }

    /** The tag for one complete route string (`focusmode://…`). */
    fun sign(route: String): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key(), ALGORITHM)) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(route.toByteArray(Charsets.UTF_8)))
    }

    fun verify(route: String, tag: String?): Boolean {
        if (tag == null || tag.length != TAG_CHARS) return false
        return MessageDigest.isEqual(sign(route).toByteArray(Charsets.US_ASCII), tag.toByteArray(Charsets.US_ASCII))
    }

    /** Adds the route and its tag to an intent this app creates for its own PendingIntents. */
    fun attach(intent: Intent, target: DeepLinkTarget): Intent {
        val route = DeepLinks.build(target)
        intent.data = route.toUri()
        intent.putExtra(EXTRA_TAG, sign(route))
        return intent
    }

    /**
     * The validated in-app destination of an incoming intent, or null for a plain launch: not a
     * VIEW intent, another scheme, no or a wrong tag (a foreign caller), or unreadable extras.
     */
    fun routeFrom(intent: Intent?): DeepLinkTarget? = runCatching {
        if (intent?.action != Intent.ACTION_VIEW) return@runCatching null
        val data = intent.data ?: return@runCatching null
        if (data.scheme != DeepLinks.SCHEME) return@runCatching null
        val route = data.toString()
        // Extras of a foreign intent can hold classes this app cannot unparcel; that is a plain launch.
        if (!verify(route, intent.getStringExtra(EXTRA_TAG))) return@runCatching null
        DeepLinks.parse(route)
    }.getOrNull()

    companion object {
        const val EXTRA_TAG = "app.focusmodeplus.extra.ROUTE_TAG"
        internal const val KEY_FILE = "deeplink_route.key"
        private const val ALGORITHM = "HmacSHA256"
        private const val KEY_BYTES = 32
        /** Base64url without padding of 32 bytes. */
        private const val TAG_CHARS = 43
    }
}
