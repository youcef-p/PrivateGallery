package com.privategallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FolderStatus { PENDING_INVITE, ACTIVE, REVOKED, DELETED }

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val participantUserId: String?,
    val encryptedName: ByteArray,
    val encryptedNameNonce: ByteArray,
    val encryptedThumbnailPath: String?,
    // Reference only — the actual unwrapped folder key material lives in Android Keystore /
    // FolderKeyManager's wrapped-key store, never as a raw key column in this table.
    val folderKeyAlias: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: FolderStatus
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FolderEntity) return false
        return id == other.id && ownerUserId == other.ownerUserId &&
            participantUserId == other.participantUserId &&
            encryptedName.contentEquals(other.encryptedName) &&
            encryptedNameNonce.contentEquals(other.encryptedNameNonce) &&
            encryptedThumbnailPath == other.encryptedThumbnailPath &&
            folderKeyAlias == other.folderKeyAlias && createdAt == other.createdAt &&
            updatedAt == other.updatedAt && status == other.status
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + encryptedName.contentHashCode()
        result = 31 * result + encryptedNameNonce.contentHashCode()
        return result
    }
}
