plugins {
    alias(libs.plugins.focus.android.application)
    alias(libs.plugins.focus.android.compose)
    alias(libs.plugins.focus.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig {
        applicationId = "io.github.illiapohukai.focusmode"
        versionCode = 1900
        versionName = "19.0.0"
        // Public URL of the existing Cloudflare Worker. Not a secret; no API key exists on Android.
        buildConfigField("String", "WORKER_BASE_URL", "\"https://focusmodedemoai.illia-pohukai.workers.dev/\"")
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

    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.androidx.work.testing)
    // Seeds the real database file before the activity starts (Mission flow tests).
    testImplementation(libs.androidx.room.runtime)
}
