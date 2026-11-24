import com.android.build.gradle.api.AndroidBasePlugin
import io.github.tb18003.musicmania.buildlogic.lib
import io.github.tb18003.musicmania.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Adds Dagger/Hilt with KSP. Works for both pure JVM and Android modules. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            dependencies {
                add("ksp", libs.lib("hilt-compiler"))
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    add("implementation", libs.lib("hilt-core"))
                }
            }

            plugins.withType(AndroidBasePlugin::class.java) {
                pluginManager.apply("com.google.dagger.hilt.android")
                dependencies {
                    add("implementation", libs.lib("hilt-android"))
                }
            }
        }
    }
}
