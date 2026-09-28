package com.privategallery.signaling.routes

import com.privategallery.signaling.model.FolderMembers
import com.privategallery.signaling.model.Folders
import com.privategallery.signaling.model.Invites
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

@Serializable data class CreateFolderRequest(val encryptedNameB64: String, val encryptedNameNonceB64: String)
@Serializable data class FolderResponse(val id: String, val ownerUserId: String, val participantUserId: String?, val encryptedNameB64: String, val encryptedNameNonceB64: String, val encryptedThumbnailRef: String?, val status: String, val createdAt: Long, val updatedAt: Long)
@Serializable data class CreateInviteRequest(val folderId: String, val inviteeIdentifier: String, val inviteMethod: String, val wrappedFolderKeyB64: String, val ourEphemeralPublicKeyX25519B64: String, val expiresAt: Long)
@Serializable data class InviteResponse(val id: String, val folderId: String, val status: String)
@Serializable data class RemoveParticipantRequest(val folderId: String)

fun Route.folderRoutes() {
    authenticate("jwt") {
        route("/folders") {

            get {
                val userId = call.userId()
                // Access-control invariant, enforced server-side (mirrors FolderDao's client
                // query, defense in depth): only folders where this user is owner or an ACTIVE
                // member are ever returned.
                val rows = transaction {
                    (Folders innerJoin FolderMembers)
                        .select { (FolderMembers.userId eq userId) and (FolderMembers.status eq "ACTIVE") }
                        .map { it.toFolderResponse() }
                }
                call.respond(rows)
            }

            post {
                val userId = call.userId()
                val body = call.receive<CreateFolderRequest>()
                val folderId = UUID.randomUUID().toString()
                val now = Instant.now()
                transaction {
                    Folders.insert {
                        it[id] = folderId
                        it[ownerId] = userId
                        it[participantId] = null
                        it[encryptedName] = Base64Decoder.decode(body.encryptedNameB64)
                        it[status] = "PENDING_INVITE"
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    FolderMembers.insert {
                        it[FolderMembers.folderId] = folderId
                        it[FolderMembers.userId] = userId
                        it[FolderMembers.status] = "ACTIVE"
                        it[joinedAt] = now
                    }
                }
                call.respond(FolderResponse(folderId, userId, null, body.encryptedNameB64, "", null, "PENDING_INVITE", now.toEpochMilli(), now.toEpochMilli()))
            }

            post("/{folderId}/invites") {
                val userId = call.userId()
                val folderId = call.parameters["folderId"]!!
                if (!AccessControl.isActiveMember(folderId, userId)) return@post call.respond(HttpStatusCode.Forbidden)

                val body = call.receive<CreateInviteRequest>()
                val inviteId = UUID.randomUUID().toString()
                transaction {
                    Invites.insert {
                        it[id] = inviteId
                        it[Invites.folderId] = folderId
                        it[inviterId] = userId
                        it[inviteeIdentifier] = body.inviteeIdentifier
                        it[inviteMethod] = body.inviteMethod
                        it[wrappedFolderKey] = null // filled in once invitee's pubkey is known — see docs/SECURITY.md
                        it[inviterEphemeralPubKey] = body.ourEphemeralPublicKeyX25519B64
                        it[status] = "PENDING"
                        it[expiresAt] = Instant.ofEpochMilli(body.expiresAt)
                    }
                }
                call.respond(InviteResponse(inviteId, folderId, "PENDING"))
            }

            post("/{folderId}/remove-participant") {
                val userId = call.userId()
                val folderId = call.parameters["folderId"]!!
                val folder = transaction { Folders.select { Folders.id eq folderId }.singleOrNull() }
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                if (folder[Folders.ownerId] != userId) return@post call.respond(HttpStatusCode.Forbidden, "Only the owner can remove the participant")

                transaction {
                    val participantId = folder[Folders.participantId]
                    if (participantId != null) {
                        FolderMembers.update({ (FolderMembers.folderId eq folderId) and (FolderMembers.userId eq participantId) }) {
                            it[status] = "REMOVED"
                        }
                    }
                    Folders.update({ Folders.id eq folderId }) { it[Folders.participantId] = null; it[updatedAt] = Instant.now() }
                }
                call.respond(HttpStatusCode.OK)
            }

            delete("/{folderId}") {
                val userId = call.userId()
                val folderId = call.parameters["folderId"]!!
                val folder = transaction { Folders.select { Folders.id eq folderId }.singleOrNull() }
                    ?: return@delete call.respond(HttpStatusCode.NotFound)
                if (folder[Folders.ownerId] != userId) return@delete call.respond(HttpStatusCode.Forbidden)

                transaction {
                    FolderMembers.deleteWhere { FolderMembers.folderId eq folderId }
                    Folders.deleteWhere { Folders.id eq folderId }
                }
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

private fun ResultRow.toFolderResponse() = FolderResponse(
    id = this[Folders.id], ownerUserId = this[Folders.ownerId], participantUserId = this[Folders.participantId],
    encryptedNameB64 = java.util.Base64.getEncoder().encodeToString(this[Folders.encryptedName]),
    encryptedNameNonceB64 = "", encryptedThumbnailRef = this[Folders.encryptedThumbnailRef],
    status = this[Folders.status], createdAt = this[Folders.createdAt].toEpochMilli(), updatedAt = this[Folders.updatedAt].toEpochMilli()
)

private object Base64Decoder { fun decode(s: String): ByteArray = java.util.Base64.getDecoder().decode(s) }

private fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
