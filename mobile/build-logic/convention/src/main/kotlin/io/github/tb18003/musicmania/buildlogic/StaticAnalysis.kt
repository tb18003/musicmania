package io.github.tb18003.musicmania.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applies Detekt, Spotless (ktlint) and Kover to a module so every module is
 * held to the same quality bar without repeating configuration.
 */
internal fun Project.configureStaticAnalysis() {
    pluginManager.apply("dev.detekt")
    pluginManager.apply("com.diffplug.spotless")
    pluginManager.apply("org.jetbrains.kotlinx.kover")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(repoFile("config/detekt/detekt.yml"))
        baseline.set(file("detekt-baseline.xml"))
    }

    extensions.configure<SpotlessExtension> {
        val ktlintVersion = libs.version("ktlint")
        kotlin {
            // A tree rooted at src/: a "src/**" pattern makes Gradle walk the whole module,
            // build/ included, which parallel tasks rewrite ("Could not read path …/build/…").
            target(fileTree("src") { include("**/*.kt") })
            // Rules are configured in the root .editorconfig.
            ktlint(ktlintVersion).setEditorConfigPath(repoFile(".editorconfig"))
            licenseHeaderFile(repoFile("config/spotless/copyright.kt"))
        }
        kotlinGradle {
            target("*.kts")
            ktlint(ktlintVersion).setEditorConfigPath(repoFile(".editorconfig"))
        }
    }
}
