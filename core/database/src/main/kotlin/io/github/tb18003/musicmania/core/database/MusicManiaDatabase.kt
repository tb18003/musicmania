/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.github.tb18003.musicmania.core.database.dao.CatalogDao
import io.github.tb18003.musicmania.core.database.dao.PlayEventDao
import io.github.tb18003.musicmania.core.database.dao.TopSnapshotDao
import io.github.tb18003.musicmania.core.database.model.AlbumEntity
import io.github.tb18003.musicmania.core.database.model.ArtistEntity
import io.github.tb18003.musicmania.core.database.model.ArtistRef
import io.github.tb18003.musicmania.core.database.model.PlayEventEntity
import io.github.tb18003.musicmania.core.database.model.TopSnapshotEntity
import io.github.tb18003.musicmania.core.database.model.TrackEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Schema changes MUST bump [version] and ship a tested migration (schemas are exported
 * to `core/database/schemas`). Destructive migrations would wipe imported history.
 */
@Database(
    entities = [
        TrackEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        TopSnapshotEntity::class,
        PlayEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MusicManiaDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun topSnapshotDao(): TopSnapshotDao

    abstract fun playEventDao(): PlayEventDao

    companion object {
        const val NAME = "musicmania.db"
    }
}

internal class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringList: KSerializer<List<String>> = ListSerializer(String.serializer())
    private val artistList: KSerializer<List<ArtistRef>> = ListSerializer(ArtistRef.serializer())

    @TypeConverter
    fun stringsToJson(value: List<String>): String = json.encodeToString(stringList, value)

    @TypeConverter
    fun jsonToStrings(value: String): List<String> = json.decodeFromString(stringList, value)

    @TypeConverter
    fun artistsToJson(value: List<ArtistRef>): String = json.encodeToString(artistList, value)

    @TypeConverter
    fun jsonToArtists(value: String): List<ArtistRef> = json.decodeFromString(artistList, value)
}
