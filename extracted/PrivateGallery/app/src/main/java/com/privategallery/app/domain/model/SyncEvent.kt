package com.privategallery.app.domain.model

enum class SyncEventType {
    MEDIA_ADDED, MEDIA_DELETED, FOLDER_UPDATED, FOLDER_DELETED, MEMBER_REMOVED,
    SYNC_COMPLETED, SYNC_FAILED
}

data class SyncEvent(
    val id: String,
    val folderId: String,
    val mediaId: String?,
    val type: SyncEventType,
    val timestamp: Long
)
