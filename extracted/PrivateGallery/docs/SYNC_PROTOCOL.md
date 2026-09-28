# Signaling & Sync Protocol

Transport: a single authenticated WebSocket per online device (`wss://.../ws?token=<jwt>`).
All messages are JSON envelopes:

```json
{ "type": "...", "from": "userId", "to": "userId", "folderId": "...", "payload": { ... } }
```

## Message types

| type              | payload                                   | purpose                                      |
|-------------------|--------------------------------------------|-----------------------------------------------|
| `presence`        | { "online": bool }                        | server → the *other* folder participant only  |
| `rtc-offer`       | { "sdp": "..." }                          | opaque SDP, relayed verbatim                  |
| `rtc-answer`      | { "sdp": "..." }                          | opaque SDP, relayed verbatim                  |
| `rtc-ice`         | { "candidate": "..." }                    | opaque ICE candidate, relayed verbatim        |
| `invite-created`  | { "inviteId", "wrappedKey", "code" }      | delivered to invitee if online, else polled   |
| `invite-accepted` | { "inviteId" }                            | notifies inviter, triggers folder ACTIVE      |
| `sync-event`      | { SyncEvent fields }                      | MEDIA_ADDED / MEDIA_DELETED / etc.            |
| `blob-ready`      | { "blobId", "folderId", "sizeBytes" }     | relay fallback: ciphertext ready to fetch     |
| `member-removed`  | { "folderId", "userId" }                  | forces the removed user's client to purge     |

## Connection flow (per folder, per session)

1. Both devices connect their persistent WebSocket at app foreground / periodic WorkManager wake.
2. `SyncEngine` requests peer presence for each ACTIVE folder's counterpart.
3. If counterpart online: initiate WebRTC — createOffer → send `rtc-offer` → receive `rtc-answer`
   → exchange `rtc-ice` → data channel opens → drain the local pending-upload queue directly,
   chunk by chunk, each chunk AES-256-GCM encrypted with the per-file key derived nonce scheme in
   SECURITY.md.
4. If counterpart offline or WebRTC fails to establish within 8s (configurable): encrypt the file,
   upload the ciphertext blob to the relay, emit `sync-event` with `MEDIA_ADDED`; the event log is
   what the recipient's client replays on next connect/pull-to-refresh, fetching `blob-ready`
   references.
5. Deletion: sender writes a local tombstone, emits `sync-event(MEDIA_DELETED)`; recipient deletes
   local ciphertext + thumbnail on receipt, regardless of whether the original transfer used P2P
   or relay.
6. Pull-to-refresh explicitly re-runs steps 2–5 against the server as a synchronous check, so a
   user is never stuck waiting on push/WebSocket delivery alone.
