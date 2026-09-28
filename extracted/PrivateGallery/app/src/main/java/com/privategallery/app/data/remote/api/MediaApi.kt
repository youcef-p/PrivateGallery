package com.privategallery.app.data.remote.api

import com.privategallery.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.*

interface MediaApi {
    @POST("media/blobs/initiate")
    suspend fun initiateBlobUpload(@Body body: InitiateBlobUploadRequest): InitiateBlobUploadResponse

    @Multipart
    @PUT("media/blobs/{blobId}/chunk/{chunkIndex}")
    suspend fun uploadChunk(
        @Path("blobId") blobId: String,
        @Path("chunkIndex") chunkIndex: Int,
        @Part chunk: MultipartBody.Part
    )

    @GET("media/blobs/{blobId}/chunk/{chunkIndex}")
    suspend fun downloadChunk(@Path("blobId") blobId: String, @Path("chunkIndex") chunkIndex: Int): ResponseBody

    @GET("folders/{folderId}/sync-events")
    suspend fun getSyncEventsSince(@Path("folderId") folderId: String, @Query("since") since: Long): List<SyncEventDto>
}
