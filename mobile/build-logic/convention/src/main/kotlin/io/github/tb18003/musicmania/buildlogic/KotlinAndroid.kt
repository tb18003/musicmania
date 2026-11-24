package io.github.tb18003.musicmania.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private val JAVA_VERSION = JavaVersion.VERSION_17

/** Gradle Managed Device used for instrumented tests (ATD image: headless and fast). */
const val MANAGED_DEVICE = "pixel6Api34"
private const val MANAGED_DEVICE_API = 34

/** Shared Android + Kotlin configuration for every Android module (app and libraries). */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.version("compileSdk").toInt()

        defaultConfig.apply {
            minSdk = libs.version("minSdk").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            // With the orchestrator every test runs in its own process on a freshly cleared app.
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
            vectorDrawables.useSupportLibrary = true
        }

        compileOptions.apply {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }

        testOptions.apply {
            unitTests.isIncludeAndroidResources = true
            unitTests.isReturnDefaultValues = false
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
            animationsDisabled = true
            // Reproducible device for instrumented tests, locally and in CI:
            // ./gradlew pixel6Api34DebugAndroidTest
            managedDevices.allDevices.create(MANAGED_DEVICE, ManagedVirtualDevice::class.java) {
                device = "Pixel 6"
                apiLevel = MANAGED_DEVICE_API
                systemImageSource = "aosp-atd"
            }
        }

        lint.apply {
            abortOnError = true
            checkDependencies = false
            warningsAsErrors = false
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
        }

        packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }

    configureKotlin<KotlinAndroidProjectExtension>()

    dependencies {
        add("testImplementation", libs.lib("junit4"))
        // Built-in Kotlin does not infer the kotlin-test JUnit variant, so request it explicitly.
        add("testImplementation", libs.lib("kotlin-test-junit"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
    }
    if (hasAndroidTests()) {
        dependencies {
            add("androidTestImplementation", libs.lib("androidx-test-ext-junit"))
            add("androidTestImplementation", libs.lib("androidx-test-runner"))
            add("androidTestImplementation", libs.lib("androidx-test-rules"))
            add("androidTestImplementation", libs.lib("kotlin-test-junit"))
            add("androidTestImplementation", libs.lib("kotlinx-coroutines-test"))
            add("androidTestUtil", libs.lib("androidx-test-orchestrator"))
            add("androidTestUtil", libs.lib("androidx-test-services"))
        }
    }
}

/** Configuration for pure Kotlin/JVM modules (no Android framework). */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    configureKotlin<KotlinJvmProjectExtension>()
    dependencies {
        add("testImplementation", libs.lib("junit4"))
        add("testImplementation", libs.lib("kotlin-test"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
    }
}

private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)
    when (this) {
        is KotlinAndroidProjectExtension -> compilerOptions
        is KotlinJvmProjectExtension -> compilerOptions
        else -> error("Unsupported Kotlin extension $this")
    }.apply {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(warningsAsErrors)
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xconsistent-data-class-copy-visibility",
        )
    }
}
