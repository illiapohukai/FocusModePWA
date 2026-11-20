plugins {
    alias(libs.plugins.focus.android.library)
    alias(libs.plugins.focus.android.hilt)
    alias(libs.plugins.room)
}

room {
    // Exported schemas are committed and drive the migration tests.
    schemaDirectory("$projectDir/schemas")
}

android {
    sourceSets {
        getByName("test").assets.directories.add("$projectDir/schemas")
    }
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.turbine)
}
