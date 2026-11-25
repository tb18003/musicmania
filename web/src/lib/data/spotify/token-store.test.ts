/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { testDatabase } from '../testing'
import { TokenStore } from './token-store'

describe('TokenStore', () => {
  it('round-trips the refresh token, encrypted at rest', async () => {
    const db = testDatabase()
    const store = new TokenStore(db)
    await store.saveRefreshToken('refresh-secret-123')

    expect(await store.readRefreshToken()).toBe('refresh-secret-123')
    const raw = JSON.stringify(await db.secrets.toArray(), (_, value) =>
      value instanceof ArrayBuffer ? new TextDecoder().decode(value) : value,
    )
    expect(raw).not.toContain('refresh-secret-123')
  })

  it('uses a non-extractable AES-GCM key: the raw key can never be exported', async () => {
    const db = testDatabase()
    await new TokenStore(db).saveRefreshToken('x')
    const key = (await db.secrets.get('auth.key'))?.value as CryptoKey
    expect(key.extractable).toBe(false)
    expect(key.algorithm).toMatchObject({ name: 'AES-GCM', length: 256 })
    await expect(crypto.subtle.exportKey('raw', key)).rejects.toThrow()
  })

  it('a fresh IV per write: the same token never encrypts the same way twice', async () => {
    const db = testDatabase()
    const store = new TokenStore(db)
    await store.saveRefreshToken('same')
    const first = (await db.secrets.get('auth.refreshToken'))?.value as { iv: Uint8Array }
    await store.saveRefreshToken('same')
    const second = (await db.secrets.get('auth.refreshToken'))?.value as { iv: Uint8Array }
    expect(Array.from(second.iv)).not.toEqual(Array.from(first.iv))
  })

  it('an undecryptable token (lost or wrong key) is wiped and reads as signed out', async () => {
    const db = testDatabase()
    const store = new TokenStore(db)
    await store.saveRefreshToken('x')
    await db.secrets.delete('auth.key')

    expect(await store.readRefreshToken()).toBeNull()
    expect(await db.secrets.count()).toBe(0)
  })
})
