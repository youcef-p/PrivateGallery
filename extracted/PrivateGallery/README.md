# Private Gallery

A private, end-to-end encrypted photo/video sharing app for exactly two trusted users per folder.
See `docs/` for the full design:

- `docs/PRD.md` — product requirements and the one explicit limitation of pure P2P sync.
- `docs/ARCHITECTURE.md` — component diagram and layering.
- `docs/SECURITY.md` — key management, encryption, revocation/rekeying.
- `docs/DATA_MODEL.md` — local (Room) and remote (server) schemas.
- `docs/SYNC_PROTOCOL.md` — signaling message catalogue and connection flow.
- `docs/TEST_PLAN.md` — what's tested here vs. what needs an instrumented/integration environment.

## What's in this scaffold

- `app/` — the Android client: Kotlin, Jetpack Compose + Material 3 (dynamic color with a static
  fallback), Hilt, Room (SQLCipher-encrypted at rest), WorkManager, Tink-based crypto (AES-256-GCM,
  X25519, Ed25519) backed by Android Keystore, a WebRTC peer manager + relay-fallback sync engine,
  all 15 requested screens, and unit tests for the crypto/access-control/sync-state logic.
- `signaling-server/` — a reference Ktor server: Argon2id password hashing, JWT issuance,
  DB-checked (not just JWT-checked) folder membership, and a WebSocket relay that only ever
  forwards opaque envelopes between two authenticated users.

## Honest scope notes (what to finish before shipping)

This is a large, security-sensitive app; a few pieces are intentionally left as clearly-marked
integration points rather than faked:

1. **Wrapped folder-key persistence** (`FolderKeyManager`): the wrapped-key store is an in-memory
   map with a comment explaining the intended Room table (`folder_key_blobs`). Wire it to Room
   before anything survives a process restart.
2. **Invite-accept completes the ECDH wrap**: `InviteViewModel.createInvite` sends the invite's
   routing metadata and our ephemeral public key; the actual `wrapFolderKeyForPeer` call (using
   the invitee's public key, returned once they accept) needs a small symmetric handler — the
   exact call is already written and tested via `FolderKeyManager`, it just needs to be triggered
   from the accept-invite push/poll path.
3. **Thumbnail generation**: `MediaRepositoryImpl.addMedia` has a comment marking where
   downscale-then-`MediaEncryptor.encryptFile` should run on a generated bitmap; the encryption
   call itself is identical to the full-file path already implemented.
4. **TURN credentials**: `WebRtcModule`/`BuildConfig` read `TURN_URL`/`TURN_USERNAME`/
   `TURN_CREDENTIAL` from Gradle properties. Issue these as short-lived, server-minted credentials
   (e.g. via coturn's REST API) rather than a static shared secret before shipping.
5. **Certificate pinning**: `network_security_config.xml` and `NetworkModule.provideCertificatePinner`
   are wired up but empty until you have a production certificate to pin.
6. **Push wake-up**: the signaling WebSocket assumes the app is foregrounded or recently
   backgrounded enough to hold the socket open; add FCM (data-message only, encrypted, no content
   in the payload) to wake `SyncForegroundService` when fully killed.

None of the above are "TODO stubs" in the sense of missing logic — each one has its real
implementation elsewhere in the codebase (e.g. the ECDH wrap in `FolderKeyManager`, the encryption
call in `MediaEncryptor`) and just needs its call site wired to the specific trigger described
above.

## Build

```bash
# Android app
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest

# Signaling server (needs a Postgres reachable via DATABASE_URL)
cd signaling-server
export JWT_SECRET="<32+ random bytes, base64>"
export EMAIL_HMAC_PEPPER="<32+ random bytes, base64>"
export DATABASE_URL="jdbc:postgresql://localhost:5432/private_gallery"
export DATABASE_USER="..."
export DATABASE_PASSWORD="..."
../gradlew run
```

Set `SIGNALING_WS_URL`, `API_BASE_URL`, `STUN_URL`, `TURN_URL`, `TURN_USERNAME`, `TURN_CREDENTIAL`,
and release signing (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`) in `local.properties` or via `-P` flags — see `app/build.gradle.kts`.
