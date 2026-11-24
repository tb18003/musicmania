import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.HasAndroidTestBuilder
import com.android.build.api.variant.HasUnitTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import io.github.tb18003.musicmania.buildlogic.configureKotlinAndroid
import io.github.tb18003.musicmania.buildlogic.configureStaticAnalysis
import io.github.tb18003.musicmania.buildlogic.hasAndroidTests
import io.github.tb18003.musicmania.buildlogic.hasUnitTests
import io.github.tb18003.musicmania.buildlogic.libs
import io.github.tb18003.musicmania.buildlogic.musicManiaNamespace
import io.github.tb18003.musicmania.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                namespace = musicManiaNamespace()
                testOptions.targetSdk = libs.version("targetSdk").toInt()
                lint.targetSdk = libs.version("targetSdk").toInt()
                // AGP 9 fails on missing files, so only modules that ship R8 rules declare them.
                if (file(CONSUMER_RULES).exists()) defaultConfig.consumerProguardFiles(CONSUMER_RULES)
                // Library resources are prefixed per module to avoid clashes.
                resourcePrefix = path.split(':').drop(1).joinToString("_") + "_"
            }
            // Unit tests run on the debug variant only; release variants would just duplicate them.
            // Modules without tests get no test task (Gradle 9 fails test tasks that discover nothing).
            extensions.configure<LibraryAndroidComponentsExtension> {
                beforeVariants { variant ->
                    (variant as HasUnitTestBuilder).enableUnitTest = variant.buildType != "release" && hasUnitTests()
                    (variant as HasAndroidTestBuilder).enableAndroidTest = variant.buildType == "debug" && hasAndroidTests()
                }
            }
            configureStaticAnalysis()
        }
    }

    private companion object {
        const val CONSUMER_RULES = "consumer-rules.pro"
    }
}
