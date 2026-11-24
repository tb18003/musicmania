/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.tb18003.musicmania.api.config.DatabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource

/** Connection pool plus a small transaction helper. Every query runs on the IO dispatcher. */
class Database(private val dataSource: DataSource) : AutoCloseable {
    constructor(config: DatabaseConfig) : this(
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                // The Supabase free tier allows few connections; a handful of users needs even fewer.
                maximumPoolSize = MAX_POOL_SIZE
                connectionTimeout = CONNECTION_TIMEOUT_MS
                isAutoCommit = false
                poolName = "musicmania"
            },
        ),
    )

    suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            var committed = false
            try {
                block(connection).also {
                    connection.commit()
                    committed = true
                }
            } finally {
                if (!committed) connection.rollback()
            }
        }
    }

    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    private companion object {
        const val MAX_POOL_SIZE = 4
        const val CONNECTION_TIMEOUT_MS = 5_000L
    }
}
