/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.common.result.DataResult
import io.github.tb18003.musicmania.core.data.importer.DefaultStreamingHistoryImporter
import io.github.tb18003.musicmania.core.data.importer.ImportLimits
import io.github.tb18003.musicmania.core.data.mapper.toEntity
import io.github.tb18003.musicmania.core.data.repository.DefaultDataManagementRepository
import io.github.tb18003.musicmania.core.data.repository.DefaultHistoryRepository
import io.github.tb18003.musicmania.core.data.repository.OfflineFirstTopItemsRepository
import io.github.tb18003.musicmania.core.database.MusicManiaDatabase
import io.github.tb18003.musicmania.core.datastore.prefs.AppStateDataSource
import io.github.tb18003.musicmania.core.domain.repository.ImportException
import io.github.tb18003.musicmania.core.domain.repository.ImportSource
import io.github.tb18003.musicmania.core.model.ImportProgress
import io.github.tb18003.musicmania.core.model.PlaySource
import io.github.tb18003.musicmania.core.model.TimeRange
import io.github.tb18003.musicmania.core.network.model.CursorPagingDto
import io.github.tb18003.musicmania.core.network.model.CursorsDto
import io.github.tb18003.musicmania.core.network.model.PlayHistoryDto
import io.github.tb18003.musicmania.core.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DataLayerIntegrationTest {
    private lateinit var db: MusicManiaDatabase
    private val network = FakeNetworkDataSource()
    private val appState = AppStateDataSource(InMemoryPreferencesDataStore())
    private val clock = MutableClock(Instant.parse("2026-06-15T12:00:00Z"))
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MusicManiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun topRepository() = OfflineFirstTopItemsRepository(network, db, db.catalogDao(), db.topSnapshotDao(), appState, clock)

    private fun historyRepository() = DefaultHistoryRepository(db.playEventDao(), db.catalogDao(), network, appState, clock)

    private fun TestScope.importer(limits: ImportLimits = ImportLimits()) =
        DefaultStreamingHistoryImporter(db.playEventDao(), StandardTestDispatcher(testScheduler), limits)

    private fun source(name: String, bytes: ByteArray) = object : ImportSource {
        override val displayName = name

        override fun openStream() = bytes.inputStream()
    }

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun topRefreshStoresSnapshotsAndComputesPreviousRanks() = runTest(dispatcher) {
        val repository = topRepository()
        network.topTracks = DataResult.Success(listOf(trackDto("a"), trackDto("b")))
        network.topArtists = DataResult.Success(listOf(artistDto("x")))
        repository.refresh(TimeRange.FOUR_WEEKS)

        clock.now = clock.now.plus(Duration.ofDays(1))
        network.topTracks = DataResult.Success(listOf(trackDto("b"), trackDto("c")))
        repository.refresh(TimeRange.FOUR_WEEKS)

        val tracks = repository.observeTopTracks(TimeRange.FOUR_WEEKS).first()
        assertEquals(listOf("b", "c"), tracks.items.map { it.id })
        assertEquals(mapOf("a" to 1, "b" to 2), tracks.previousRanks)
        assertEquals(listOf("x"), repository.observeTopArtists(TimeRange.FOUR_WEEKS).first().items.map { it.id })
    }

    @Test
    fun topRefreshPropagatesErrors() = runTest(dispatcher) {
        network.topTracks = DataResult.Failure(AppError.Forbidden)
        assertEquals(DataResult.Failure(AppError.Forbidden), topRepository().refresh(TimeRange.SIX_MONTHS))
    }

    @Test
    fun syncStoresPlaysAndAdvancesCursor() = runTest(dispatcher) {
        val repository = historyRepository()
        network.recentlyPlayed = DataResult.Success(
            CursorPagingDto(
                items = listOf(
                    PlayHistoryDto(trackDto("a"), "2026-06-15T10:00:00.123Z"),
                    PlayHistoryDto(trackDto("b"), "2026-06-15T10:05:00Z"),
                ),
                cursors = CursorsDto(after = "1781517900000"),
            ),
        )

        assertEquals(DataResult.Success(2), repository.syncRecentlyPlayed())
        // Same page again: nothing new is inserted, cursor is reused
        assertEquals(DataResult.Success(0), repository.syncRecentlyPlayed())

        assertEquals(listOf(null, 1_781_517_900_000L), network.recentlyPlayedCursors)
        assertEquals(2, repository.observeEventCount().first())
        assertEquals(clock.now, appState.lastSuccessfulSync.first())
    }

    @Test
    fun importsJsonAndZipAndDeduplicates() = runTest(dispatcher) {
        val extended = """[{"ts":"2024-01-01T10:00:00Z","ms_played":200000,"master_metadata_track_name":"Song",
            "master_metadata_album_artist_name":"Band","platform":"iOS 17"}]"""
        val basic = """[{"endTime":"2023-05-05 08:00","artistName":"Other","trackName":"Tune","msPlayed":90000}]"""
        val archive = zip(
            "Spotify Extended Streaming History/Streaming_History_Audio_2024_0.json" to extended,
            "Spotify Extended Streaming History/Streaming_History_Video_2024.json" to "not json at all",
            "MyData/StreamingHistory_music_0.json" to basic,
        )

        val progress = importer().import(listOf(source("my_spotify_data.zip", archive))).toList()
        val result = assertIs<ImportProgress.Completed>(progress.last()).result

        assertEquals(2, result.filesProcessed)
        assertEquals(2, result.eventsInserted)
        assertEquals(Instant.parse("2023-05-05T08:00:00Z"), result.earliest)

        val again = importer().import(listOf(source("Streaming_History_Audio_2024_0.json", extended.toByteArray()))).last()
        assertEquals(0, (again as ImportProgress.Completed).result.eventsInserted)
        assertEquals(1, again.result.duplicates)
    }

    @Test
    fun musicManiaExportsImportOnAnotherDevice() = runTest(dispatcher) {
        val plays = listOf(
            TestData.play(track = "Night Drive", artist = "Nova Lights", playedAt = Instant.parse("2026-06-01T20:00:00Z"))
                .copy(trackId = "NightDrive", imageUrl = "https://i.scdn.co/image/night", source = PlaySource.SYNC),
            TestData.play(track = "Low Tide", artist = "Coral Echo", playedAt = Instant.parse("2025-01-02T08:30:00Z"), skipped = true),
        )
        db.playEventDao().insertAll(plays.map { it.toEntity() })
        val exported = ByteArrayOutputStream()
        val export = DefaultDataManagementRepository(db, db.playEventDao(), appState, StandardTestDispatcher(testScheduler))
        assertEquals(DataResult.Success(2), export.exportHistory(exported))

        // The other phone: an empty database importing the file.
        db.clearAllTables()
        val result = importer().import(listOf(source("musicmania-history.json", exported.toByteArray()))).toList()
            .let { assertIs<ImportProgress.Completed>(it.last()).result }

        assertEquals(2, result.eventsInserted)
        val restored = db.playEventDao().page(limit = 10, offset = 0).associateBy { it.trackName }
        val night = restored.getValue("Night Drive")
        assertEquals(Instant.parse("2026-06-01T20:00:00Z").toEpochMilli(), night.playedAt)
        assertEquals("NightDrive", night.trackId)
        assertEquals("https://i.scdn.co/image/night", night.imageUrl)
        assertEquals("Album", night.albumName)
        assertEquals(true, restored.getValue("Low Tide").skipped)

        val again = importer().import(listOf(source("musicmania-history.json", exported.toByteArray()))).last()
        assertEquals(0, assertIs<ImportProgress.Completed>(again).result.eventsInserted, "importing twice adds nothing")
    }

    @Test
    fun rejectsInvalidFiles() = runTest(dispatcher) {
        val error = assertFailsWith<ImportException> {
            importer().import(listOf(source("notes.json", "{\"hello\":1}".toByteArray()))).toList()
        }
        assertIs<AppError.InvalidData>(error.error)

        assertFailsWith<ImportException> {
            importer().import(listOf(source("empty.zip", zip("readme.txt" to "hi")))).toList()
        }
    }

    @Test
    fun zipBombsAreRejected() = runTest(dispatcher) {
        val huge = "[" + List(2_000) { """{"endTime":"2023-05-05 08:00","artistName":"A","trackName":"T$it","msPlayed":1}""" }
            .joinToString(",") + "]"
        val archive = zip("StreamingHistory0.json" to huge)

        assertFailsWith<ImportException> {
            importer(ImportLimits(maxEntryBytes = 1_024)).import(listOf(source("bomb.zip", archive))).toList()
        }
        assertNull(historyRepository().observeHistoryStart().first())
    }
}
