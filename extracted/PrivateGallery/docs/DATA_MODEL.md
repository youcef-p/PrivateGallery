# Data Model

## Local (Room, SQLCipher-encrypted file at rest) — see entity classes for exact Kotlin

- **UserEntity**: id, username, email, publicKeyX25519, publicKeyEd25519, deviceId, createdAt
- **FolderEntity**: id, ownerUserId, participantUserId (nullable until invite accepted),
  encryptedName (bytes), encryptedNameNonce, encryptedThumbnail (path to encrypted file, nullable),
  folderKeyAlias (Keystore alias reference — the wrapped key itself lives in Keystore, not in the
  DB row), createdAt, updatedAt, status (PENDING_INVITE / ACTIVE / REVOKED / DELETED)
- **MediaItemEntity**: id, folderId, ownerUserId, localEncryptedFilePath, remoteBlobRef (nullable),
  encryptedThumbnailPath, mimeType, encryptedMetadata (bytes: original filename, capture time, geo
  if present — user-controlled), fileKeyWrapped (bytes, wrapped with folder key), createdAt,
  uploadedAt (nullable), syncStatus (PENDING / UPLOADING / SYNCED / FAILED / DELETED_LOCAL /
  DELETED_REMOTE), transferProgress (0–100), sizeBytes
- **SyncEventEntity**: id, folderId, mediaId (nullable — null for folder-level events), eventType
  (MEDIA_ADDED / MEDIA_DELETED / FOLDER_UPDATED / FOLDER_DELETED / MEMBER_REMOVED /
  SYNC_COMPLETED / SYNC_FAILED), timestamp, status (PENDING / APPLIED / FAILED)

## Remote (server — stores ciphertext & routing metadata only)

- `users(id, email_hash, username, password_argon2id, pubkey_x25519, pubkey_ed25519, created_at)`
- `folders(id, owner_id, participant_id NULL, encrypted_name, encrypted_thumbnail_ref, status,
  created_at, updated_at)`
- `folder_members(folder_id, user_id, status, joined_at)` — the actual access-control table;
  checked on every request regardless of JWT validity.
- `blobs(id, folder_id, sender_id, ciphertext_ref, size_bytes, chunk_count, created_at, expires_at)`
  — TTL'd once both sides have acked receipt.
- `sync_events(id, folder_id, media_id NULL, event_type, actor_id, created_at)` — an append-only
  log the recipient's device polls / receives over the WebSocket to know what changed without the
  server needing to understand *what* changed semantically.
- `invites(id, folder_id, inviter_id, invitee_identifier, invite_code, wrapped_folder_key,
  status, expires_at)`

None of the `encrypted_*` or `wrapped_*` columns are ever decryptable server-side — the server has
no folder keys, no user private keys, ever.
