import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "io.github.tb18003.musicmania.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.musicmania.android.application.asProvider().get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = libs.plugins.musicmania.android.application.compose.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.musicmania.android.library.asProvider().get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = libs.plugins.musicmania.android.library.compose.get().pluginId
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.musicmania.android.feature.get().pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = libs.plugins.musicmania.android.room.get().pluginId
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidRoborazzi") {
            id = libs.plugins.musicmania.android.roborazzi.get().pluginId
            implementationClass = "AndroidRoborazziConventionPlugin"
        }
        register("androidTest") {
            id = libs.plugins.musicmania.android.test.get().pluginId
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.musicmania.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.musicmania.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
