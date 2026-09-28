package com.privategallery.app.domain.model

enum class MediaSyncStatus { PENDING, UPLOADING, DOWNLOADING, SYNCED, FAILED, DELETED }
enum class MediaType { IMAGE, VIDEO }

data class MediaItem(
    val id: String,
    val folderId: String,
    val ownerUserId: String,
    val type: MediaType,
    val thumbnailPath: String?, // decrypted, cached
    val fullResPath: String?, // decrypted on-demand in the viewer, null until requested
    val createdAt: Long,
    val syncStatus: MediaSyncStatus,
    val transferProgress: Int,
    val sizeBytes: Long,
    val isMine: Boolean
)
