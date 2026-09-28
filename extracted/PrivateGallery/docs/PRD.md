# Private Gallery — Product Requirements Document

## 1. Purpose
A private, end-to-end encrypted photo/video sharing app for exactly **two trusted users per folder**
(e.g. a couple, or two close friends). No public feed, no discovery, no groups. Every folder is a
one-to-one private space; the server/relay never has access to plaintext media, folder names, or
thumbnails.

## 2. Target Users
Two people who want to share an ongoing, private stream of photos/videos with each other, with the
guarantee that a compromised or subpoenaed server cannot expose their content.

## 3. Non-Goals
- Not a social network. No public profiles, likes, comments, discovery feed.
- No group folders (a folder is strictly 1 owner + 1 invited participant).
- No ads, no third-party analytics or trackers that touch content.
- No custom/home-grown cryptographic primitives — only vetted primitives (Tink, backed by
  AES-256-GCM, X25519, Ed25519), used correctly.

## 4. Core User Journeys
1. **Onboarding**: Register → device keypair generated in Android Keystore → login → optional
   biometric app lock enabled.
2. **Create a private space**: Create folder → invite partner by username/email/invite code/QR →
   partner accepts → key exchange completes → folder is "live."
3. **Share media**: Add photo/video → encrypted locally → queued → transferred P2P (WebRTC) or via
   encrypted relay fallback → appears in partner's grid, marked synced.
4. **Manual refresh**: Pull-to-refresh on the folder screen → checks inbound/outbound queues,
   deletions, membership changes, connectivity.
5. **Revoke**: Remove partner from folder → local access revoked immediately, folder rekeyed for
   any future media, partner's capability invalidated server-side.

## 5. Acceptance Criteria
See the acceptance criteria list in the original request — all are addressed functionally in this
scaffold; items that depend on external infrastructure (a deployed TURN server, push notification
credentials, app store listing) are called out explicitly in `SETUP.md` rather than silently
assumed.

## 6. Explicit Limitation Called Out Up Front
Pure P2P (WebRTC data channels) cannot guarantee delivery when **both peers are offline
simultaneously** — there is no third device to hold the encrypted blob. The requirements permit an
encrypted-relay fallback for exactly this reason; this scaffold implements that hybrid model:
WebRTC first, encrypted-blob relay through the signaling server second, both encrypted client-side
before they ever leave the device. This is the same tradeoff every real-world "private sync" tool
(including Signal's own media handling) makes — there is no way around it without keeping media on
a third always-on device the user does not control, which would reintroduce exactly the trust
problem this app is designed to avoid.
