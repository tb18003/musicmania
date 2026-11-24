/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.db

import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDate

/** Binds [values] to the `?` placeholders in order. `LocalDate` becomes a SQL date. */
fun PreparedStatement.bindAll(vararg values: Any?): PreparedStatement = apply {
    values.forEachIndexed { index, value ->
        setObject(index + 1, if (value is LocalDate) Date.valueOf(value) else value)
    }
}

/** Runs a single-row query and maps it, or returns null when there is no row. */
fun <T> Connection.queryOne(sql: String, vararg params: Any?, map: (ResultSet) -> T): T? =
    prepareStatement(sql).use { st -> st.bindAll(*params).executeQuery().use { rs -> if (rs.next()) map(rs) else null } }

/** Runs a query and maps every row. */
fun <T> Connection.queryList(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> = prepareStatement(sql).use { st ->
    st.bindAll(*params).executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
}

/** Runs an insert/update/delete and returns the affected row count. */
fun Connection.update(sql: String, vararg params: Any?): Int = prepareStatement(sql).use { st -> st.bindAll(*params).executeUpdate() }
