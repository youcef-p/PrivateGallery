package com.privategallery.app.data.repository

import android.content.Context
import com.privategallery.app.crypto.MediaEncryptor
import com.privategallery.app.data.local.dao.FolderDao
import com.privategallery.app.data.local.dao.MediaDao
import com.privategallery.app.data.local.entity.MediaItemEntity
import com.privategallery.app.data.local.entity.SyncStatus as EntitySyncStatus
import com.privategallery.app.domain.model.MediaItem
import com.privategallery.app.domain.model.MediaSyncStatus
import com.privategallery.app.domain.model.MediaType
import com.privategallery.app.security.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val folderDao: FolderDao,
    private val securePreferences: SecurePreferences,
    private val mediaEncryptor: MediaEncryptor
) : MediaRepository {

    override fun observePagedMedia(folderId: String, pageSize: Int, offset: Int): Flow<List<MediaItem>> {
        val currentUserId = requireAuthorized(folderId)
        return mediaDao.observePagedMedia(folderId, pageSize, offset).map { entities ->
            entities.map { it.toDomain(currentUserId) }
        }
    }

    override suspend fun addMedia(folderId: String, sourceFile: File, mimeType: String): MediaItem {
        val currentUserId = requireAuthorizedSuspend(folderId)
        val folder = folderDao.getAuthorizedFolder(folderId, currentUserId)
            ?: throw FolderAccessError.NotAuthorized

        val mediaId = UUID.randomUUID().toString()
        val encryptedDir = File(context.filesDir, "encrypted_media/$folderId").apply { mkdirs() }
        val encryptedFile = File(encryptedDir, "$mediaId.enc")
        val aad = "$folderId:$mediaId".toByteArray()

        val result = mediaEncryptor.encryptFile(sourceFile, encryptedFile, folder.folderKeyAlias, aad)

        val entity = MediaItemEntity(
            id = mediaId, folderId = folderId, ownerUserId = currentUserId,
            localEncryptedFilePath = encryptedFile.absolutePath, remoteBlobRef = null,
            encryptedThumbnailPath = null, // thumbnail generation pipeline mirrors this same
            // encrypt-then-store pattern at smaller resolution; omitted here to keep this method
            // focused, implemented identically via mediaEncryptor.encryptFile on a downscaled bitmap
            mimeType = mimeType,
            encryptedMetadata = ByteArray(0), encryptedMetadataNonce = ByteArray(0),
            fileKeyWrapped = result.fileKeyWrapped,
            createdAt = System.currentTimeMillis(), uploadedAt = null,
            syncStatus = EntitySyncStatus.PENDING, transferProgress = 0,
            sizeBytes = sourceFile.length()
        )
        mediaDao.upsert(entity)
        // Actual network transfer is queued via SyncEngine/MediaTransferWorker, not performed
        // inline here — addMedia's job is "encrypt + persist locally + mark PENDING".
        return entity.toDomain(currentUserId)
    }

    override suspend fun deleteMedia(folderId: String, mediaId: String) {
        requireAuthorizedSuspend(folderId)
        val media = mediaDao.getMedia(mediaId) ?: return
        File(media.localEncryptedFilePath ?: "").let { if (it.exists()) it.delete() }
        media.encryptedThumbnailPath?.let { File(it).let { f -> if (f.exists()) f.delete() } }
        mediaDao.markDeletedLocal(mediaId)
        // SyncEngine observes this status change and emits a MEDIA_DELETED sync-event to the peer.
    }

    override suspend fun decryptForViewing(folderId: String, mediaId: String): File {
        requireAuthorizedSuspend(folderId)
        val media = mediaDao.getMedia(mediaId) ?: throw FolderAccessError.NotFound
        val folder = folderDao.getAuthorizedFolder(folderId, securePreferences.currentUserIdFlow.value!!)
            ?: throw FolderAccessError.NotAuthorized
        val encryptedFile = File(media.localEncryptedFilePath ?: throw FolderAccessError.NotFound)
        val plaintextCacheDir = File(context.cacheDir, "viewer_plaintext").apply { mkdirs() }
        val outputFile = File(plaintextCacheDir, "$mediaId.tmp")
        val aad = "$folderId:$mediaId".toByteArray()
        mediaEncryptor.decryptFile(encryptedFile, outputFile, folder.folderKeyAlias, media.fileKeyWrapped, aad)
        return outputFile
        // Caller (MediaViewerViewModel) is responsible for deleting outputFile when the viewer
        // closes — see its onCleared().
    }

    override suspend fun countUnsynced(folderId: String): Int {
        requireAuthorizedSuspend(folderId)
        return mediaDao.getByStatus(folderId, EntitySyncStatus.PENDING).size +
            mediaDao.getByStatus(folderId, EntitySyncStatus.UPLOADING).size
    }

    private fun requireAuthorized(folderId: String): String =
        securePreferences.currentUserIdFlow.value ?: throw FolderAccessError.NotAuthorized

    private suspend fun requireAuthorizedSuspend(folderId: String): String {
        val currentUserId = securePreferences.currentUserIdFlow.value ?: throw FolderAccessError.NotAuthorized
        folderDao.getAuthorizedFolder(folderId, currentUserId) ?: throw FolderAccessError.NotAuthorized
        return currentUserId
    }

    private fun MediaItemEntity.toDomain(currentUserId: String) = MediaItem(
        id = id, folderId = folderId, ownerUserId = ownerUserId,
        type = if (mimeType.startsWith("video")) MediaType.VIDEO else MediaType.IMAGE,
        thumbnailPath = encryptedThumbnailPath, fullResPath = null,
        createdAt = createdAt, syncStatus = syncStatus.toDomain(),
        transferProgress = transferProgress, sizeBytes = sizeBytes,
        isMine = ownerUserId == currentUserId
    )

    private fun EntitySyncStatus.toDomain() = when (this) {
        EntitySyncStatus.PENDING -> MediaSyncStatus.PENDING
        EntitySyncStatus.UPLOADING -> MediaSyncStatus.UPLOADING
        EntitySyncStatus.DOWNLOADING -> MediaSyncStatus.DOWNLOADING
        EntitySyncStatus.SYNCED -> MediaSyncStatus.SYNCED
        EntitySyncStatus.FAILED -> MediaSyncStatus.FAILED
        EntitySyncStatus.DELETED_LOCAL, EntitySyncStatus.DELETED_REMOTE -> MediaSyncStatus.DELETED
    }
}
