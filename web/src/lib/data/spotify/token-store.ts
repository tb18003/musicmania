/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { MusicManiaDatabase } from '../db/database'

const KEY = 'auth.key'
const REFRESH = 'auth.refreshToken'
const IV_BYTES = 12

interface Sealed {
  iv: Uint8Array<ArrayBuffer>
  data: ArrayBuffer
}

/**
 * The web's TokenStore. The refresh token is encrypted with AES-256-GCM under a key that WebCrypto
 * generated as non-extractable: the raw key bytes can never be read, so a copy of the storage is
 * useless elsewhere. Access tokens are never persisted (memory only).
 */
export class TokenStore {
  constructor(
    private readonly db: MusicManiaDatabase,
    private readonly crypto: Crypto = globalThis.crypto,
  ) {}

  async #key(create: boolean): Promise<CryptoKey | null> {
    const stored = (await this.db.secrets.get(KEY))?.value
    // Duck-typed: a CryptoKey read back from IndexedDB may come from another realm.
    if (typeof stored === 'object' && stored !== null && 'algorithm' in stored) return stored as CryptoKey
    if (!create) return null
    const key = await this.crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt'])
    await this.db.secrets.put({ key: KEY, value: key })
    return key
  }

  async saveRefreshToken(token: string): Promise<void> {
    const key = await this.#key(true)
    if (!key) throw new Error('Encryption key unavailable')
    const iv = this.crypto.getRandomValues(new Uint8Array(IV_BYTES))
    const data = await this.crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(token))
    const sealed: Sealed = { iv, data }
    await this.db.secrets.put({ key: REFRESH, value: sealed })
  }

  /** The refresh token, or null if there is none or it cannot be decrypted (then it is wiped). */
  async readRefreshToken(): Promise<string | null> {
    const sealed = (await this.db.secrets.get(REFRESH))?.value as Sealed | undefined
    if (!sealed) return null
    const key = await this.#key(false)
    try {
      if (!key) throw new Error('missing key')
      const plain = await this.crypto.subtle.decrypt({ name: 'AES-GCM', iv: sealed.iv }, key, sealed.data)
      return new TextDecoder().decode(plain)
    } catch {
      await this.clear()
      return null
    }
  }

  async clear(): Promise<void> {
    await this.db.secrets.bulkDelete([REFRESH, KEY])
  }
}
