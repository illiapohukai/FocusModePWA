plugins {
    alias(libs.plugins.focus.android.application)
    alias(libs.plugins.focus.android.compose)
    alias(libs.plugins.focus.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// v21 Firebase configuration boundary. The repository never contains google-services.json; the
// owner places it at android/app/google-services.json (see android/docs/firebase-setup-v21.md).
// Without it the build is complete and works in guest mode: the Google Services plugin is not
// applied, FIREBASE_CONFIGURED is false and Firebase is never initialized.
val googleServicesFile = layout.projectDirectory.file("google-services.json").asFile
val firebaseConfigured = googleServicesFile.isFile
if (firebaseConfigured) apply(plugin = libs.plugins.google.services.get().pluginId)

/** The public OAuth web client id (client_type 3) for Sign in with Google; not a secret. */
fun googleServerClientId(): String {
    if (!firebaseConfigured) return ""
    @Suppress("UNCHECKED_CAST")
    val root = groovy.json.JsonSlurper().parse(googleServicesFile) as Map<String, Any?>
    val clients = (root["client"] as? List<Map<String, Any?>>).orEmpty()
    return clients.asSequence()
        .flatMap { (it["oauth_client"] as? List<Map<String, Any?>>).orEmpty().asSequence() }
        .firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }
        ?.get("client_id") as? String ?: ""
}

android {
    defaultConfig {
        // v22 application identity (android/docs/application-identity-v22.md): permanent once
        // published on Google Play. Debug builds append ".debug" and install side by side.
        applicationId = "app.focusmodeplus"
        versionCode = 2200
        versionName = "22.0.0"
        // Public URL of the existing Cloudflare Worker. Not a secret; no API key exists on Android.
        buildConfigField("String", "WORKER_BASE_URL", "\"https://focusmodedemoai.illia-pohukai.workers.dev/\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${googleServerClientId()}\"")
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No signing configuration here by design: release signing is configured outside the repository.
        }
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:notifications"))
    implementation(project(":core:protection"))
    implementation(project(":core:session"))
    implementation(project(":core:reminders"))
    implementation(project(":core:account"))

    implementation(project(":feature:home"))
    implementation(project(":feature:quickfocus"))
    implementation(project(":feature:focus"))
    implementation(project(":feature:result"))
    implementation(project(":feature:history"))
    implementation(project(":feature:missions"))
    implementation(project(":feature:reminders"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // App Check providers per build type (versions from the Firebase BoM): Play Integrity in
    // release, the debug provider only in debug builds. No debug token is stored anywhere.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // v22 (V22-F17): security constraint, not a new dependency. grpc-api (via firebase-firestore)
    // requests guava 33.6.0-android, affected by GHSA-xxph-c9ww-hj94 (HIGH); the first patched
    // release of the same major is enforced for every app classpath. Remove when grpc ships it.
    constraints {
        implementation(libs.guava) { because("GHSA-xxph-c9ww-hj94: first patched guava release (same major)") }
    }

    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.androidx.work.testing)
    // Seeds the real database file before the activity starts (Mission flow tests).
    testImplementation(libs.androidx.room.runtime)
}
