plugins {
    alias(libs.plugins.musicmania.android.feature)
}

dependencies {
    // Exception to the feature dependency rule: the login screen must launch the
    // OAuth intent, which is Android-specific and lives in core:auth.
    implementation(projects.core.auth)
}

android {
    defaultConfig {
        // Required by AppAuth's manifest when merging test manifests; see core:auth.
        manifestPlaceholders["appAuthRedirectScheme"] = "io.github.tb18003.musicmania.unused"
    }
}
