package com.privategallery.app.di

import android.content.Context
import com.privategallery.app.sync.webrtc.WebRtcPeerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {
    @Provides
    @Singleton
    fun provideWebRtcPeerManager(@ApplicationContext context: Context): WebRtcPeerManager =
        WebRtcPeerManager(context)
}
