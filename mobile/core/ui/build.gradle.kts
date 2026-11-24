plugins {
    alias(libs.plugins.musicmania.android.library.compose)
    alias(libs.plugins.musicmania.android.roborazzi)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(projects.core.common)
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.androidx.test.core)
}
