package com.privategallery.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service used only while an *active, large* transfer (typically video) is in flight,
 * per the requirement that large transfers "must run reliably using foreground services or
 * WorkManager where appropriate." Small photo transfers go through WorkManager alone; this
 * service exists so a multi-hundred-MB video upload survives Doze/App Standby without the OS
 * killing the process mid-transfer. The notification text is deliberately generic — it never
 * names the folder, participant, or filename, to avoid leaking any of that onto the lock screen.
 */
@AndroidEntryPoint
class SyncForegroundService : Service() {

    @Inject lateinit var workManager: WorkManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mediaId = intent?.getStringExtra(EXTRA_MEDIA_ID)
        startForeground(NOTIFICATION_ID, buildNotification())

        if (mediaId != null) {
            val request = OneTimeWorkRequestBuilder<MediaTransferWorker>()
                .setInputData(Data.Builder().putString(MediaTransferWorker.KEY_MEDIA_ID, mediaId).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniqueWork(
                "${MediaTransferWorker.WORK_NAME_PREFIX}$mediaId",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "sync_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Sync", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Syncing")
            .setContentText("Transferring securely")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_MEDIA_ID = "extra_media_id"
        private const val NOTIFICATION_ID = 42
    }
}
