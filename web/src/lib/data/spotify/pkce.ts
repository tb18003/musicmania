/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/** RFC 7636 "unreserved" characters, allowed in a code verifier. */
const UNRESERVED = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~'

/** Cryptographically random string over the unreserved set (rejection sampling: no modulo bias). */
export function randomString(length: number, crypto: Crypto = globalThis.crypto): string {
  const limit = 256 - (256 % UNRESERVED.length)
  let out = ''
  while (out.length < length) {
    for (const byte of crypto.getRandomValues(new Uint8Array(length * 2))) {
      if (byte < limit && out.length < length) out += UNRESERVED[byte % UNRESERVED.length]
    }
  }
  return out
}

export function base64Url(bytes: ArrayBuffer): string {
  let binary = ''
  for (const byte of new Uint8Array(bytes)) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** S256 code challenge: BASE64URL(SHA-256(verifier)). */
export async function codeChallenge(verifier: string, crypto: Crypto = globalThis.crypto): Promise<string> {
  return base64Url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)))
}

/** 64 characters: well inside RFC 7636's 43–128 range. */
export const VERIFIER_LENGTH = 64
export const STATE_LENGTH = 32
