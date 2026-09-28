package com.privategallery.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class InitiateBlobUploadRequest(
    val folderId: String,
    val mediaId: String,
    val sizeBytes: Long,
    val chunkCount: Int,
    val mimeType: String
)

@Serializable
data class InitiateBlobUploadResponse(val blobId: String, val uploadUrl: String)

@Serializable
data class BlobReadyNotification(val blobId: String, val folderId: String, val mediaId: String, val sizeBytes: Long)

@Serializable
data class SyncEventDto(
    val id: String,
    val folderId: String,
    val mediaId: String?,
    val eventType: String,
    val actorId: String,
    val createdAt: Long
)
