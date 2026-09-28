# Test Plan

## Unit tests (included in this scaffold, `app/src/test`)
- `crypto/AesGcmCipherTest` — encrypt/decrypt round trip, wrong-key rejection, tamper detection,
  AAD-context mismatch rejection, chunk-reorder rejection (chunk index bound into AAD).
- `crypto/X25519KeyExchangeTest` — both parties derive an identical shared wrapping key from
  their own private + the other's public key; different HKDF context info yields different keys.
- `crypto/Ed25519SignerTest` — valid signature verifies, wrong-key and tampered-data both fail.
- `access/FolderAccessControlTest` — owner/participant authorized, unrelated third user rejected,
  two folders between the same owner and different participants are independent, deleted folder
  is inaccessible even to the former owner, revoked participant (`participantUserId = null`)
  loses access.
- `sync/MediaSyncStateTransitionTest` — encodes and checks the legal sync-status transition graph
  so an invalid jump (e.g. PENDING → SYNCED skipping UPLOADING) cannot silently regress in.
- `sync/OfflineQueueBehaviorTest` — only PENDING/FAILED items are re-queued by a refresh;
  in-flight UPLOADING items are not duplicated.

## Additional tests to add before shipping (not included — require Robolectric/instrumented
  environment or a running signaling-server instance, both out of scope for this scaffold's
  plain-JUnit test task)
- **Login validation**: instrumented test hitting `/auth/login/challenge` + `/auth/login` against
  a test Postgres, asserting wrong password rejected, wrong device signature rejected, expired
  challenge rejected.
- **Encryption/decryption (integration)**: `MediaEncryptorTest` round-tripping a real file through
  `encryptFile`/`decryptFile` with a real Keystore-backed alias (needs Robolectric's Keystore
  shadow or an instrumented device/emulator).
- **Pull-to-refresh behavior**: Compose UI test asserting `PullToRefreshContainer` triggers
  `FolderViewModel.refresh()` exactly once per gesture and shows the offline banner when
  `refreshFromServer()` throws `IOException`.
- **Delete propagation**: two-emulator instrumented test — delete on device A, assert the
  `MEDIA_DELETED` sync-event reaches device B and its local file is removed.
- **WebRTC connection fallback logic**: fake `SignalingWebSocketClient` that never delivers an
  answer, asserting `SyncEngine` falls back to `MediaTransferWorker` after
  `SyncEngine.P2P_TIMEOUT_MS`.
- **Local database integrity**: Room migration test once a second schema version exists (replace
  `fallbackToDestructiveMigration()` in `DatabaseModule` first).
- **Unauthorized access rejection (server)**: Ktor test-host request to `/folders/{id}/...` from a
  JWT belonging to a non-member, asserting 403 regardless of JWT validity.
