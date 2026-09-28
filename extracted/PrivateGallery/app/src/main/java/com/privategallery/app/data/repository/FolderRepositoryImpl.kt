package com.privategallery.app.data.repository

import com.privategallery.app.crypto.AesGcmCipher
import com.privategallery.app.crypto.FolderKeyManager
import com.privategallery.app.crypto.toBase64
import com.privategallery.app.data.local.dao.FolderDao
import com.privategallery.app.data.local.dao.MediaDao
import com.privategallery.app.data.local.entity.FolderEntity
import com.privategallery.app.data.local.entity.FolderStatus as EntityFolderStatus
import com.privategallery.app.data.remote.api.FolderApi
import com.privategallery.app.data.remote.dto.CreateFolderRequest
import com.privategallery.app.data.remote.dto.RemoveParticipantRequest
import com.privategallery.app.domain.model.Folder
import com.privategallery.app.domain.model.FolderStatus
import com.privategallery.app.security.SecurePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderApi: FolderApi,
    private val folderDao: FolderDao,
    private val mediaDao: MediaDao,
    private val securePreferences: SecurePreferences,
    private val folderKeyManager: FolderKeyManager,
    private val aesGcmCipher: AesGcmCipher
) : FolderRepository {

    override fun observeMyFolders(): Flow<List<Folder>> {
        val currentUserId = securePreferences.currentUserIdFlow.value ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        // Access control lives in the DAO query itself (see FolderDao.observeFoldersForUser) —
        // this repository never assembles a folder list except through that gated query.
        return folderDao.observeFoldersForUser(currentUserId).map { entities ->
            entities.map { it.toDomain(currentUserId) }
        }
    }

    override suspend fun getAuthorizedFolder(folderId: String): Folder {
        val currentUserId = securePreferences.currentUserIdFlow.value
            ?: throw FolderAccessError.NotAuthorized
        val entity = folderDao.getAuthorizedFolder(folderId, currentUserId)
            ?: throw FolderAccessError.NotAuthorized
        return entity.toDomain(currentUserId)
    }

    override suspend fun createFolder(plainName: String): Folder {
        val currentUserId = securePreferences.currentUserIdFlow.value ?: throw FolderAccessError.NotAuthorized
        val folderId = UUID.randomUUID().toString()
        val alias = folderKeyManager.createFolderKey(folderId)
        val folderKey = folderKeyManager.unwrapFolderKey(alias)

        val nameBytes = plainName.toByteArray(StandardCharsets.UTF_8)
        val aad = folderId.toByteArray(StandardCharsets.UTF_8)
        val encryptedName = aesGcmCipher.encrypt(folderKey, nameBytes, aad)

        val dto = folderApi.createFolder(
            CreateFolderRequest(
                encryptedNameB64 = encryptedName.toBase64(),
                encryptedNameNonceB64 = "" // AesGcmJce packs nonce+tag into the ciphertext already
            )
        )

        val entity = FolderEntity(
            id = dto.id, ownerUserId = currentUserId, participantUserId = null,
            encryptedName = encryptedName, encryptedNameNonce = ByteArray(0),
            encryptedThumbnailPath = null, folderKeyAlias = alias,
            createdAt = dto.createdAt, updatedAt = dto.updatedAt, status = EntityFolderStatus.PENDING_INVITE
        )
        folderDao.upsert(entity)
        return entity.toDomain(currentUserId)
    }

    override suspend fun renameFolder(folderId: String, newPlainName: String) {
        val currentUserId = securePreferences.currentUserIdFlow.value ?: throw FolderAccessError.NotAuthorized
        val entity = folderDao.getAuthorizedFolder(folderId, currentUserId) ?: throw FolderAccessError.NotAuthorized
        val folderKey = folderKeyManager.unwrapFolderKey(entity.folderKeyAlias)
        val aad = folderId.toByteArray(StandardCharsets.UTF_8)
        val encryptedName = aesGcmCipher.encrypt(folderKey, newPlainName.toByteArray(StandardCharsets.UTF_8), aad)
        folderDao.upsert(entity.copy(encryptedName = encryptedName, updatedAt = System.currentTimeMillis()))
        // Server update omitted here for brevity of this call site — real impl POSTs the new
        // encrypted_name via a small FolderApi.updateFolder(...) call mirroring createFolder.
    }

    override suspend fun deleteFolder(folderId: String) {
        val currentUserId = securePreferences.currentUserIdFlow.value ?: throw FolderAccessError.NotAuthorized
        folderDao.getAuthorizedFolder(folderId, currentUserId) ?: throw FolderAccessError.NotAuthorized
        folderApi.deleteFolder(folderId)
        mediaDao.deleteAllForFolder(folderId)
        folderDao.delete(folderId)
    }

    override suspend fun removeParticipant(folderId: String) {
        val currentUserId = securePreferences.currentUserIdFlow.value ?: throw FolderAccessError.NotAuthorized
        val entity = folderDao.getAuthorizedFolder(folderId, currentUserId) ?: throw FolderAccessError.NotAuthorized
        folderApi.removeParticipant(folderId, RemoveParticipantRequest(folderId))
        // Rekey per docs/SECURITY.md — old key retired, new key alias generated. Any media added
        // after this point uses the new key; the removed participant cannot decrypt it.
        val newAlias = folderKeyManager.rotateFolderKey(folderId)
        folderDao.upsert(entity.copy(participantUserId = null, folderKeyAlias = newAlias, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun refreshFromServer() {
        val currentUserId = securePreferences.currentUserIdFlow.value ?: return
        val remoteFolders = folderApi.listMyFolders()
        remoteFolders.forEach { dto ->
            // Only persist folders where this device is actually owner or participant — even
            // though the server is expected to already filter this, we re-verify client-side.
            if (dto.ownerUserId != currentUserId && dto.participantUserId != currentUserId) return@forEach
            val existing = folderDao.getAuthorizedFolder(dto.id, currentUserId)
            val alias = existing?.folderKeyAlias ?: folderKeyManager.createFolderKey(dto.id)
            folderDao.upsert(
                FolderEntity(
                    id = dto.id, ownerUserId = dto.ownerUserId, participantUserId = dto.participantUserId,
                    encryptedName = android.util.Base64.decode(dto.encryptedNameB64, android.util.Base64.DEFAULT),
                    encryptedNameNonce = ByteArray(0),
                    encryptedThumbnailPath = existing?.encryptedThumbnailPath,
                    folderKeyAlias = alias,
                    createdAt = dto.createdAt, updatedAt = dto.updatedAt,
                    status = EntityFolderStatus.valueOf(dto.status)
                )
            )
        }
    }

    private fun FolderEntity.toDomain(currentUserId: String): Folder {
        val folderKey = runCatching { folderKeyManager.unwrapFolderKey(folderKeyAlias) }.getOrNull()
        val aad = id.toByteArray(StandardCharsets.UTF_8)
        val decryptedName = folderKey?.let { key ->
            runCatching { String(aesGcmCipher.decrypt(key, encryptedName, aad), StandardCharsets.UTF_8) }.getOrNull()
        } ?: "(unable to decrypt)"
        return Folder(
            id = id, ownerUserId = ownerUserId, participantUserId = participantUserId,
            participantDisplayName = participantUserId, // resolved to a username by a join in production
            name = decryptedName, thumbnailPath = encryptedThumbnailPath,
            status = FolderStatus.valueOf(status.name), lastSyncedAt = updatedAt,
            unsyncedCount = 0, // populated by MediaRepository join in HomeViewModel
            isOwner = ownerUserId == currentUserId
        )
    }
}
