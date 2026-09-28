package com.privategallery.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderRequest(
    val encryptedNameB64: String,
    val encryptedNameNonceB64: String
)

@Serializable
data class FolderDto(
    val id: String,
    val ownerUserId: String,
    val participantUserId: String?,
    val encryptedNameB64: String,
    val encryptedNameNonceB64: String,
    val encryptedThumbnailRef: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class CreateInviteRequest(
    val folderId: String,
    val inviteeIdentifier: String, // username, email, or invite code depending on inviteMethod
    val inviteMethod: String, // USERNAME, EMAIL, CODE, QR
    val wrappedFolderKeyB64: String,
    val ourEphemeralPublicKeyX25519B64: String,
    val expiresAt: Long
)

@Serializable
data class InviteDto(
    val id: String,
    val folderId: String,
    val inviterId: String,
    val wrappedFolderKeyB64: String,
    val inviterEphemeralPublicKeyX25519B64: String,
    val status: String,
    val expiresAt: Long
)

@Serializable
data class AcceptInviteRequest(val inviteId: String, val ourPublicKeyX25519B64: String)

@Serializable
data class RemoveParticipantRequest(val folderId: String)
