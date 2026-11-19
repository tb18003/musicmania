/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.demo

import androidx.room.withTransaction
import io.github.tb18003.musicmania.core.common.di.Dispatcher
import io.github.tb18003.musicmania.core.common.di.MusicManiaDispatchers
import io.github.tb18003.musicmania.core.database.MusicManiaDatabase
import io.github.tb18003.musicmania.core.database.model.ArtistEntity
import io.github.tb18003.musicmania.core.database.model.ArtistRef
import io.github.tb18003.musicmania.core.database.model.PlayEventEntity
import io.github.tb18003.musicmania.core.database.model.TopSnapshotEntity
import io.github.tb18003.musicmania.core.database.model.TrackEntity
import io.github.tb18003.musicmania.core.model.AlbumSummary
import io.github.tb18003.musicmania.core.model.ArtistSummary
import io.github.tb18003.musicmania.core.model.NowPlaying
import io.github.tb18003.musicmania.core.model.PlaySource
import io.github.tb18003.musicmania.core.model.Track
import io.github.tb18003.musicmania.core.network.SpotifyTimeRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.pow
import kotlin.random.Random

/**
 * Fictional catalogue used by demo mode so the whole app can be explored (and
 * screenshot-tested) without a Spotify account. Names are invented on purpose.
 */
internal object DemoCatalog {
    private val artistNames = listOf(
        "Neon Harbor", "Velvet Static", "Lunar Arcade", "Paper Comets", "The Quiet Tides", "Saffron Days",
        "Glass Animals Club", "Midnight Orchard", "Coral Engines", "Ivory Signals", "Polar Bloom", "Echo Atlas",
        "Sundown Ritual", "Copper Skies", "Hollow Lanterns", "Static Gardens", "Northbound Kids", "Amber Wires",
        "Silk Thunder", "Mono Lake", "Digital Fern", "The Low Planets", "Rosewater Avenue", "Kite Theory",
    )
    private val genres = listOf("indie pop", "dream pop", "synthwave", "alt rock", "lo-fi", "electronica", "folk", "neo soul", "shoegaze")
    private val words = listOf(
        "Afterglow", "Gravity", "Satellites", "Golden Hour", "Paper Hearts", "Neon Rain", "Static", "Wildfire", "Echoes",
        "Daydream", "Low Tide", "Northern Lights", "Velvet", "Overdrive", "Slow Motion", "Supernova", "Lanterns", "Horizon",
        "Mirage", "Weightless", "Polaroid", "Wavelength", "Sunday Drive", "Fever", "Comet", "Late Night", "Blue Hour",
    )

    data class Seed(val artists: List<ArtistEntity>, val tracks: List<TrackEntity>)

    fun build(random: Random = Random(SEED)): Seed {
        val artists = artistNames.mapIndexed { i, name ->
            ArtistEntity(
                id = "demo-artist-$i",
                name = name,
                imageUrl = null,
                genres = genres.shuffled(random).take(2),
                uri = null,
            )
        }
        val tracks = artists.flatMapIndexed { ai, artist ->
            val album = "${words.random(random)} ${listOf("Sessions", "Tapes", "EP", "", "Vol. 2").random(random)}".trim()
            (0 until TRACKS_PER_ARTIST).map { ti ->
                TrackEntity(
                    id = "demo-track-$ai-$ti",
                    name = words.random(random) + if (random.nextInt(4) == 0) " (Reprise)" else "",
                    artists = listOf(ArtistRef(artist.id, artist.name)),
                    albumId = "demo-album-$ai",
                    albumName = album,
                    imageUrl = null,
                    releaseDate = "${2015 + random.nextInt(11)}-01-01",
                    durationMs = 150_000L + random.nextLong(120_000),
                    explicit = false,
                    uri = null,
                )
            }
        }
        return Seed(artists, tracks)
    }

    const val SEED = 42
    const val TRACKS_PER_ARTIST = 6
}

internal class DemoDataSeeder @Inject constructor(
    private val db: MusicManiaDatabase,
    private val clock: Clock,
    @param:Dispatcher(MusicManiaDispatchers.IO) private val io: CoroutineDispatcher,
) {
    suspend fun seed() = withContext(io) {
        val random = Random(DemoCatalog.SEED)
        val seed = DemoCatalog.build(random)
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val events = generatePlays(seed, today, zone, random)

        db.clearAllTables() // not allowed inside a transaction
        db.withTransaction {
            db.catalogDao().upsertArtists(seed.artists)
            db.catalogDao().upsertTracks(seed.tracks)
            events.chunked(BATCH).forEach { db.playEventDao().insertAll(it) }
            writeSnapshots(seed, today, random)
        }
    }

    /** Zipf-like artist popularity + an evening-heavy daily rhythm makes charts look real. */
    private fun generatePlays(seed: DemoCatalog.Seed, today: LocalDate, zone: ZoneId, random: Random): List<PlayEventEntity> {
        val weights = seed.artists.indices.map { 1.0 / (it + 1.0).pow(ZIPF_EXPONENT) }
        val hourWeights = listOf(
            2.0, 1.0, 0.5, 0.2, 0.1, 0.2, 0.8, 2.0, 3.0, 2.5, 2.0, 2.2,
            2.8, 2.5, 2.2, 2.4, 3.0, 3.6, 4.2, 4.8, 5.0, 4.5, 3.8, 3.0,
        )
        val events = ArrayList<PlayEventEntity>()
        for (dayOffset in DAYS downTo 0) {
            val day = today.minusDays(dayOffset.toLong())
            if (random.nextInt(100) < SKIP_DAY_PERCENT) continue
            repeat(MIN_PLAYS + random.nextInt(EXTRA_PLAYS)) {
                val artistIndex = pickWeighted(weights, random)
                val track = seed.tracks[artistIndex * DemoCatalog.TRACKS_PER_ARTIST + random.nextInt(DemoCatalog.TRACKS_PER_ARTIST)]
                val hour = pickWeighted(hourWeights, random)
                val playedAt = day.atTime(hour, random.nextInt(60), random.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
                if (playedAt > clock.millis()) return@repeat
                val skipped = random.nextInt(100) < SKIP_PERCENT
                val ms = if (skipped) 10_000L + random.nextLong(25_000) else track.durationMs
                events += PlayEventEntity(
                    trackId = track.id,
                    trackName = track.name,
                    artistName = track.artists.first().name,
                    albumName = track.albumName,
                    imageUrl = null,
                    playedAt = playedAt,
                    msPlayed = ms,
                    source = PlaySource.DEMO.name,
                    skipped = skipped,
                    shuffle = random.nextBoolean(),
                    platform = listOf("Android", "Android", "Android", "Web", "Smart speaker", "Windows").random(random),
                    dedupeKey = PlayEventEntity.dedupeKeyOf(track.name, track.artists.first().name, playedAt) + "|" + track.id,
                )
            }
        }
        return events
    }

    private suspend fun writeSnapshots(seed: DemoCatalog.Seed, today: LocalDate, random: Random) {
        val dao = db.topSnapshotDao()
        val now = clock.millis()
        SpotifyTimeRange.entries.forEachIndexed { rangeIndex, range ->
            // Yesterday + today, so movement arrows have something to compare with.
            listOf(1L, 0L).forEach { daysAgo ->
                val day = today.minusDays(daysAgo).toEpochDay()
                val artistOrder = seed.artists.indices.sortedBy { it + random.nextInt(-2 - rangeIndex, 3 + rangeIndex) }
                val trackOrder = artistOrder.flatMap { a ->
                    (0 until 2).map { seed.tracks[a * DemoCatalog.TRACKS_PER_ARTIST + (it + rangeIndex) % DemoCatalog.TRACKS_PER_ARTIST] }
                }
                dao.replaceSnapshot(
                    TopSnapshotEntity.TYPE_ARTIST,
                    range.apiValue,
                    day,
                    artistOrder.mapIndexed { i, a ->
                        TopSnapshotEntity(TopSnapshotEntity.TYPE_ARTIST, range.apiValue, day, i + 1, seed.artists[a].id, now)
                    },
                )
                dao.replaceSnapshot(
                    TopSnapshotEntity.TYPE_TRACK,
                    range.apiValue,
                    day,
                    trackOrder.take(TOP_SIZE)
                        .mapIndexed { i, t -> TopSnapshotEntity(TopSnapshotEntity.TYPE_TRACK, range.apiValue, day, i + 1, t.id, now) },
                )
            }
        }
    }

    private fun pickWeighted(weights: List<Double>, random: Random): Int {
        var r = random.nextDouble() * weights.sum()
        weights.forEachIndexed { i, w ->
            r -= w
            if (r <= 0) return i
        }
        return weights.lastIndex
    }

    private companion object {
        const val DAYS = 420
        const val MIN_PLAYS = 8
        const val EXTRA_PLAYS = 40
        const val SKIP_DAY_PERCENT = 6
        const val SKIP_PERCENT = 18
        const val ZIPF_EXPONENT = 0.9
        const val BATCH = 1_000
        const val TOP_SIZE = 50
    }
}

/** Simulated "now playing" for demo mode: rotates through demo tracks in real time. */
internal class DemoPlayback @Inject constructor(private val clock: Clock) {
    private val tracks by lazy { DemoCatalog.build().tracks }

    fun current(): NowPlaying {
        val cycle = tracks.sumOf { it.durationMs }
        var position = clock.millis() % cycle
        val entity = tracks.first { track ->
            (position < track.durationMs).also { if (!it) position -= track.durationMs }
        }
        return NowPlaying(
            track = Track(
                id = entity.id,
                name = entity.name,
                artists = entity.artists.map { ArtistSummary(it.id, it.name) },
                album = AlbumSummary(entity.albumId, entity.albumName, null),
                durationMs = entity.durationMs,
            ),
            progressMs = position,
            isPlaying = true,
            fetchedAt = clock.instant(),
        )
    }
}
