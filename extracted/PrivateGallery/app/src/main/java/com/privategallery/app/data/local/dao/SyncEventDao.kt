package com.privategallery.app.data.local.dao

import androidx.room.*
import com.privategallery.app.data.local.entity.SyncEventEntity
import com.privategallery.app.data.local.entity.SyncEventStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncEventDao {
    @Query("SELECT * FROM sync_events WHERE folderId = :folderId ORDER BY timestamp DESC LIMIT 100")
    fun observeRecent(folderId: String): Flow<List<SyncEventEntity>>

    @Query("SELECT * FROM sync_events WHERE status = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPending(): List<SyncEventEntity>

    @Insert
    suspend fun insert(event: SyncEventEntity)

    @Query("UPDATE sync_events SET status = :status WHERE id = :eventId")
    suspend fun updateStatus(eventId: String, status: SyncEventStatus)

    @Query("DELETE FROM sync_events")
    suspend fun clearAll()
}
