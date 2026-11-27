plugins {
    alias(libs.plugins.musicmania.android.feature)
    alias(libs.plugins.musicmania.android.roborazzi)
}

dependencies {
    // Screenshot tests serve generated covers through a fake Coil image loader (no network).
    testImplementation(libs.coil.compose)
}
