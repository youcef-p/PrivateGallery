package com.privategallery.app.data.remote.api

import com.privategallery.app.data.remote.dto.*
import retrofit2.http.*

interface FolderApi {
    @GET("folders")
    suspend fun listMyFolders(): List<FolderDto>

    @POST("folders")
    suspend fun createFolder(@Body body: CreateFolderRequest): FolderDto

    @POST("folders/{folderId}/invites")
    suspend fun createInvite(@Path("folderId") folderId: String, @Body body: CreateInviteRequest): InviteDto

    @POST("invites/{inviteId}/accept")
    suspend fun acceptInvite(@Path("inviteId") inviteId: String, @Body body: AcceptInviteRequest): FolderDto

    @POST("invites/{inviteId}/reject")
    suspend fun rejectInvite(@Path("inviteId") inviteId: String)

    @POST("folders/{folderId}/remove-participant")
    suspend fun removeParticipant(@Path("folderId") folderId: String, @Body body: RemoveParticipantRequest)

    @DELETE("folders/{folderId}")
    suspend fun deleteFolder(@Path("folderId") folderId: String)
}
