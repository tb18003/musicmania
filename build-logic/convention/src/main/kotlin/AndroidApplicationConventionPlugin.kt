import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasUnitTestBuilder
import io.github.tb18003.musicmania.buildlogic.configureKotlinAndroid
import io.github.tb18003.musicmania.buildlogic.configureStaticAnalysis
import io.github.tb18003.musicmania.buildlogic.hasUnitTests
import io.github.tb18003.musicmania.buildlogic.libs
import io.github.tb18003.musicmania.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
                buildFeatures.buildConfig = true
            }
            // Debug-only unit tests, and no test task for modules without tests (see the library plugin).
            extensions.configure<ApplicationAndroidComponentsExtension> {
                beforeVariants { variant ->
                    (variant as HasUnitTestBuilder).enableUnitTest = variant.buildType != "release" && hasUnitTests()
                }
            }
            configureStaticAnalysis()
        }
    }
}
