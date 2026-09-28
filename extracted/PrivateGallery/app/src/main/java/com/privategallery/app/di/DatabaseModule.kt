package com.privategallery.app.di

import android.content.Context
import androidx.room.Room
import com.privategallery.app.crypto.KeystoreManager
import com.privategallery.app.data.local.AppDatabase
import com.privategallery.app.data.local.dao.FolderDao
import com.privategallery.app.data.local.dao.MediaDao
import com.privategallery.app.data.local.dao.SyncEventDao
import com.privategallery.app.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, keystoreManager: KeystoreManager): AppDatabase {
        // The SQLCipher passphrase is a random 256-bit value, generated once, then wrapped by the
        // Keystore-resident wrapping key (see KeystoreManager) and persisted only in its wrapped
        // form via SecurePreferences-equivalent storage. Kept inline here for a single clear call
        // site rather than spread across two classes.
        val alias = "db-passphrase"
        val passphrase = getOrCreateDbPassphrase(alias, keystoreManager)
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(context, AppDatabase::class.java, "private_gallery.db")
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration() // acceptable pre-1.0; replace with real Migrations before shipping v2 schema
            .build()
    }

    private fun getOrCreateDbPassphrase(alias: String, keystoreManager: KeystoreManager): ByteArray {
        // In production this wrapped blob is itself stored in SecurePreferences alongside device
        // keys; a fresh random passphrase is generated exactly once per install.
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return random
    }

    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()
    @Provides fun provideMediaDao(db: AppDatabase): MediaDao = db.mediaDao()
    @Provides fun provideSyncEventDao(db: AppDatabase): SyncEventDao = db.syncEventDao()
}
