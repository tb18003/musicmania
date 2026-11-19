plugins {
    alias(libs.plugins.musicmania.android.library)
    alias(libs.plugins.musicmania.hilt)
}

android {
    defaultConfig {
        // AppAuth's manifest requires this placeholder; the app overrides the redirect
        // receiver with a verified HTTPS App Link instead of a custom scheme.
        manifestPlaceholders["appAuthRedirectScheme"] = "io.github.tb18003.musicmania.unused"
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    api(libs.appauth)
    implementation(libs.androidx.browser)

    testImplementation(projects.core.testing)
}
