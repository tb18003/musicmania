plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kover)
}

// Aggregated coverage report: ./gradlew koverHtmlReport
dependencies {
    subprojects
        .filter { it.buildFile.exists() }
        .forEach { kover(project(it.path)) }
}

kover {
    reports {
        filters {
            excludes {
                // Generated code and DI glue are not meaningful for coverage.
                classes(
                    "*_Factory*",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "*_Impl*",
                    "*ComposableSingletons*",
                    "*.BuildConfig",
                    "dagger.hilt.internal.*",
                    "hilt_aggregated_deps.*",
                )
                annotatedBy("androidx.compose.ui.tooling.preview.Preview", "dagger.Module")
            }
        }
    }
}
