package com.privategallery.app.di

import com.privategallery.app.data.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
    @Binds @Singleton abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository
}
