/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.tb18003.musicmania.core.database.model.ArtistEntity
import io.github.tb18003.musicmania.core.database.model.PlayEventEntity
import io.github.tb18003.musicmania.core.database.model.SnapshotRankRow
import io.github.tb18003.musicmania.core.database.model.TopSnapshotEntity
import io.github.tb18003.musicmania.core.database.model.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MusicManiaDatabaseTest {
    private lateinit var db: MusicManiaDatabase
    private val dayMs = 86_400_000L

    /** Midday timestamps keep day-bucketing assertions independent of the host time zone. */
    private val base = 1_750_000_000_000L - (1_750_000_000_000L % dayMs) + dayMs / 2

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MusicManiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun play(track: String, artist: String, at: Long, ms: Long = 180_000, skipped: Boolean? = null) = PlayEventEntity(
        trackId = null,
        trackName = track,
        artistName = artist,
        albumName = "Album of $artist",
        imageUrl = null,
        playedAt = at,
        msPlayed = ms,
        source = "IMPORT",
        skipped = skipped,
        shuffle = null,
        platform = "Android",
        dedupeKey = PlayEventEntity.dedupeKeyOf(track, artist, at),
    )

    @Test
    fun duplicatePlaysAreIgnored() = runTest {
        val dao = db.playEventDao()
        val first = dao.insertAll(listOf(play("Song", "Band", base)))
        // Same play reported again with millisecond jitter and different casing
        val second = dao.insertAll(listOf(play("song ", "BAND", base + 400), play("Other", "Band", base)))

        assertEquals(1, first.count { it != -1L })
        assertEquals(listOf(-1L), second.take(1))
        assertEquals(2, dao.observeCount().first())
    }

    @Test
    fun summaryCountsOnlyPlaysOver30sAsStreams() = runTest {
        val dao = db.playEventDao()
        dao.insertAll(listOf(play("A", "X", base), play("B", "Y", base + 1_000, ms = 5_000), play("A", "X", base + 2_000)))

        val summary = dao.observeSummary(base - 1, base + dayMs).first()

        assertEquals(365_000L, summary.listenedMs)
        assertEquals(2, summary.streams)
        assertEquals(2, summary.uniqueTracks)
        assertEquals(2, summary.uniqueArtists)
    }

    @Test
    fun topTracksAreOrderedByStreamsAndUseCatalogArtwork() = runTest {
        val dao = db.playEventDao()
        db.catalogDao().upsertTracks(
            listOf(TrackEntity("id-b", "B", emptyList(), null, "Album", "https://img/b", null, 1, false, null)),
        )
        dao.insertAll(
            listOf(
                play("A", "X", base),
                play("B", "X", base + 1_000).copy(trackId = "id-b"),
                play("B", "X", base + 2_000).copy(trackId = "id-b"),
            ),
        )

        val top = dao.observeTopTracks(base - 1, base + dayMs, 10).first()

        assertEquals(listOf("B", "A"), top.map { it.trackName })
        assertEquals("https://img/b", top.first().imageUrl)
    }

    @Test
    fun topArtistsUseArtistImagesByName() = runTest {
        db.catalogDao().upsertArtists(listOf(ArtistEntity("x", "X", "https://img/x", emptyList(), null)))
        db.playEventDao().insertAll(listOf(play("A", "X", base)))

        val top = db.playEventDao().observeTopArtists(base - 1, base + dayMs, 10).first()

        assertEquals("https://img/x", top.single().imageUrl)
    }

    @Test
    fun dailyBucketsAndNewArtists() = runTest {
        val dao = db.playEventDao()
        dao.insertAll(listOf(play("A", "Old", base - 10 * dayMs), play("B", "New", base), play("C", "Old", base + dayMs)))

        val daily = dao.observeDaily(base - dayMs, base + 2 * dayMs).first()
        val newArtists = dao.observeNewArtists(base - dayMs, base + 2 * dayMs).first()

        assertEquals(2, daily.size)
        assertEquals(1, newArtists)
    }

    @Test
    fun habitsIgnoreUnknownValues() = runTest {
        val dao = db.playEventDao()
        dao.insertAll(listOf(play("A", "X", base, skipped = true), play("B", "X", base + 1, skipped = false), play("C", "X", base + 2)))

        val habits = dao.observeHabits(base - 1, base + dayMs).first()

        assertEquals(2, habits.skipKnown)
        assertEquals(1, habits.skipped)
        assertEquals(0, habits.shuffleKnown)
    }

    @Test
    fun snapshotsExposeLatestListAndPreviousRanks() = runTest {
        val catalog = db.catalogDao()
        val snapshots = db.topSnapshotDao()
        catalog.upsertArtists(listOf("a", "b", "c").map { ArtistEntity(it, it.uppercase(), null, emptyList(), null) })
        fun rows(day: Long, ids: List<String>) = ids.mapIndexed { i, id -> TopSnapshotEntity("artist", "short_term", day, i + 1, id, 0) }

        snapshots.replaceSnapshot("artist", "short_term", 100, rows(100, listOf("a", "b")))
        snapshots.replaceSnapshot("artist", "short_term", 101, rows(101, listOf("c", "a")))
        // A second refresh on the same day replaces that day's snapshot
        snapshots.replaceSnapshot("artist", "short_term", 101, rows(101, listOf("b", "a")))

        val latest = snapshots.observeLatestArtists("short_term").first()
        val previous = snapshots.observePreviousRanks("artist", "short_term").first()

        assertEquals(listOf("b", "a"), latest.map { it.artist.id })
        assertEquals(setOf(SnapshotRankRow("a", 1), SnapshotRankRow("b", 2)), previous.toSet())
        assertTrue(snapshots.observePreviousRanks("track", "short_term").first().isEmpty())
    }
}
