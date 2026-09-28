package com.privategallery.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.privategallery.app.data.local.dao.FolderDao
import com.privategallery.app.data.local.dao.MediaDao
import com.privategallery.app.data.local.entity.SyncStatus
import com.privategallery.app.data.remote.api.MediaApi
import com.privategallery.app.data.remote.dto.InitiateBlobUploadRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import java.io.File
import java.io.RandomAccessFile

/**
 * Resumable relay-fallback uploader/downloader. Only invoked by SyncEngine after the bounded
 * P2P negotiation window elapses without an open data channel (see SyncEngine.P2P_TIMEOUT_MS),
 * or directly by pull-to-refresh for anything still PENDING. Uploads chunks that are *already*
 * AES-256-GCM ciphertext (produced by MediaEncryptor at add-time) — this worker never sees
 * plaintext.
 *
 * Resumability: progress is persisted per-mediaId as `transferProgress` (chunk count uploaded),
 * so a process death (app killed mid-transfer) resumes from the last acknowledged chunk on the
 * next WorkManager run rather than restarting the whole file.
 */
@HiltWorker
class MediaTransferWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaDao: MediaDao,
    private val folderDao: FolderDao,
    private val mediaApi: MediaApi
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mediaId = inputData.getString(KEY_MEDIA_ID) ?: return Result.failure()
        val media = mediaDao.getMedia(mediaId) ?: return Result.failure()
        if (media.syncStatus != SyncStatus.PENDING && media.syncStatus != SyncStatus.FAILED) {
            return Result.success() // already handled (e.g. delivered via P2P in the meantime)
        }

        val encryptedFile = File(media.localEncryptedFilePath ?: return Result.failure())
        if (!encryptedFile.exists()) return Result.failure()

        return try {
            mediaDao.updateSyncStatus(mediaId, SyncStatus.UPLOADING, media.transferProgress)

            val chunkOffsets = indexChunkOffsets(encryptedFile)
            val totalChunks = chunkOffsets.size
            val startChunk = (media.transferProgress * totalChunks / 100).coerceIn(0, totalChunks)

            val initiate = mediaApi.initiateBlobUpload(
                InitiateBlobUploadRequest(
                    folderId = media.folderId, mediaId = mediaId,
                    sizeBytes = media.sizeBytes, chunkCount = totalChunks, mimeType = media.mimeType
                )
            )

            RandomAccessFile(encryptedFile, "r").use { raf ->
                for (chunkIndex in startChunk until totalChunks) {
                    raf.seek(chunkOffsets[chunkIndex])
                    val lenBuf = ByteArray(4); raf.readFully(lenBuf)
                    val len = ((lenBuf[0].toInt() and 0xFF) shl 24) or ((lenBuf[1].toInt() and 0xFF) shl 16) or
                        ((lenBuf[2].toInt() and 0xFF) shl 8) or (lenBuf[3].toInt() and 0xFF)
                    val chunkCiphertext = ByteArray(len); raf.readFully(chunkCiphertext)

                    val body = MultipartBody.Part.createFormData(
                        "chunk", "chunk_$chunkIndex",
                        chunkCiphertext.toRequestBodyBytes("application/octet-stream".toMediaTypeOrNull())
                    )
                    mediaApi.uploadChunk(initiate.blobId, chunkIndex, body)

                    val progress = ((chunkIndex + 1) * 100 / totalChunks)
                    mediaDao.updateSyncStatus(mediaId, SyncStatus.UPLOADING, progress)
                }
            }

            mediaDao.updateSyncStatus(mediaId, SyncStatus.SYNCED, 100)
            Result.success()
        } catch (e: Exception) {
            mediaDao.updateSyncStatus(mediaId, SyncStatus.FAILED, media.transferProgress)
            Result.retry()
        }
    }

    /** Walks the chunk-framed encrypted file (see MediaEncryptor) to build a byte-offset index
     *  without loading the whole file, so resuming a huge video doesn't require re-reading it. */
    private fun indexChunkOffsets(file: File): List<Long> {
        val offsets = mutableListOf<Long>()
        RandomAccessFile(file, "r").use { raf ->
            while (raf.filePointer < raf.length()) {
                offsets += raf.filePointer
                val lenBuf = ByteArray(4); raf.readFully(lenBuf)
                val len = ((lenBuf[0].toInt() and 0xFF) shl 24) or ((lenBuf[1].toInt() and 0xFF) shl 16) or
                    ((lenBuf[2].toInt() and 0xFF) shl 8) or (lenBuf[3].toInt() and 0xFF)
                raf.seek(raf.filePointer + len)
            }
        }
        return offsets
    }

    private fun ByteArray.toRequestBodyBytes(mediaType: okhttp3.MediaType?) =
        object : okhttp3.RequestBody() {
            override fun contentType() = mediaType
            override fun contentLength() = this@toRequestBodyBytes.size.toLong()
            override fun writeTo(sink: BufferedSink) { sink.write(this@toRequestBodyBytes) }
        }

    companion object {
        const val KEY_MEDIA_ID = "media_id"
        const val WORK_NAME_PREFIX = "media-transfer-"
    }
}
