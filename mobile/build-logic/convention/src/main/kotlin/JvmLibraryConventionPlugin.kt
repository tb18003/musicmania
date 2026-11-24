import io.github.tb18003.musicmania.buildlogic.configureKotlinJvm
import io.github.tb18003.musicmania.buildlogic.configureStaticAnalysis
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Pure Kotlin module: no Android dependency, fastest to build and test. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureKotlinJvm()
            configureStaticAnalysis()
        }
    }
}
