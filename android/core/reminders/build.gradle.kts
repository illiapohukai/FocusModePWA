plugins {
    alias(libs.plugins.focus.android.library)
    alias(libs.plugins.focus.android.hilt)
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:notifications"))
    implementation(project(":core:protection"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
