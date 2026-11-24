plugins {
    alias(libs.plugins.musicmania.android.library)
    alias(libs.plugins.musicmania.android.room)
    alias(libs.plugins.musicmania.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.serialization.json)
    api(libs.androidx.room.paging)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.paging.testing)
}
