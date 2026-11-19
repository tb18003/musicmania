plugins {
    alias(libs.plugins.musicmania.android.library)
    alias(libs.plugins.musicmania.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(projects.core.common)
    implementation(projects.core.datastore)
    api(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
