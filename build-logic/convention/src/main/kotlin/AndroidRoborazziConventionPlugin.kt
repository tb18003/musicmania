import io.github.tb18003.musicmania.buildlogic.lib
import io.github.tb18003.musicmania.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Screenshot testing on the JVM (Robolectric + Roborazzi). */
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")
            dependencies {
                add("testImplementation", libs.lib("roborazzi"))
                add("testImplementation", libs.lib("roborazzi-compose"))
                add("testImplementation", libs.lib("roborazzi-junit-rule"))
                add("testImplementation", libs.lib("robolectric"))
            }
        }
    }
}
