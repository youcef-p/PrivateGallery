package com.privategallery.signaling.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Every "encrypted_*" / "wrapped_*" column below stores opaque ciphertext this server never
 * decrypts — see docs/DATA_MODEL.md and docs/SECURITY.md. Nothing here stores a private key or a
 * folder key in usable form.
 */
object Users : Table("users") {
    val id = varchar("id", 36)
    val username = varchar("username", 64).uniqueIndex()
    val emailHash = varchar("email_hash", 128).uniqueIndex() // HMAC(email) — avoids storing plaintext email as a lookup key
    val passwordArgon2id = varchar("password_argon2id", 256)
    val pubkeyX25519 = varchar("pubkey_x25519", 64)
    val pubkeyEd25519 = varchar("pubkey_ed25519", 64)
    val deviceId = varchar("device_id", 64)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Folders : Table("folders") {
    val id = varchar("id", 36)
    val ownerId = varchar("owner_id", 36).references(Users.id)
    val participantId = varchar("participant_id", 36).references(Users.id).nullable()
    val encryptedName = binary("encrypted_name")
    val encryptedThumbnailRef = varchar("encrypted_thumbnail_ref", 256).nullable()
    val status = varchar("status", 32)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/** The actual access-control table — checked on every folder/media request regardless of JWT validity. */
object FolderMembers : Table("folder_members") {
    val folderId = varchar("folder_id", 36).references(Folders.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val status = varchar("status", 32) // ACTIVE / REMOVED
    val joinedAt = timestamp("joined_at")
    override val primaryKey = PrimaryKey(folderId, userId)
}

object Invites : Table("invites") {
    val id = varchar("id", 36)
    val folderId = varchar("folder_id", 36).references(Folders.id)
    val inviterId = varchar("inviter_id", 36).references(Users.id)
    val inviteeIdentifier = varchar("invitee_identifier", 256)
    val inviteMethod = varchar("invite_method", 16)
    val wrappedFolderKey = binary("wrapped_folder_key").nullable()
    val inviterEphemeralPubKey = varchar("inviter_ephemeral_pubkey", 64)
    val status = varchar("status", 32) // PENDING / ACCEPTED / REJECTED / EXPIRED
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(id)
}

object Blobs : Table("blobs") {
    val id = varchar("id", 36)
    val folderId = varchar("folder_id", 36).references(Folders.id)
    val mediaId = varchar("media_id", 36)
    val senderId = varchar("sender_id", 36).references(Users.id)
    val sizeBytes = long("size_bytes")
    val chunkCount = integer("chunk_count")
    val mimeType = varchar("mime_type", 64)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(id)
}

object SyncEvents : Table("sync_events") {
    val id = varchar("id", 36)
    val folderId = varchar("folder_id", 36).references(Folders.id)
    val mediaId = varchar("media_id", 36).nullable()
    val eventType = varchar("event_type", 32)
    val actorId = varchar("actor_id", 36).references(Users.id)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
