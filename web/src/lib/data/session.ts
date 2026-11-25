/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { SessionRepository } from '$lib/domain/repositories'
import type { MusicManiaDatabase } from './db/database'

/** Sign-out wipes tokens, the key and all local data: the whole database goes (security rule 9). */
export class DefaultSessionRepository implements SessionRepository {
  constructor(
    private readonly db: MusicManiaDatabase,
    private readonly forgetInMemoryToken: () => void,
  ) {}

  async signOut(): Promise<void> {
    this.forgetInMemoryToken()
    await this.db.delete()
  }
}
