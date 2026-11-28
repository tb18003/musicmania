/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.importer

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingHistoryParserTest {
    private fun parse(json: String) = StreamingHistoryParser.decode(json.byteInputStream()).toList()

    @Test
    fun `extended entries map to plays with ids, platform and skip`() {
        val entries = parse(
            """
            [{"ts":"2024-05-01T21:15:00Z","platform":"Android OS 14 API 34 (Google, Pixel 8)","ms_played":201000,
              "conn_country":"ES","ip_addr":"203.0.113.7","master_metadata_track_name":"Song",
              "master_metadata_album_artist_name":"Band","master_metadata_album_album_name":"LP",
              "spotify_track_uri":"spotify:track:abc","reason_end":"fwdbtn","shuffle":true,"skipped":null}]
            """,
        )

        val play = StreamingHistoryParser.toPlayEvent(entries.single())!!

        assertEquals("abc", play.trackId)
        assertEquals(Instant.parse("2024-05-01T21:15:00Z"), play.playedAt)
        assertEquals(201_000, play.msPlayed)
        assertEquals("Android", play.platform)
        assertEquals(true, play.skipped)
        assertEquals(true, play.shuffle)
    }

    @Test
    fun `MusicMania exports from the app and from the web are read`() {
        val android = parse(
            """
            [{"playedAt":"2026-06-01T20:00:00Z","trackName":"Night Drive","artistName":"Nova Lights","albumName":"Neon",
              "imageUrl":"https://i.scdn.co/image/night","msPlayed":180000,"spotifyTrackId":"NightDrive","source":"SYNC",
              "skipped":false,"shuffle":true,"platform":"Android"}]
            """,
        ).single().let { StreamingHistoryParser.toPlayEvent(it)!! }
        assertEquals(Instant.parse("2026-06-01T20:00:00Z"), android.playedAt)
        assertEquals("NightDrive", android.trackId)
        assertEquals("Neon", android.albumName)
        assertEquals("https://i.scdn.co/image/night", android.imageUrl)
        assertEquals("Android", android.platform)

        val web = parse(
            """
            [{"trackId":"LowTide","trackName":"Low Tide","artistName":"Coral Echo","albumName":null,"imageUrl":"http://insecure/x",
              "playedAt":1748808000000,"msPlayed":120000,"source":"IMPORT","skipped":null,"shuffle":null,"platform":"Smart speaker"}]
            """,
        ).single().let { StreamingHistoryParser.toPlayEvent(it)!! }
        assertEquals(Instant.ofEpochMilli(1_748_808_000_000), web.playedAt)
        assertEquals("LowTide", web.trackId)
        assertNull(web.imageUrl, "only https covers are kept")
        assertEquals("Smart speaker", web.platform)
    }

    @Test
    fun `MusicMania entries without a valid time or id are handled safely`() {
        val entries = parse(
            """
            [{"playedAt":"yesterday","trackName":"A","artistName":"B","msPlayed":1},
             {"playedAt":-5,"trackName":"A","artistName":"B","msPlayed":1},
             {"playedAt":"2026-06-01T20:00:00Z","trackName":"A","artistName":"B","msPlayed":1,"trackId":"../../etc"}]
            """,
        )
        assertNull(StreamingHistoryParser.toPlayEvent(entries[0]))
        assertNull(StreamingHistoryParser.toPlayEvent(entries[1]))
        assertNull(StreamingHistoryParser.toPlayEvent(entries[2])!!.trackId)
    }

    @Test
    fun `basic format is parsed as UTC`() {
        val entries = parse("""[{"endTime":"2023-01-02 03:04","artistName":"Band","trackName":"Song","msPlayed":1234}]""")

        val play = StreamingHistoryParser.toPlayEvent(entries.single())!!

        assertEquals(Instant.parse("2023-01-02T03:04:00Z"), play.playedAt)
        assertNull(play.trackId)
        assertNull(play.platform)
    }

    @Test
    fun `podcast episodes are skipped`() {
        val entries = parse("""[{"ts":"2024-05-01T21:15:00Z","ms_played":1000,"master_metadata_track_name":null,"episode_name":"Ep 1"}]""")
        assertNull(StreamingHistoryParser.toPlayEvent(entries.single()))
    }

    @Test
    fun `platform strings are normalised`() {
        assertEquals("iOS", PlatformNormalizer.normalize("iOS 17.1 (iPhone15,2)"))
        assertEquals("Windows", PlatformNormalizer.normalize("Windows 10 (10.0.19045; x64)"))
        assertEquals("macOS", PlatformNormalizer.normalize("OS X 14.0 [arm 2]"))
        assertEquals("Web", PlatformNormalizer.normalize("web_player linux chrome"))
        assertEquals("Other", PlatformNormalizer.normalize("Partner foo"))
        assertNull(PlatformNormalizer.normalize(""))
    }

    @Test
    fun `only streaming history files are considered`() {
        assertTrue(
            DefaultStreamingHistoryImporter.isHistoryFile("Spotify Extended Streaming History/Streaming_History_Audio_2020-2021_0.json"),
        )
        assertTrue(DefaultStreamingHistoryImporter.isHistoryFile("MyData/StreamingHistory_music_0.json"))
        assertTrue(DefaultStreamingHistoryImporter.isHistoryFile("endsong_3.json"))
        assertFalse(DefaultStreamingHistoryImporter.isHistoryFile("Spotify Extended Streaming History/Streaming_History_Video_2021.json"))
        assertFalse(DefaultStreamingHistoryImporter.isHistoryFile("../../evil/StreamingHistory0.json"))
        assertFalse(DefaultStreamingHistoryImporter.isHistoryFile("Userdata.json"))
    }
}
