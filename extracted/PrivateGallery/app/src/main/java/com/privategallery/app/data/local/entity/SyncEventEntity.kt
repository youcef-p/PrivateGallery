package com.privategallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncEventType {
    MEDIA_ADDED, MEDIA_DELETED, FOLDER_UPDATED, FOLDER_DELETED, MEMBER_REMOVED,
    SYNC_COMPLETED, SYNC_FAILED
}
enum class SyncEventStatus { PENDING, APPLIED, FAILED }

@Entity(tableName = "sync_events")
data class SyncEventEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val mediaId: String?,
    val eventType: SyncEventType,
    val timestamp: Long,
    val status: SyncEventStatus
)
