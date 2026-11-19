import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import io.github.tb18003.musicmania.buildlogic.lib
import io.github.tb18003.musicmania.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<KspExtension> {
                arg("room.generateKotlin", "true")
            }
            extensions.configure<RoomExtension> {
                // Exported schemas are committed so migrations can be reviewed and tested.
                schemaDirectory("$projectDir/schemas")
            }
            dependencies {
                add("implementation", libs.lib("androidx-room-runtime"))
                add("implementation", libs.lib("androidx-room-ktx"))
                add("ksp", libs.lib("androidx-room-compiler"))
                add("testImplementation", libs.lib("androidx-room-testing"))
            }
        }
    }
}
