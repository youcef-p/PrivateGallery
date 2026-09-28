package com.privategallery.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.privategallery.app.data.local.dao.FolderDao
import com.privategallery.app.data.local.dao.MediaDao
import com.privategallery.app.data.local.dao.SyncEventDao
import com.privategallery.app.data.local.dao.UserDao
import com.privategallery.app.data.local.entity.FolderEntity
import com.privategallery.app.data.local.entity.MediaItemEntity
import com.privategallery.app.data.local.entity.SyncEventEntity
import com.privategallery.app.data.local.entity.UserEntity

@Database(
    entities = [UserEntity::class, FolderEntity::class, MediaItemEntity::class, SyncEventEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun folderDao(): FolderDao
    abstract fun mediaDao(): MediaDao
    abstract fun syncEventDao(): SyncEventDao
}
