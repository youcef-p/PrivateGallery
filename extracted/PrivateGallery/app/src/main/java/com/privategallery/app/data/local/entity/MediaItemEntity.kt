package com.privategallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus { PENDING, UPLOADING, DOWNLOADING, SYNCED, FAILED, DELETED_LOCAL, DELETED_REMOTE }

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val ownerUserId: String,
    val localEncryptedFilePath: String?,
    val remoteBlobRef: String?,
    val encryptedThumbnailPath: String?,
    val mimeType: String,
    val encryptedMetadata: ByteArray,
    val encryptedMetadataNonce: ByteArray,
    // The per-file content key, wrapped (AES-GCM) with the folder key — safe to store at rest.
    val fileKeyWrapped: ByteArray,
    val createdAt: Long,
    val uploadedAt: Long?,
    val syncStatus: SyncStatus,
    val transferProgress: Int,
    val sizeBytes: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaItemEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}
