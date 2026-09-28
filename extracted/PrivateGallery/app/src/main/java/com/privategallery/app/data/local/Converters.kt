package com.privategallery.app.data.local

import androidx.room.TypeConverter
import com.privategallery.app.data.local.entity.FolderStatus
import com.privategallery.app.data.local.entity.SyncEventStatus
import com.privategallery.app.data.local.entity.SyncEventType
import com.privategallery.app.data.local.entity.SyncStatus

class Converters {
    @TypeConverter fun toFolderStatus(v: String) = FolderStatus.valueOf(v)
    @TypeConverter fun fromFolderStatus(v: FolderStatus) = v.name

    @TypeConverter fun toSyncStatus(v: String) = SyncStatus.valueOf(v)
    @TypeConverter fun fromSyncStatus(v: SyncStatus) = v.name

    @TypeConverter fun toSyncEventType(v: String) = SyncEventType.valueOf(v)
    @TypeConverter fun fromSyncEventType(v: SyncEventType) = v.name

    @TypeConverter fun toSyncEventStatus(v: String) = SyncEventStatus.valueOf(v)
    @TypeConverter fun fromSyncEventStatus(v: SyncEventStatus) = v.name
}
