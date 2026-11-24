import com.android.build.api.dsl.LibraryExtension
import io.github.tb18003.musicmania.buildlogic.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("musicmania.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            configureAndroidCompose(extensions.getByType<LibraryExtension>())
        }
    }
}
