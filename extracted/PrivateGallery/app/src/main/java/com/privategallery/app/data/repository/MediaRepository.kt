package com.privategallery.app.data.repository

import com.privategallery.app.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import java.io.File

interface MediaRepository {
    /** Access-gated: throws FolderAccessError.NotAuthorized if caller isn't a folder member. */
    fun observePagedMedia(folderId: String, pageSize: Int, offset: Int): Flow<List<MediaItem>>

    suspend fun addMedia(folderId: String, sourceFile: File, mimeType: String): MediaItem
    suspend fun deleteMedia(folderId: String, mediaId: String)
    suspend fun decryptForViewing(folderId: String, mediaId: String): File
    suspend fun countUnsynced(folderId: String): Int
}
