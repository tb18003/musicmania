/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue as assertThat

/**
 * Executable architecture rules (see CLAUDE.md §3). A failing test here means a change
 * broke a layering rule — fix the dependency, don't relax the test.
 */
class ArchitectureTest {
    private val production = moduleSources()
    private val base = "io.github.tb18003.musicmania"
    private val featureRoot = "$base.feature"

    private val featureFiles = production.files.filter { it.packagee?.name?.startsWith("$featureRoot.") == true }

    @Test
    fun `scope covers the sources of every layer`() {
        val packages = production.files.mapNotNull { it.packagee?.name }.toSet()
        listOf("$base.core.domain", "$base.core.data", "$featureRoot.top", "$base.widget").forEach { prefix ->
            assertThat(packages.any { it.startsWith(prefix) }, "No sources scanned under $prefix")
        }
    }

    @Test
    fun `features do not depend on other features`() {
        featureFiles.assertTrue { file ->
            val own = file.packagee!!.name.removePrefix("$featureRoot.").substringBefore('.')
            file.imports.none { it.name.startsWith("$featureRoot.") && !it.name.startsWith("$featureRoot.$own.") }
        }
    }

    @Test
    fun `features never touch the data layer`() {
        val forbidden = listOf("$base.core.data.", "$base.core.network.", "$base.core.database.", "$base.core.datastore.")
        featureFiles.assertTrue { file -> file.imports.none { import -> forbidden.any { import.name.startsWith(it) } } }
    }

    @Test
    fun `only the auth feature may use core auth`() {
        featureFiles
            .filterNot { it.packagee!!.name.startsWith("$featureRoot.auth") }
            .assertTrue { file -> file.imports.none { it.name.startsWith("$base.core.auth.") } }
    }

    @Test
    fun `domain layer is platform independent`() {
        production.files
            .filter { it.packagee?.name?.startsWith("$base.core.domain") == true }
            .assertTrue { file ->
                file.imports.none {
                    it.name.startsWith("android.") ||
                        (it.name.startsWith("androidx.") && !it.name.startsWith("androidx.paging."))
                }
            }
    }

    @Test
    fun `use cases live in the domain usecase package`() {
        production.classes().withNameEndingWith("UseCase").assertTrue { it.resideInPackage("$base.core.domain.usecase..") }
    }

    @Test
    fun `view models live in feature or app packages`() {
        production.classes().withNameEndingWith("ViewModel").assertTrue {
            it.resideInPackage("$featureRoot..") || it.resideInPackage(base)
        }
    }

    @Test
    fun `repository implementations are internal to the data layer`() {
        production.classes()
            .filter { it.resideInPackage("$base.core.data..") && it.name.endsWith("Repository") }
            .assertTrue { it.hasInternalModifier }
    }

    private companion object {
        private const val MODULE_SOURCES_DEPTH = 4 // e.g. core/data/src/main

        /**
         * Production sources of every module (`<module>/src/main`). Unlike `scopeFromProduction()`
         * this never walks `build/` folders, which other Gradle tasks rewrite while tests run in
         * parallel (a directory vanishing mid-walk made the scan fail intermittently).
         */
        fun moduleSources(): KoScope {
            // Android unit tests run with the module directory (app/) as working directory.
            val root = File(System.getProperty("user.dir")).parentFile
            return root.walkTopDown()
                .maxDepth(MODULE_SOURCES_DEPTH)
                .onEnter { it == root || (it.name != "build" && it.name != "build-logic" && !it.name.startsWith(".")) }
                .filter { it.name == "main" && it.parentFile.name == "src" }
                .map { Konsist.scopeFromDirectory(it.relativeTo(root).invariantSeparatorsPath) }
                .reduce(KoScope::plus)
        }
    }
}
