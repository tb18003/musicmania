import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.musicmania.android.application)
    alias(libs.plugins.musicmania.android.application.compose)
    alias(libs.plugins.musicmania.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

/*
 * Secrets never live in the repository. They are read from (in order):
 *  1. environment variables (CI), 2. the git-ignored local.properties.
 * See local.properties.example and docs/setup/spotify-app-links.md.
 */
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull?.let { load(it.reader()) }
}

fun secret(name: String, default: String = ""): String =
    providers.environmentVariable(name).orNull ?: localProperties.getProperty(name) ?: default

val spotifyClientId = secret("SPOTIFY_CLIENT_ID")
val spotifyRedirectUri = secret("SPOTIFY_REDIRECT_URI", "https://example.invalid/musicmania/callback")
val redirect: URI = URI(spotifyRedirectUri)

// Optional friends leaderboard: empty keeps the app fully local (no ranking tab).
val musicManiaApiUrl = secret("MUSICMANIA_API_URL")

/*
 * One release version for the whole repository: web/package.json (the web app, the API and this app).
 * versionCode is derived from it (MAJOR * 10000 + MINOR * 100 + PATCH), so it grows with every release.
 */
val appVersionName: String = providers.fileContents(rootProject.layout.projectDirectory.file("../web/package.json")).asText
    .map { json ->
        Regex(""""version"\s*:\s*"(\d+)\.(\d+)\.(\d+)"""").find(json)?.destructured?.let { (major, minor, patch) -> "$major.$minor.$patch" }
    }
    .orNull ?: error("web/package.json must hold the release version as MAJOR.MINOR.PATCH")
val appVersionCode: Int = appVersionName.split('.').map(String::toInt).let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "MINOR and PATCH must stay below 100 for versionCode" }
    major * 10_000 + minor * 100 + patch
}

require(spotifyClientId.matches(Regex("[A-Za-z0-9]*"))) { "SPOTIFY_CLIENT_ID must be alphanumeric" }
require(musicManiaApiUrl.isEmpty() || musicManiaApiUrl.startsWith("https://")) { "MUSICMANIA_API_URL must be empty or https://" }
require(redirect.scheme == "https") { "SPOTIFY_REDIRECT_URI must be an https:// App Link (Spotify rejects custom schemes)" }

android {
    namespace = "io.github.tb18003.musicmania"

    defaultConfig {
        applicationId = "io.github.tb18003.musicmania"
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"$spotifyRedirectUri\"")
        buildConfigField("String", "MUSICMANIA_API_URL", "\"$musicManiaApiUrl\"")
        // Used by the verified App Link intent filter that receives the OAuth redirect.
        manifestPlaceholders["redirectHost"] = redirect.host
        manifestPlaceholders["redirectPath"] = redirect.path.ifEmpty { "/" }
        // Required by AppAuth's manifest; our manifest replaces its custom-scheme receiver.
        manifestPlaceholders["appAuthRedirectScheme"] = "io.github.tb18003.musicmania.unused"
    }

    signingConfigs {
        val keystorePath = secret("MUSICMANIA_KEYSTORE_PATH")
        if (keystorePath.isNotBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = secret("MUSICMANIA_KEYSTORE_PASSWORD")
                keyAlias = secret("MUSICMANIA_KEY_ALIAS")
                keyPassword = secret("MUSICMANIA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    packaging {
        resources.excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
}

// The baseline profile plugin adds `nonMinifiedRelease` (profile generation) and `benchmarkRelease`
// (macrobenchmarks) build types. They are local, never-distributed artifacts: without a release
// keystore they fall back to the debug key so they can be installed on a test device.
android.buildTypes.matching { it.name in setOf("nonMinifiedRelease", "benchmarkRelease") }.configureEach {
    if (signingConfig == null) signingConfig = android.signingConfigs.getByName("debug")
}

baselineProfile {
    // Profiles are generated on demand and committed (src/release/generated/baselineProfiles).
    automaticGenerationDuringBuild = false
    // Also generate a startup profile so R8 lays out startup classes in the primary DEX.
    dexLayoutOptimization = true
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.sync.work)
    implementation(projects.widget)

    implementation(projects.feature.auth)
    implementation(projects.feature.home)
    implementation(projects.feature.top)
    implementation(projects.feature.history)
    implementation(projects.feature.detail)
    implementation(projects.feature.insights)
    implementation(projects.feature.leaderboard)
    implementation(projects.feature.recap)
    implementation(projects.feature.importer)
    implementation(projects.feature.settings)

    // The manifest replaces AppAuth's redirect receiver, so the class must be on this classpath.
    implementation(libs.appauth)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Installs the bundled Baseline Profile on devices that don't get it from Play.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.baselineprofile)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)

    // AGP pins the androidTest runtime classpath to the app's resolved versions; androidx.test
    // needs concurrent-futures 1.2, so align the app instead of letting the pin conflict.
    constraints {
        implementation(libs.androidx.concurrent.futures)
        implementation(libs.androidx.concurrent.futures.ktx)
    }

    testImplementation(projects.core.testing)
    testImplementation(libs.konsist)
}
