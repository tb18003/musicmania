/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.api.db

import io.github.tb18003.musicmania.api.model.LeaderboardEntry
import io.github.tb18003.musicmania.api.model.Metric
import io.github.tb18003.musicmania.api.model.PeriodKind
import io.github.tb18003.musicmania.api.model.PeriodStats
import io.github.tb18003.musicmania.api.model.Role
import io.github.tb18003.musicmania.api.model.TopItem
import io.github.tb18003.musicmania.api.model.UserStatsResponse
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate

data class AllowlistEntry(
    val spotifyUserId: String,
    val displayName: String?,
    val role: Role,
    val enabled: Boolean,
)

/** Who is writing, as Spotify reported it when the token was verified. */
data class Identity(
    val spotifyUserId: String,
    val displayName: String?,
    val avatarUrl: String?,
)

data class Profile(
    val spotifyUserId: String,
    val displayName: String?,
    val sharingEnabled: Boolean,
)

/** Read-only: the allowlist is maintained by hand in the database. */
class AllowlistRepository(private val db: Database) {
    suspend fun find(spotifyUserId: String): AllowlistEntry? = db.transaction { c ->
        c.queryOne(
            "select spotify_user_id, display_name, role, enabled from musicmania.allowlist where spotify_user_id = ?",
            spotifyUserId,
        ) { rs ->
            AllowlistEntry(
                spotifyUserId = rs.getString("spotify_user_id"),
                displayName = rs.getString("display_name"),
                role = Role.valueOf(rs.getString("role").uppercase()),
                enabled = rs.getBoolean("enabled"),
            )
        }
    }
}

class ProfileRepository(private val db: Database) {
    /** Creates the profile on first use and refreshes the name, picture and last-seen time afterwards. */
    suspend fun upsert(identity: Identity): Profile = db.transaction { c -> upsert(c, identity) }

    suspend fun setSharing(identity: Identity, enabled: Boolean): Profile = db.transaction { c ->
        upsert(c, identity)
        requireNotNull(
            c.queryOne(
                "update musicmania.profiles set sharing_enabled = ? where spotify_user_id = ? $RETURNING_PROFILE",
                enabled,
                identity.spotifyUserId,
                map = ::toProfile,
            ),
        )
    }

    /** Deletes the profile; stats and top items cascade. The allowlist entry is left untouched. */
    suspend fun delete(spotifyUserId: String): Boolean = db.transaction { c ->
        c.update("delete from musicmania.profiles where spotify_user_id = ?", spotifyUserId) > 0
    }

    companion object {
        private const val MAX_NAME_LENGTH = 100
        private const val RETURNING_PROFILE = "returning spotify_user_id, display_name, sharing_enabled"

        internal fun upsert(c: Connection, identity: Identity): Profile = requireNotNull(
            c.queryOne(
                // The picture always mirrors Spotify: removing it there removes it here.
                """
                insert into musicmania.profiles (spotify_user_id, display_name, avatar_url) values (?, ?, ?)
                on conflict (spotify_user_id) do update
                    set display_name = coalesce(excluded.display_name, musicmania.profiles.display_name),
                        avatar_url = excluded.avatar_url,
                        last_seen_at = now()
                $RETURNING_PROFILE
                """.trimIndent(),
                identity.spotifyUserId,
                identity.displayName?.take(MAX_NAME_LENGTH),
                identity.avatarUrl,
                map = ::toProfile,
            ),
        )

        private fun toProfile(rs: ResultSet) = Profile(
            spotifyUserId = rs.getString("spotify_user_id"),
            displayName = rs.getString("display_name"),
            sharingEnabled = rs.getBoolean("sharing_enabled"),
        )
    }
}

class StatsRepository(private val db: Database) {
    /** Upserts every period and replaces its top items, atomically. Inputs are already validated. */
    suspend fun replace(identity: Identity, periods: List<PeriodStats>) = db.transaction { c ->
        ProfileRepository.upsert(c, identity)
        val spotifyUserId = identity.spotifyUserId
        periods.forEach { period ->
            val start = LocalDate.parse(period.start)
            c.update(
                """
                insert into musicmania.stat_periods (spotify_user_id, period_kind, period_start, minutes, streams,
                    unique_artists, unique_tracks, current_streak, longest_streak)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (spotify_user_id, period_kind, period_start) do update set
                    minutes = excluded.minutes, streams = excluded.streams, unique_artists = excluded.unique_artists,
                    unique_tracks = excluded.unique_tracks, current_streak = excluded.current_streak,
                    longest_streak = excluded.longest_streak
                """.trimIndent(),
                spotifyUserId, period.kind.dbValue, start, period.minutes, period.streams,
                period.uniqueArtists, period.uniqueTracks, period.currentStreak, period.longestStreak,
            )
            c.update(
                "delete from musicmania.top_items where spotify_user_id = ? and period_kind = ? and period_start = ?",
                spotifyUserId,
                period.kind.dbValue,
                start,
            )
            insertTopItems(c, spotifyUserId, period, start)
        }
    }

    private fun insertTopItems(c: Connection, spotifyUserId: String, period: PeriodStats, start: LocalDate) {
        c.prepareStatement(
            "insert into musicmania.top_items " +
                "(spotify_user_id, period_kind, period_start, item_type, position, name, spotify_id) values (?, ?, ?, ?, ?, ?, ?)",
        ).use { st ->
            listOf("artist" to period.topArtists, "track" to period.topTracks).forEach { (type, items) ->
                items.forEachIndexed { index, item ->
                    st.bindAll(spotifyUserId, period.kind.dbValue, start, type, index + 1, item.name, item.spotifyId).addBatch()
                }
            }
            st.executeBatch()
        }
    }
}

class LeaderboardRepository(private val db: Database) {
    suspend fun leaderboard(metric: Metric, kind: PeriodKind, start: LocalDate, me: String): List<LeaderboardEntry> = db.transaction { c ->
        // Column names come from the Metric enum, never from the request.
        c.queryList(
            """
            select spotify_user_id, display_name, avatar_url, ${metric.valueColumn} as value, ${metric.rankColumn} as rank
            from musicmania.leaderboard
            where period_kind = ? and period_start = ?
            order by rank, display_name nulls last, spotify_user_id
            """.trimIndent(),
            kind.dbValue,
            start,
        ) { rs ->
            val id = rs.getString("spotify_user_id")
            LeaderboardEntry(
                rank = rs.getInt("rank"),
                spotifyUserId = id,
                displayName = rs.getString("display_name"),
                avatarUrl = rs.getString("avatar_url"),
                value = rs.getInt("value"),
                isMe = id == me,
            )
        }
    }

    /** A user's stats for one period, only if they share them (or [requester] is that user). */
    suspend fun userStats(target: String, requester: String, kind: PeriodKind, start: LocalDate): UserStatsResponse? = db.transaction { c ->
        val stats = c.queryOne(
            """
            select s.*, p.display_name, p.avatar_url
            from musicmania.stat_periods s
            join musicmania.profiles p using (spotify_user_id)
            join musicmania.allowlist a using (spotify_user_id)
            where s.spotify_user_id = ? and s.period_kind = ? and s.period_start = ?
              and a.enabled and (p.sharing_enabled or s.spotify_user_id = ?)
            """.trimIndent(),
            target,
            kind.dbValue,
            start,
            requester,
        ) { rs -> rs.toStats(kind, start) } ?: return@transaction null
        val items = c.queryList(
            "select item_type, name, spotify_id from musicmania.top_items " +
                "where spotify_user_id = ? and period_kind = ? and period_start = ? order by item_type, position",
            target,
            kind.dbValue,
            start,
        ) { rs -> rs.getString("item_type") to TopItem(rs.getString("name"), rs.getString("spotify_id")) }
        stats.copy(
            topArtists = items.filter { it.first == "artist" }.map { it.second },
            topTracks = items.filter { it.first == "track" }.map { it.second },
        )
    }

    private fun ResultSet.toStats(kind: PeriodKind, start: LocalDate) = UserStatsResponse(
        spotifyUserId = getString("spotify_user_id"),
        displayName = getString("display_name"),
        avatarUrl = getString("avatar_url"),
        period = kind,
        start = start.toString(),
        minutes = getInt("minutes"),
        streams = getInt("streams"),
        uniqueArtists = getInt("unique_artists"),
        uniqueTracks = getInt("unique_tracks"),
        currentStreak = getInt("current_streak"),
        longestStreak = getInt("longest_streak"),
        topArtists = emptyList(),
        topTracks = emptyList(),
    )
}
