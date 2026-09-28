package com.privategallery.app.data.local.dao

import androidx.room.*
import com.privategallery.app.data.local.entity.FolderEntity
import com.privategallery.app.data.local.entity.FolderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    // CRITICAL access-control invariant: a folder is only ever visible locally if the current
    // user is the owner or the participant. This mirrors the server-side check but is enforced
    // here too, defense-in-depth, since this query backs every UI list.
    @Query(
        """
        SELECT * FROM folders
        WHERE status != 'DELETED'
        AND (ownerUserId = :currentUserId OR participantUserId = :currentUserId)
        ORDER BY updatedAt DESC
        """
    )
    fun observeFoldersForUser(currentUserId: String): Flow<List<FolderEntity>>

    @Query(
        """
        SELECT * FROM folders WHERE id = :folderId
        AND (ownerUserId = :currentUserId OR participantUserId = :currentUserId)
        LIMIT 1
        """
    )
    suspend fun getAuthorizedFolder(folderId: String, currentUserId: String): FolderEntity?

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("UPDATE folders SET status = :status, updatedAt = :updatedAt WHERE id = :folderId")
    suspend fun updateStatus(folderId: String, status: FolderStatus, updatedAt: Long)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun delete(folderId: String)

    @Query("DELETE FROM folders")
    suspend fun clearAll()
}
