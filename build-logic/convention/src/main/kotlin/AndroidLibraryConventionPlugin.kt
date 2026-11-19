import com.android.build.api.dsl.LibraryExtension
import io.github.tb18003.musicmania.buildlogic.configureKotlinAndroid
import io.github.tb18003.musicmania.buildlogic.configureStaticAnalysis
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
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                namespace = musicManiaNamespace()
                testOptions.targetSdk = libs.version("targetSdk").toInt()
                lint.targetSdk = libs.version("targetSdk").toInt()
                defaultConfig.consumerProguardFiles("consumer-rules.pro")
                // Library resources are prefixed per module to avoid clashes.
                resourcePrefix = path.split(':').drop(1).joinToString("_") + "_"
            }
            configureStaticAnalysis()
        }
    }
}
