import com.android.build.api.dsl.TestExtension
import io.github.tb18003.musicmania.buildlogic.configureStaticAnalysis
import io.github.tb18003.musicmania.buildlogic.libs
import io.github.tb18003.musicmania.buildlogic.version
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Standalone test module (`com.android.test`) that instruments another module, e.g. the
 * baseline profile generator and macrobenchmarks. No orchestrator: Macrobenchmark manages
 * the target app's process and compilation state itself.
 */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.test")

            extensions.configure<TestExtension> {
                compileSdk = libs.version("compileSdk").toInt()
                compileSdkMinor = libs.version("compileSdkMinor").toInt()
                defaultConfig.apply {
                    // Baseline profiles need API 28+ (API 33+ without root).
                    minSdk = MIN_SDK
                    targetSdk = libs.version("targetSdk").toInt()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions.apply {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            configureStaticAnalysis()
        }
    }

    private companion object {
        const val MIN_SDK = 28
    }
}
