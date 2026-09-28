package com.privategallery.signaling.ws

import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class SignalingEnvelope(
    val type: String,
    val from: String? = null,
    val to: String? = null,
    val folderId: String? = null,
    val payload: JsonElement
)

/**
 * Pure relay: routes an envelope from one authenticated user's socket to another's by user id,
 * without parsing or persisting `payload` beyond what's needed for `sync-event`/`blob-ready`
 * (which are themselves just routing metadata, not media). Presence is tracked only as
 * "this userId has an open socket right now" — never persisted, never broadcast beyond the one
 * other participant of a shared folder (the caller of notifyPresence supplies exactly who to
 * tell, resolved from FolderMembers, not from this class).
 */
object SignalingHub {
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun register(userId: String, session: DefaultWebSocketServerSession) {
        sessions[userId] = session
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val envelope = runCatching { json.decodeFromString(SignalingEnvelope.serializer(), frame.readText()) }.getOrNull() ?: continue
                    route(userId, envelope)
                }
            }
        } finally {
            sessions.remove(userId, session)
        }
    }

    private suspend fun route(fromUserId: String, envelope: SignalingEnvelope) {
        val target = envelope.to ?: return
        val targetSession = sessions[target] ?: return // recipient offline — caller falls back to relay/blob path
        val outgoing = envelope.copy(from = fromUserId)
        targetSession.send(Frame.Text(json.encodeToString(SignalingEnvelope.serializer(), outgoing)))
    }

    fun isOnline(userId: String): Boolean = sessions.containsKey(userId)

    suspend fun notifyPresence(aboutUserId: String, tellUserId: String, online: Boolean) {
        val session = sessions[tellUserId] ?: return
        val envelope = SignalingEnvelope(
            type = "presence", from = aboutUserId, to = tellUserId,
            payload = kotlinx.serialization.json.buildJsonObject { put("online", kotlinx.serialization.json.JsonPrimitive(online)) }
        )
        session.send(Frame.Text(json.encodeToString(SignalingEnvelope.serializer(), envelope)))
    }
}
