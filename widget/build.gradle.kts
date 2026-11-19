plugins {
    alias(libs.plugins.musicmania.android.library.compose)
    alias(libs.plugins.musicmania.hilt)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
