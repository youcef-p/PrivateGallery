package com.privategallery.app.domain.model

enum class FolderStatus { PENDING_INVITE, ACTIVE, REVOKED, DELETED }

data class Folder(
    val id: String,
    val ownerUserId: String,
    val participantUserId: String?,
    val participantDisplayName: String?,
    val name: String, // already decrypted for display
    val thumbnailPath: String?, // decrypted cache path, nullable
    val status: FolderStatus,
    val lastSyncedAt: Long?,
    val unsyncedCount: Int,
    val isOwner: Boolean
)
