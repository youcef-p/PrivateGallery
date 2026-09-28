package com.privategallery.app.data.repository

import com.privategallery.app.domain.model.Folder
import kotlinx.coroutines.flow.Flow

sealed class FolderAccessError : Exception() {
    data object NotAuthorized : FolderAccessError()
    data object NotFound : FolderAccessError()
}

interface FolderRepository {
    /** Only ever returns folders where the caller is owner or participant — see impl. */
    fun observeMyFolders(): Flow<List<Folder>>

    /** Throws FolderAccessError.NotAuthorized if the current user isn't owner/participant. */
    suspend fun getAuthorizedFolder(folderId: String): Folder

    suspend fun createFolder(plainName: String): Folder
    suspend fun renameFolder(folderId: String, newPlainName: String)
    suspend fun deleteFolder(folderId: String)
    suspend fun removeParticipant(folderId: String)
    suspend fun refreshFromServer()
}
