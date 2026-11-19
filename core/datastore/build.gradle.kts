plugins {
    alias(libs.plugins.musicmania.android.library)
    alias(libs.plugins.musicmania.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    api(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
