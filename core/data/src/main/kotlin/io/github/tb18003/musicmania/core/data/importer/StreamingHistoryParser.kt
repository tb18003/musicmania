/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.importer

import io.github.tb18003.musicmania.core.model.PlayEvent
import io.github.tb18003.musicmania.core.model.PlaySource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Union of both Spotify export formats:
 *  - Extended streaming history (`Streaming_History_Audio_*.json`, legacy `endsong_*.json`)
 *  - Basic "account data" history (`StreamingHistory*.json`, `StreamingHistory_music_*.json`)
 *
 * Privacy: fields such as `ip_addr`, `conn_country` or `user_agent_decrypted` are
 * deliberately not mapped, so they are never read into memory or stored.
 */
@Serializable
internal data class RawStreamingEntry(
    val ts: String? = null,
    val endTime: String? = null,
    @SerialName("ms_played") val msPlayedExtended: Long? = null,
    val msPlayed: Long? = null,
    @SerialName("master_metadata_track_name") val trackNameExtended: String? = null,
    val trackName: String? = null,
    @SerialName("master_metadata_album_artist_name") val artistNameExtended: String? = null,
    val artistName: String? = null,
    @SerialName("master_metadata_album_album_name") val albumName: String? = null,
    @SerialName("spotify_track_uri") val trackUri: String? = null,
    val platform: String? = null,
    val shuffle: Boolean? = null,
    val skipped: Boolean? = null,
    @SerialName("reason_end") val reasonEnd: String? = null,
)

internal object StreamingHistoryParser {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = false
    }
    private val basicFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private const val TRACK_URI_PREFIX = "spotify:track:"

    /** Lazily decodes a JSON array without loading the whole file into memory. */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(input: InputStream): Sequence<RawStreamingEntry> =
        json.decodeToSequence(input, RawStreamingEntry.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)

    /** Maps an entry to a play, or null for podcasts/audiobooks/malformed rows. */
    fun toPlayEvent(entry: RawStreamingEntry): PlayEvent? {
        val track = (entry.trackNameExtended ?: entry.trackName)?.takeIf { it.isNotBlank() }
        val artist = (entry.artistNameExtended ?: entry.artistName)?.takeIf { it.isNotBlank() }
        val playedAt = parseInstant(entry)
        val msPlayed = entry.msPlayedExtended ?: entry.msPlayed
        if (track == null || artist == null || playedAt == null || msPlayed == null) return null
        return PlayEvent(
            trackId = entry.trackUri?.takeIf { it.startsWith(TRACK_URI_PREFIX) }?.removePrefix(TRACK_URI_PREFIX),
            trackName = track,
            artistName = artist,
            albumName = entry.albumName,
            imageUrl = null,
            playedAt = playedAt,
            msPlayed = msPlayed.coerceAtLeast(0),
            source = PlaySource.IMPORT,
            skipped = entry.skipped ?: entry.reasonEnd?.let { it == "fwdbtn" },
            shuffle = entry.shuffle,
            platform = PlatformNormalizer.normalize(entry.platform),
        )
    }

    private fun parseInstant(entry: RawStreamingEntry): Instant? = try {
        when {
            entry.ts != null -> Instant.parse(entry.ts)
            // Basic export: UTC, minute precision
            entry.endTime != null -> LocalDateTime.parse(entry.endTime, basicFormat).toInstant(ZoneOffset.UTC)
            else -> null
        }
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Collapses Spotify's verbose platform strings into a few readable buckets. */
internal object PlatformNormalizer {
    @Suppress("CyclomaticComplexMethod")
    fun normalize(raw: String?): String? {
        val p = raw?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            // Web player strings also mention the host OS, so check them first.
            "web" in p -> "Web"
            "android" in p -> "Android"
            "ios" in p || "iphone" in p || "ipad" in p -> "iOS"
            "windows" in p -> "Windows"
            "os x" in p || "macos" in p || "osx" in p -> "macOS"
            "linux" in p -> "Linux"
            "cast" in p -> "Cast"
            "sonos" in p || "speaker" in p || "echo" in p || "alexa" in p -> "Smart speaker"
            "tv" in p || "playstation" in p || "xbox" in p -> "TV & consoles"
            "car" in p || "auto" in p -> "Car"
            else -> "Other"
        }
    }
}
