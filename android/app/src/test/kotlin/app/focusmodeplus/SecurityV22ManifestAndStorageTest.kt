package app.focusmodeplus

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * v22 platform and local-storage assertions on the merged (debug) manifest as Robolectric parses
 * it and on the shipped XML policies. The release APK is checked by scripts/security/apk_audit.py.
 */
@RunWith(AndroidJUnit4::class)
class SecurityV22ManifestAndStorageTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val pm = app.packageManager

    private fun xml(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path)).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.androidAttr(name: String): String = getAttributeNS("http://schemas.android.com/apk/res/android", name)

    @Test fun identityIsTheV22DebugApplicationId() {
        assertEquals("app.focusmodeplus.debug", app.packageName)
        assertEquals("Focus Mode+ Debug", app.getString(R.string.app_name))
        assertEquals("22.0.0-debug", BuildConfig.VERSION_NAME)
        assertEquals(2200, BuildConfig.VERSION_CODE)
        assertEquals("app.focusmodeplus", BuildConfig::class.java.`package`!!.name)
        assertFalse(BuildConfig.FIREBASE_CONFIGURED) // the repository never contains google-services.json
        assertTrue(BuildConfig.WORKER_BASE_URL.startsWith("https://"))
    }

    @Test fun onlyTheLauncherActivityOfTheAppIsExported() {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        val info = pm.getPackageInfo(app.packageName, flags)
        val own = (info.activities.orEmpty().toList() + info.receivers.orEmpty() + info.services.orEmpty() + info.providers.orEmpty())
            .filter { it.name.startsWith("app.focusmodeplus.") }
        assertEquals(listOf("app.focusmodeplus.MainActivity"), own.filter { it.exported }.map { it.name })
        // No implicit VIEW intent reaches the app: the focusmode:// scheme is not claimed (V22-F06).
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("focusmode://session/end")).setPackage(app.packageName)
        assertTrue(pm.queryIntentActivities(view, 0).isEmpty())
        // Providers are private; the FileProvider authority is derived from the application id.
        info.providers.orEmpty().forEach { p ->
            assertFalse(p.name, p.exported)
            p.authority.split(';').forEach { assertTrue(it, it.startsWith(app.packageName + ".")) }
        }
        assertTrue(info.providers.orEmpty().any { it.authority == "app.focusmodeplus.debug.materialcapture" && it.grantUriPermissions })
        assertTrue(info.providers.orEmpty().none { it.name == "com.google.firebase.provider.FirebaseInitProvider" })
    }

    @Test fun debugToolingActivitiesAreNotExportedToOtherApps() {
        // V22-F09: Compose PreviewActivity and the test host stay in-process only.
        val info = pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)
        for (name in listOf("androidx.compose.ui.tooling.PreviewActivity", "androidx.activity.ComponentActivity")) {
            info.activities.orEmpty().filter { it.name == name }.forEach { assertFalse(name, it.exported) }
        }
        info.activities.orEmpty().filter { it.name.startsWith("com.google.firebase.auth.internal.") }.let { assertTrue(it.map { a -> a.name }.toString(), it.isEmpty()) }
    }

    @Test fun requestedPermissionsAreTheMinimalSet() {
        val requested = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        val forbidden = setOf(
            "android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES", "android.permission.ACCESS_FINE_LOCATION", "android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT",
            "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_EXACT_ALARM", "com.google.android.providers.gsf.permission.READ_GSERVICES",
            "com.google.android.gms.permission.AD_ID", "android.permission.GET_ACCOUNTS",
        )
        assertTrue((requested intersect forbidden).toString(), (requested intersect forbidden).isEmpty())
        assertTrue(requested.contains("android.permission.PACKAGE_USAGE_STATS")) // special access, granted by the user in settings
    }

    @Test fun backupPoliciesIncludeOnlyTheDatabaseAndPreferences() {
        val extraction = xml("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val s = extraction.children(section).single()
            val includes = s.children("include").map { it.getAttribute("domain") to it.getAttribute("path") }.toSet()
            assertEquals(section, setOf("database" to ".", "file" to "datastore/"), includes)
        }
        val legacy = xml("src/main/res/xml/backup_rules.xml").children("include").map { it.getAttribute("domain") to it.getAttribute("path") }.toSet()
        assertEquals(setOf("database" to ".", "file" to "datastore/"), legacy)
        // Firebase Auth keeps its session in shared preferences, the deep-link key and the draft
        // photo live in no_backup: none of these domains is included (an include list excludes the rest).
        val all = (extraction.getElementsByTagName("include").let { l -> (0 until l.length).map { (l.item(it) as Element).getAttribute("domain") } }).toSet()
        assertFalse(all.contains("sharedpref") || all.contains("root") || all.contains("external"))
    }

    @Test fun networkSecurityConfigIsHttpsOnlyWithSystemAnchors() {
        val config = xml("src/main/res/xml/network_security_config.xml")
        val base = config.children("base-config").single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val anchors = base.children("trust-anchors").single().children("certificates").map { it.getAttribute("src") }
        assertEquals(listOf("system"), anchors)
        assertTrue(config.children("domain-config").isEmpty())
        assertTrue(config.children("debug-overrides").isEmpty())
        val manifest = xml("src/main/AndroidManifest.xml").children("application").single()
        assertFalse(manifest.androidAttr("usesCleartextTraffic") == "true")
        assertEquals("", manifest.androidAttr("debuggable"))
    }

    @Test fun fileProviderExposesOnlyTheCameraCacheDirectory() {
        val paths = xml("../core/network/src/main/res/xml/material_capture_paths.xml")
        val entries = (0 until paths.childNodes.length).map { paths.childNodes.item(it) }.filterIsInstance<Element>()
        assertEquals(listOf("cache-path" to "material_capture/"), entries.map { it.tagName to it.getAttribute("path") })
    }

    @Test fun theRoomSchemaHoldsNoTokensCredentialsOrAccountProfile() {
        val schema = Json.parseToJsonElement(File("../core/data/schemas/app.focusmodeplus.core.data.db.FocusDatabase/5.json").readText()).jsonObject
        val database = schema["database"]!!.jsonObject
        assertEquals(5, database["version"]!!.jsonPrimitive.content.toInt())
        val forbidden = Regex("(?i)token|secret|password|credential|email|display_?name|photo|api_?key|auth")
        val tables = database["entities"]!!.jsonArray.map { it.jsonObject }
        tables.forEach { t ->
            val table = t["tableName"]!!.jsonPrimitive.content
            t["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }.forEach { c ->
                assertFalse("$table.$c", forbidden.containsMatchIn(c))
            }
        }
        // The account binding stores the Firebase uid and sync bookkeeping only.
        val binding = tables.single { it["tableName"]!!.jsonPrimitive.content == "account_binding" }
        val columns = binding["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        assertTrue(columns.toString(), columns.contains("uid") && columns.none { forbidden.containsMatchIn(it) })
    }
}
