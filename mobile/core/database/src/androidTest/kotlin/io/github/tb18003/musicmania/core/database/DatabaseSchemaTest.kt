/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Checks the committed schema (`core/database/schemas`) against the entities on real SQLite.
 * When the schema moves to version 2, add `runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)`
 * cases here: user history (synced and imported) must never be lost by an upgrade.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MusicManiaDatabase::class.java)

    @Test
    fun databaseCreatedFromTheExportedSchemaOpensWithTheCurrentEntities() = runTest {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO play_events (trackName, artistName, playedAt, msPlayed, source, dedupeKey)
                VALUES ('Song', 'Artist', 1700000000000, 180000, 'import', 'song|artist|1700000000')
                """.trimIndent(),
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, MusicManiaDatabase::class.java, DB).build()
        try {
            // Room validates the on-disk schema against the entities when it opens the database.
            assertEquals(1, database.playEventDao().observeCount().first())
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB = "schema-test.db"
    }
}
