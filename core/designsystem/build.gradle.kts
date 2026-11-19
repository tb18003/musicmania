plugins {
    alias(libs.plugins.musicmania.android.library.compose)
    alias(libs.plugins.musicmania.android.roborazzi)
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.coil.compose)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.androidx.test.core)
}
