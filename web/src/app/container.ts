/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { GetItemDetail } from '$lib/domain/detail'
import { GetInsights } from '$lib/domain/insights'
import { GetLeaderboard, PublishListeningStats } from '$lib/domain/leaderboard'
import type {
  AuthRepository,
  CloudRepository,
  HistoryRepository,
  SessionRepository,
  SpotifyRemote,
  StreamingHistoryImporter,
} from '$lib/domain/repositories'
import { HistorySync } from '$lib/domain/sync'
import { GetTopItems } from '$lib/domain/top'
import { MusicManiaCloudClient } from '$lib/data/cloud/client'
import { spotifyConfigFrom } from '$lib/data/config'
import { MusicManiaDatabase } from '$lib/data/db/database'
import { DexieAppStateRepository, DexieHistoryRepository } from '$lib/data/db/repositories'
import { CachedTopItemsRepository } from '$lib/data/db/top-items'
import { WorkerStreamingHistoryImporter } from '$lib/data/importer/worker-importer'
import { DefaultSessionRepository } from '$lib/data/session'
import { SpotifyAuth } from '$lib/data/spotify/auth'
import { SpotifyApiClient } from '$lib/data/spotify/client'
import { TokenStore } from '$lib/data/spotify/token-store'

/** Everything the screens need, behind domain interfaces (the web's Hilt graph). */
export interface Container {
  auth: AuthRepository
  remote: SpotifyRemote
  history: HistoryRepository
  sync: HistorySync
  top: GetTopItems
  insights: GetInsights
  detail: GetItemDetail
  importer: StreamingHistoryImporter
  cloud: CloudRepository
  leaderboard: GetLeaderboard
  publish: PublishListeningStats
  /** My Spotify user ID (asked once per session). */
  spotifyUserId: () => Promise<string | null>
  session: SessionRepository
  now: () => number
}

export function createAuth(db: MusicManiaDatabase): SpotifyAuth {
  return new SpotifyAuth(spotifyConfigFrom(import.meta.env), {
    db,
    tokens: new TokenStore(db),
    fetch: (...args) => fetch(...args),
    now: () => Date.now(),
  })
}

export function createContainer(): Container {
  const db = new MusicManiaDatabase()
  const auth = createAuth(db)
  const remote = new SpotifyApiClient(auth, (...args) => fetch(...args))
  const history = new DexieHistoryRepository(db)
  const now = () => Date.now()
  const cloud = new MusicManiaCloudClient(import.meta.env.VITE_MUSICMANIA_API_URL || null, auth, (...args) =>
    fetch(...args),
  )
  let myId: Promise<string | null> | null = null
  return {
    auth,
    remote,
    history,
    sync: new HistorySync({ remote, history, appState: new DexieAppStateRepository(db), now }),
    top: new GetTopItems({ top: new CachedTopItemsRepository(db, remote, now), history, now }),
    insights: new GetInsights({ history, now }),
    detail: new GetItemDetail({ history, remote, now }),
    importer: new WorkerStreamingHistoryImporter(),
    cloud,
    leaderboard: new GetLeaderboard({ cloud, now }),
    publish: new PublishListeningStats({ cloud, history, now }),
    spotifyUserId: () => (myId ??= remote.me().then((me) => (me.ok ? me.data.id : null))),
    session: new DefaultSessionRepository(db, () => auth.forget()),
    now,
  }
}
