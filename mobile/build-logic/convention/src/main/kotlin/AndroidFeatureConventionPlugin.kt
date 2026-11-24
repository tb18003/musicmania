import io.github.tb18003.musicmania.buildlogic.lib
import io.github.tb18003.musicmania.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module = Compose UI + ViewModels + navigation entry point.
 * Features may depend on core:domain/ui/designsystem/model but never on
 * data-layer modules or on other features (enforced by Konsist tests).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("musicmania.android.library.compose")
            pluginManager.apply("musicmania.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:common"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:designsystem"))

                add("implementation", libs.lib("androidx-hilt-navigation-compose"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-navigation-compose"))
                add("implementation", libs.lib("kotlinx-serialization-json"))
                add("implementation", libs.lib("kotlinx-collections-immutable"))

                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
