package io.github.tb18003.musicmania.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** Typed access to the shared `libs` version catalog from convention plugins. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId

/** Builds a stable Android namespace from the Gradle path, e.g. `:core:network` -> `...core.network`. */
fun Project.musicManiaNamespace(): String =
    "io.github.tb18003.musicmania" + path.replace(':', '.').replace('-', '_')
