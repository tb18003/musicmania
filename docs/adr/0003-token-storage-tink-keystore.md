# ADR 0003 — Token storage with Tink and the Android Keystore

- Status: Accepted · Date: 2026-11-19

## Context
Refresh tokens are long-lived credentials. `androidx.security:security-crypto`
(EncryptedSharedPreferences) is deprecated.

## Decision
- Store tokens as a single blob in a dedicated Preferences DataStore file, encrypted with
  **Tink AEAD AES-256-GCM** (associated data binds the ciphertext to its purpose).
- The Tink keyset is wrapped by a non-exportable **Android Keystore** master key.
- Both files are excluded from backup/device transfer. Undecryptable data (e.g. after a
  restore) is treated as "signed out".
- An in-memory cache serves OkHttp threads; disk is read once per process.

## Consequences
+ Strong at-rest protection with a maintained library; testable through an `Encryptor` seam.
− Keystore operations are slow on some devices (done lazily, off the main thread).
