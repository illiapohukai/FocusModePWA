plugins {
    alias(libs.plugins.focus.android.feature)
}

dependencies {
    implementation(project(":core:protection"))
}

dependencies {
    testImplementation(project(":core:notifications"))
}
