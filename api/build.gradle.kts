import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    application
}

group = "io.github.tb18003.musicmania"

// One release version for the whole repository: web/package.json. The Docker build only sees api/,
// so it passes the version explicitly with -PappVersion (see Dockerfile and deploy-api.yml).
val appVersion: Provider<String> = providers.gradleProperty("appVersion").orElse(
    providers.fileContents(layout.projectDirectory.file("../web/package.json")).asText.map { json ->
        Regex(""""version"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: error("No version in web/package.json")
    },
).map { value ->
    require(value.matches(Regex("""\d+\.\d+\.\d+"""))) { "The app version must be MAJOR.MINOR.PATCH, got '$value'" }
    value
}
version = appVersion.get()

// What changed in each release (English and Spanish), shown by the Android update dialog. It lives in
// api/ because the Docker build only sees this directory; release-android.yml reads it too.
val generateBuildInfo by tasks.registering {
    val output = layout.buildDirectory.dir("generated/build-info")
    val versionValue = appVersion
    val releaseNotes = layout.projectDirectory.file("release-notes.json")
    inputs.property("version", versionValue)
    inputs.file(releaseNotes).withPropertyName("releaseNotes").withPathSensitivity(PathSensitivity.NONE)
    outputs.dir(output)
    doLast {
        val directory = output.get().asFile
        directory.mkdirs()
        directory.resolve("musicmania-build.properties").writeText("version=${versionValue.get()}" + System.lineSeparator())
        releaseNotes.asFile.copyTo(directory.resolve("musicmania-release-notes.json"), overwrite = true)
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo)
}

application {
    mainClass.set("io.github.tb18003.musicmania.api.ApplicationKt")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        allWarningsAsErrors = providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

ktor {
    fatJar {
        archiveFileName.set("musicmania-api.jar")
    }
}

dependencies {
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.caffeine)
    runtimeOnly(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.embedded.postgres)
    testImplementation(enforcedPlatform(libs.embedded.postgres.binaries.bom))
}

tasks.test {
    useJUnitPlatform()
    // Integration tests apply the real schema from database/ to an embedded Postgres.
    val migrations = layout.projectDirectory.dir("../database/supabase/migrations")
    inputs.dir(migrations).withPropertyName("migrations").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("musicmania.migrations", migrations.asFile.absolutePath)
}

// Shared configuration with mobile/: Detekt rules, ktlint style and the GPL header live at the repo root.
detekt {
    buildUponDefaultConfig.set(true)
    parallel.set(true)
    config.setFrom(layout.projectDirectory.file("../config/detekt/detekt.yml"))
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val editorConfig = layout.projectDirectory.file("../.editorconfig").asFile
    kotlin {
        target(fileTree("src") { include("**/*.kt") })
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
        licenseHeaderFile(layout.projectDirectory.file("../config/spotless/copyright.kt").asFile)
    }
    kotlinGradle {
        target("*.kts")
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
    }
}
