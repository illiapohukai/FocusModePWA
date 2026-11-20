plugins {
    alias(libs.plugins.focus.android.feature)
}

dependencies {
    implementation(libs.androidx.activity.compose)
}

dependencies {
    implementation(project(":core:protection"))
}
