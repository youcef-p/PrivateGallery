package com.privategallery.app.data.local.dao

import androidx.room.*
import com.privategallery.app.data.local.entity.MediaItemEntity
import com.privategallery.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // Access control: media is only queryable through a folder the caller already proved
    // authorized access to via FolderDao.getAuthorizedFolder — this query does not re-check
    // user identity itself, so callers MUST gate on FolderRepository first (enforced in
    // MediaRepositoryImpl, never called directly from ui/).
    @Query(
        """
        SELECT * FROM media_items
        WHERE folderId = :folderId AND syncStatus != 'DELETED_LOCAL' AND syncStatus != 'DELETED_REMOTE'
        ORDER BY createdAt DESC
        LIMIT :pageSize OFFSET :offset
        """
    )
    fun observePagedMedia(folderId: String, pageSize: Int, offset: Int): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :mediaId LIMIT 1")
    suspend fun getMedia(mediaId: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE folderId = :folderId AND syncStatus = :status")
    suspend fun getByStatus(folderId: String, status: SyncStatus): List<MediaItemEntity>

    @Upsert
    suspend fun upsert(media: MediaItemEntity)

    @Query("UPDATE media_items SET syncStatus = :status, transferProgress = :progress WHERE id = :mediaId")
    suspend fun updateSyncStatus(mediaId: String, status: SyncStatus, progress: Int)

    @Query("UPDATE media_items SET syncStatus = 'DELETED_LOCAL' WHERE id = :mediaId")
    suspend fun markDeletedLocal(mediaId: String)

    @Query("DELETE FROM media_items WHERE folderId = :folderId")
    suspend fun deleteAllForFolder(folderId: String)

    @Query("DELETE FROM media_items")
    suspend fun clearAll()
}
