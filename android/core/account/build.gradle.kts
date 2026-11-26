plugins {
    alias(libs.plugins.focus.android.library)
    alias(libs.plugins.focus.android.hilt)
}

// v21: the only module that depends on Firebase and Credential Manager. Features and the domain
// see only AccountRepository / RemoteSyncStore / SyncScheduler. Without google-services.json the
// app binds the unconfigured implementations and never initializes Firebase.
dependencies {
    api(project(":core:domain"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.appcheck)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
}
