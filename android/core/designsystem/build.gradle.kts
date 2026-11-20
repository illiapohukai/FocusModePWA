plugins {
    alias(libs.plugins.focus.android.library)
    alias(libs.plugins.focus.android.compose)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
