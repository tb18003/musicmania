package io.github.tb18003.musicmania.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/** Enables Jetpack Compose with the shared BOM, tooling and test dependencies. */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    commonExtension.buildFeatures.compose = true

    dependencies {
        val bom = libs.lib("androidx-compose-bom")
        add("implementation", platform(bom))
        add("testImplementation", platform(bom))
        add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
        add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
        add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
        add("testImplementation", libs.lib("robolectric"))
    }
    if (hasAndroidTests()) {
        dependencies {
            add("androidTestImplementation", platform(libs.lib("androidx-compose-bom")))
            add("androidTestImplementation", libs.lib("androidx-compose-ui-test-junit4"))
        }
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        // Opt-in compiler reports: ./gradlew assembleRelease -PcomposeCompilerReports=true
        val enableReports = providers.gradleProperty("composeCompilerReports").map { it.toBoolean() }.orElse(false)
        if (enableReports.get()) {
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
            metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        }
        stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_compiler_config.conf"))
    }
}
