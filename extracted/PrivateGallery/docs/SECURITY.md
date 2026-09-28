# Security Architecture

## Identity & keys
- On registration, each **device** generates:
  - An **Ed25519** signing keypair (identity/auth signatures) — private key non-exportable, stored
    in Android Keystore (`setIsStrongBoxBacked(true)` where available).
  - An **X25519** keypair (key agreement) — same storage.
- Public keys are uploaded to the server at registration and signed challenge/response is used at
  login (no password sent for the crypto handshake; password is only used for Argon2id-hashed
  account authentication, kept fully separate from the E2E key material).
- Private keys **never** leave the device, are never sent to the server, never logged.

## Folder key establishment
- When user A invites user B: A generates a random 256-bit **folder key** `Kf` on-device.
- A performs X25519 ECDH with B's public key → `Ks` (shared secret) → HKDF → `Kwrap`.
- A encrypts `Kf` with `Kwrap` using AES-256-GCM → sends the wrapped key + invite metadata through
  the signaling channel. The server relays an opaque blob it cannot decrypt.
- B performs the matching ECDH + HKDF to derive `Kwrap`, unwraps `Kf`. Both devices now hold `Kf`
  in Keystore-protected storage (imported as a wrapped key, unwrapped only in memory for use).
- Folder name and thumbnail are encrypted with `Kf` (AES-256-GCM, random 96-bit nonce per field)
  before ever being written to Room or sent to the server.

## Media encryption
- Every photo/video is encrypted client-side with a **per-file** key `Km` (random 256-bit),
  itself wrapped with `Kf` (AES-256-GCM). This gives per-file forward secrecy within a folder:
  compromising one file's key does not expose others.
- Large files are encrypted in 1 MiB chunks using AES-256-GCM with a chunk counter mixed into the
  nonce (`nonce = deviceNonce(64 bits) || counter(32 bits)`), enabling resumable, chunk-verifiable
  transfer (see `MediaTransferWorker`).
- Thumbnails are generated locally, encrypted with the same `Km`, and are the *only* preview data
  that ever reaches the relay/blob store if a P2P channel isn't available yet.

## Revocation & rekeying
- Removing a participant: server immediately invalidates that user's capability token for the
  folder (access-control check below), and the owner's device generates a **new** `Kf'`, re-wraps
  it only for remaining authorized devices (i.e., none, since it's 1:1 — so the folder simply
  becomes owner-only and unrecoverable to the removed user for anything encrypted *after*
  rotation). Content already synced to the removed user's device before revocation is, by the
  nature of E2E encryption, already in their possession — this is disclosed to the user in the UI
  when they remove someone ("they will keep media already received").

## Access control (server-side, defense in depth even though server holds no plaintext)
- Every folder API call requires a signed capability (JWT with folder-scoped claim) validated
  against a `folder_members` row for `(folder_id, user_id)` with `status = ACTIVE`.
- `MEMBER_REMOVED` events cause the server to delete that row; subsequent requests get 403
  regardless of an unexpired JWT (checked against DB, not just JWT signature).

## Local storage
- Room database is opened via SQLCipher (`net.zetetic:android-database-sqlcipher`), keyed by a
  passphrase itself sealed in Android Keystore (AES-GCM key wrapping a random DB passphrase held
  only in `EncryptedSharedPreferences`).
- Decrypted media is only ever written to `context.cacheDir` (app-private, not scoped storage),
  and only for as long as the viewer is open; `MediaViewerViewModel` clears the plaintext cache
  file `onCleared()`. Logout wipes the entire cache dir synchronously.

## Transport
- All REST/WebSocket traffic over TLS 1.3. Certificate pinning via OkHttp `CertificatePinner`
  (pins configured per build flavor — see `NetworkModule.kt`).
- WebRTC data channels use DTLS-SRTP per the WebRTC spec baseline (mandatory, not optional).

## What the server can never see
- Plaintext media, plaintext folder name/thumbnail, folder decryption keys, private keys.
- What it *does* see (necessarily, to route anything): user id pairs, folder id, ciphertext size,
  coarse timestamps, and — only for the two participants of a folder, never broadcast —
  online/offline presence.

## Explicit non-goals of this threat model
- Does not protect against a fully compromised, rooted endpoint device (out of scope for any
  client-side E2E system).
- Does not provide deniability/forward secrecy at the Signal-protocol double-ratchet level for
  every message — folder keys rotate on membership change, not per-message. This is disclosed as a
  design tradeoff appropriate for a media-sharing app, not a messaging app; upgrading to a ratchet
  is a documented future enhancement, not silently claimed here.
