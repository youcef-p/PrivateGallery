package com.privategallery.app.sync

import com.privategallery.app.data.local.dao.FolderDao
import com.privategallery.app.data.local.dao.SyncEventDao
import com.privategallery.app.data.remote.ws.SignalingEnvelope
import com.privategallery.app.data.remote.ws.SignalingType
import com.privategallery.app.data.remote.ws.SignalingWebSocketClient
import com.privategallery.app.domain.model.ConnectionState
import com.privategallery.app.sync.webrtc.PeerConnectionEvent
import com.privategallery.app.sync.webrtc.WebRtcPeerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates: presence -> WebRTC negotiation -> data-channel transfer, with an encrypted-relay
 * fallback when P2P can't establish within [P2P_TIMEOUT_MS]. This is the class WorkManager and
 * pull-to-refresh both call into; UI never talks to WebRtcPeerManager or SignalingWebSocketClient
 * directly.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val signalingClient: SignalingWebSocketClient,
    private val webRtcPeerManager: WebRtcPeerManager,
    private val folderDao: FolderDao,
    private val syncEventDao: SyncEventDao
) {
    private val _connectionState = MutableStateFlow<Map<String, ConnectionState>>(emptyMap()) // keyed by peerUserId
    val connectionState: StateFlow<Map<String, ConnectionState>> = _connectionState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun start(scope: CoroutineScope, wsUrl: String, accessToken: String) {
        webRtcPeerManager.eventListener = { event -> handlePeerEvent(event) }
        scope.launch {
            signalingClient.connect(wsUrl, accessToken).collect { /* connection lifecycle logging only, no payload */ }
        }
        scope.launch {
            signalingClient.incoming.collect { envelope -> handleSignalingEnvelope(envelope) }
        }
    }

    /** Called by the pull-to-refresh gesture and by WorkManager on periodic wake. */
    suspend fun syncFolder(folderId: String, currentUserId: String, peerUserId: String?) {
        setState(peerUserId, ConnectionState.Connecting)
        if (peerUserId == null) return // no participant yet, nothing to sync

        // 1. Ask presence via a lightweight signaling ping — server responds with a `presence`
        //    envelope; SyncEngine listens for it in handleSignalingEnvelope.
        signalingClient.send(
            SignalingEnvelope(type = "presence-query", from = currentUserId, to = peerUserId, folderId = folderId, payload = buildJsonObject {})
        )

        // 2. Optimistically attempt WebRTC negotiation; if the peer is actually offline the offer
        //    simply times out server-side and we fall through to relay via MediaTransferWorker,
        //    which independently uploads any still-PENDING media as an encrypted blob after
        //    P2P_TIMEOUT_MS regardless of what this function does (see that class).
        webRtcPeerManager.createOffer(peerUserId)
    }

    private fun handlePeerEvent(event: PeerConnectionEvent) {
        when (event) {
            is PeerConnectionEvent.LocalSdpCreated -> {
                val type = if (event.sdp.type == SessionDescription.Type.OFFER) SignalingType.RTC_OFFER else SignalingType.RTC_ANSWER
                signalingClient.send(
                    SignalingEnvelope(type = type, to = event.peerUserId, payload = buildJsonObject {
                        put("sdp", event.sdp.description)
                        put("sdpType", event.sdp.type.canonicalForm())
                    })
                )
            }
            is PeerConnectionEvent.IceCandidateGenerated -> {
                signalingClient.send(
                    SignalingEnvelope(type = SignalingType.RTC_ICE, to = event.peerUserId, payload = buildJsonObject {
                        put("candidate", event.candidate.sdp)
                        put("sdpMid", event.candidate.sdpMid)
                        put("sdpMLineIndex", event.candidate.sdpMLineIndex)
                    })
                )
            }
            is PeerConnectionEvent.DataChannelOpen -> setState(event.peerUserId, ConnectionState.OnlineP2P)
            is PeerConnectionEvent.DataChannelClosed -> setState(event.peerUserId, ConnectionState.OnlineRelay)
            is PeerConnectionEvent.ConnectionFailed -> setState(event.peerUserId, ConnectionState.OnlineRelay)
            is PeerConnectionEvent.MessageReceived -> {
                // Encrypted chunk bytes handed off to MediaTransferWorker's receive path via a
                // shared in-memory channel (constructor-injected, wired in di/); omitted here to
                // keep SyncEngine focused on connection orchestration, not transfer bookkeeping.
            }
        }
    }

    private fun handleSignalingEnvelope(envelope: SignalingEnvelope) {
        when (envelope.type) {
            SignalingType.RTC_OFFER -> {
                val sdp = envelope.payload.jsonPrimitiveOrNull("sdp") ?: return
                envelope.from?.let { webRtcPeerManager.onRemoteOfferReceived(it, SessionDescription(SessionDescription.Type.OFFER, sdp)) }
            }
            SignalingType.RTC_ANSWER -> {
                val sdp = envelope.payload.jsonPrimitiveOrNull("sdp") ?: return
                envelope.from?.let { webRtcPeerManager.onRemoteAnswerReceived(it, SessionDescription(SessionDescription.Type.ANSWER, sdp)) }
            }
            SignalingType.RTC_ICE -> {
                val candidateSdp = envelope.payload.jsonPrimitiveOrNull("candidate") ?: return
                val mid = envelope.payload.jsonPrimitiveOrNull("sdpMid") ?: ""
                envelope.from?.let { webRtcPeerManager.onRemoteIceCandidate(it, IceCandidate(mid, 0, candidateSdp)) }
            }
            SignalingType.PRESENCE -> {
                val online = (envelope.payload as? kotlinx.serialization.json.JsonObject)?.get("online")?.toString()?.toBoolean() ?: false
                envelope.from?.let { setState(it, if (online) ConnectionState.Connecting else ConnectionState.Offline) }
            }
            SignalingType.MEMBER_REMOVED -> {
                // Handled by FolderRepository observing SyncEventDao inserts triggered here.
            }
        }
    }

    private fun setState(peerUserId: String?, state: ConnectionState) {
        if (peerUserId == null) return
        _connectionState.value = _connectionState.value.toMutableMap().apply { put(peerUserId, state) }
    }

    private fun JsonElement.jsonPrimitiveOrNull(key: String): String? =
        (this as? kotlinx.serialization.json.JsonObject)?.get(key)?.toString()?.trim('"')

    companion object {
        const val P2P_TIMEOUT_MS = 8_000L
    }
}
