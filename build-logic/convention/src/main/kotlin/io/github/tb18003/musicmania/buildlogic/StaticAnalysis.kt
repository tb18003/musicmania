package io.github.tb18003.musicmania.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applies Detekt, Spotless (ktlint) and Kover to a module so every module is
 * held to the same quality bar without repeating configuration.
 */
internal fun Project.configureStaticAnalysis() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    pluginManager.apply("com.diffplug.spotless")
    pluginManager.apply("org.jetbrains.kotlinx.kover")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
    }

    // Detekt 1.23.x embeds Kotlin 2.0.x; keep its classpath on that version.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(libs.version("detektKotlin"))
            }
        }
    }

    extensions.configure<SpotlessExtension> {
        val ktlintVersion = libs.version("ktlint")
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            // Rules are configured in the root .editorconfig.
            ktlint(ktlintVersion).setEditorConfigPath(rootProject.file(".editorconfig"))
            licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
        }
        kotlinGradle {
            target("*.kts")
            ktlint(ktlintVersion).setEditorConfigPath(rootProject.file(".editorconfig"))
        }
    }
}
