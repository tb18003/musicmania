/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.data.importer

import io.github.tb18003.musicmania.core.common.di.Dispatcher
import io.github.tb18003.musicmania.core.common.di.MusicManiaDispatchers
import io.github.tb18003.musicmania.core.common.result.AppError
import io.github.tb18003.musicmania.core.data.mapper.toEntity
import io.github.tb18003.musicmania.core.database.dao.PlayEventDao
import io.github.tb18003.musicmania.core.domain.repository.ImportException
import io.github.tb18003.musicmania.core.domain.repository.ImportSource
import io.github.tb18003.musicmania.core.domain.repository.StreamingHistoryImporter
import io.github.tb18003.musicmania.core.model.ImportProgress
import io.github.tb18003.musicmania.core.model.ImportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.inject.Inject

/** Zip-bomb guards; injectable so tests can use tiny limits. */
internal data class ImportLimits(
    val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
    val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    val maxZipEntries: Int = DEFAULT_MAX_ZIP_ENTRIES,
) {
    @Inject
    constructor() : this(maxEntryBytes = DEFAULT_MAX_ENTRY_BYTES)

    private companion object {
        const val MIB = 1024L * 1024L
        const val DEFAULT_MAX_ENTRY_BYTES = 256 * MIB
        const val DEFAULT_MAX_TOTAL_BYTES = 4 * 1024 * MIB
        const val DEFAULT_MAX_ZIP_ENTRIES = 2_000
    }
}

/**
 * Imports Spotify data exports (a `.zip` straight from Spotify or the extracted
 * `.json` files). Defensive by design: files come from the user's storage and are
 * treated as untrusted input.
 *  - read through the Storage Access Framework (no storage permission needed);
 *  - nothing is extracted to disk, so path traversal ("zip slip") is impossible;
 *  - per-entry and total uncompressed size limits plus an entry cap defeat zip bombs;
 *  - JSON is streamed, never fully loaded into memory.
 */
internal class DefaultStreamingHistoryImporter @Inject constructor(
    private val dao: PlayEventDao,
    @param:Dispatcher(MusicManiaDispatchers.IO) private val io: CoroutineDispatcher,
    private val limits: ImportLimits,
) : StreamingHistoryImporter {
    private class Counters {
        var files = 0
        var read = 0
        var inserted = 0
        var skipped = 0
        var totalBytes = 0L
        var earliest: Instant? = null
        var latest: Instant? = null
    }

    override fun import(sources: List<ImportSource>): Flow<ImportProgress> = flow {
        val counters = Counters()
        for (source in sources) {
            try {
                source.openStream().use { raw ->
                    val input = BufferedInputStream(raw)
                    if (input.isZip()) importZip(input, counters) else importJson(source.displayName, input, counters)
                }
            } catch (e: ZipException) {
                throw ImportException(AppError.InvalidData("Corrupted archive: ${e.message}"), e)
            } catch (e: IOException) {
                throw ImportException(AppError.Storage, e)
            }
        }
        if (counters.files == 0) throw ImportException(AppError.InvalidData("No streaming history files found"))
        emit(
            ImportProgress.Completed(
                ImportResult(counters.files, counters.read, counters.inserted, counters.skipped, counters.earliest, counters.latest),
            ),
        )
    }.flowOn(io)

    private suspend fun FlowCollector<ImportProgress>.importZip(input: InputStream, counters: Counters) {
        ZipInputStream(input).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > limits.maxZipEntries) throw ImportException(AppError.InvalidData("Too many files in archive"))
                val name = entry.name
                if (!entry.isDirectory && isHistoryFile(name)) {
                    importJson(name.substringAfterLast('/'), NonClosingBoundedStream(zip, limits, counters), counters)
                }
                zip.closeEntry()
            }
        }
    }

    private suspend fun FlowCollector<ImportProgress>.importJson(name: String, input: InputStream, counters: Counters) {
        counters.files++
        emit(ImportProgress.Running(name, counters.files - 1, counters.read))
        try {
            StreamingHistoryParser.decode(input).chunked(BATCH_SIZE).forEach { batch ->
                insertBatch(batch, counters)
                emit(ImportProgress.Running(name, counters.files - 1, counters.read))
            }
        } catch (e: SerializationException) {
            throw ImportException(AppError.InvalidData("$name is not a Spotify streaming history file"), e)
        } catch (e: IllegalArgumentException) {
            throw ImportException(AppError.InvalidData("$name is not a Spotify streaming history file"), e)
        }
    }

    private suspend fun insertBatch(batch: List<RawStreamingEntry>, counters: Counters) {
        val events = batch.mapNotNull { raw -> StreamingHistoryParser.toPlayEvent(raw).also { if (it == null) counters.skipped++ } }
        counters.read += events.size
        events.minOfOrNull { it.playedAt }?.let { min -> if (counters.earliest?.isAfter(min) != false) counters.earliest = min }
        events.maxOfOrNull { it.playedAt }?.let { max -> if (counters.latest?.isBefore(max) != false) counters.latest = max }
        counters.inserted += dao.insertAll(events.map { it.toEntity() }).count { it != -1L }
    }

    /** Reads entries "through" the zip stream while enforcing size limits. */
    private class NonClosingBoundedStream(input: InputStream, private val limits: ImportLimits, private val counters: Counters) :
        FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) account(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) account(it.toLong()) }

        private fun account(bytes: Long) {
            count += bytes
            counters.totalBytes += bytes
            if (count > limits.maxEntryBytes || counters.totalBytes > limits.maxTotalBytes) {
                throw ImportException(AppError.InvalidData("Archive entry exceeds the size limit"))
            }
        }

        override fun close() = Unit
    }

    companion object {
        private const val BATCH_SIZE = 1_000
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        internal fun isHistoryFile(path: String): Boolean {
            if (".." in path || !path.endsWith(".json", ignoreCase = true)) return false
            val name = path.substringAfterLast('/')
            return name.startsWith("Streaming_History_Audio", ignoreCase = true) ||
                name.startsWith("StreamingHistory", ignoreCase = true) ||
                name.startsWith("endsong", ignoreCase = true)
        }

        private fun BufferedInputStream.isZip(): Boolean {
            mark(ZIP_MAGIC.size)
            val header = ByteArray(ZIP_MAGIC.size)
            val read = read(header)
            reset()
            return read == ZIP_MAGIC.size && header.contentEquals(ZIP_MAGIC)
        }
    }
}
