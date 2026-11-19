plugins {
    alias(libs.plugins.musicmania.android.feature)
}

dependencies {
    implementation(libs.androidx.paging.compose)
    testImplementation(libs.androidx.paging.testing)
}
