package com.privategallery.app.sync.webrtc

import android.content.Context
import com.privategallery.app.BuildConfig
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class PeerConnectionEvent {
    data class LocalSdpCreated(val peerUserId: String, val sdp: SessionDescription) : PeerConnectionEvent()
    data class IceCandidateGenerated(val peerUserId: String, val candidate: IceCandidate) : PeerConnectionEvent()
    data class DataChannelOpen(val peerUserId: String) : PeerConnectionEvent()
    data class DataChannelClosed(val peerUserId: String) : PeerConnectionEvent()
    data class MessageReceived(val peerUserId: String, val data: ByteArray) : PeerConnectionEvent()
    data class ConnectionFailed(val peerUserId: String, val reason: String) : PeerConnectionEvent()
}

/**
 * Wraps GetStream's stream-webrtc-android (a maintained AAR of the upstream WebRTC org build) to
 * expose a small, testable surface: one PeerConnection + one reliable, ordered DataChannel per
 * peer folder-relationship. STUN/TURN servers come from BuildConfig (see app/build.gradle.kts);
 * TURN credentials are short-lived, server-issued (never a static shared secret baked into the
 * APK) in a production deployment — see docs/SYNC_PROTOCOL.md.
 *
 * This class never touches plaintext media: callers hand it already AES-256-GCM-encrypted chunks
 * (see MediaTransferWorker) to send, and hand back encrypted chunks on receive for the caller to
 * decrypt via MediaEncryptor.
 */
@Singleton
class WebRtcPeerManager @Inject constructor(
    context: Context
) {
    private val eglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory
    private val connections = ConcurrentHashMap<String, PeerConnection>()
    private val dataChannels = ConcurrentHashMap<String, DataChannel>()

    var eventListener: ((PeerConnectionEvent) -> Unit)? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false) // never trace/log payloads
                .createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun iceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf(
            PeerConnection.IceServer.builder(BuildConfig.STUN_URL).createIceServer()
        )
        if (BuildConfig.TURN_URL.isNotBlank()) {
            servers += PeerConnection.IceServer.builder(BuildConfig.TURN_URL)
                .setUsername(BuildConfig.TURN_USERNAME)
                .setPassword(BuildConfig.TURN_CREDENTIAL)
                .createIceServer()
        }
        return servers
    }

    fun createOffer(peerUserId: String) {
        val pc = getOrCreateConnection(peerUserId, isOfferer = true)
        val constraints = MediaConstraints()
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SdpObserverAdapter(), sdp)
                eventListener?.invoke(PeerConnectionEvent.LocalSdpCreated(peerUserId, sdp))
            }
        }, constraints)
    }

    fun onRemoteOfferReceived(peerUserId: String, sdp: SessionDescription) {
        val pc = getOrCreateConnection(peerUserId, isOfferer = false)
        pc.setRemoteDescription(SdpObserverAdapter(), sdp)
        pc.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(answer: SessionDescription) {
                pc.setLocalDescription(SdpObserverAdapter(), answer)
                eventListener?.invoke(PeerConnectionEvent.LocalSdpCreated(peerUserId, answer))
            }
        }, MediaConstraints())
    }

    fun onRemoteAnswerReceived(peerUserId: String, sdp: SessionDescription) {
        connections[peerUserId]?.setRemoteDescription(SdpObserverAdapter(), sdp)
    }

    fun onRemoteIceCandidate(peerUserId: String, candidate: IceCandidate) {
        connections[peerUserId]?.addIceCandidate(candidate)
    }

    fun sendBytes(peerUserId: String, data: ByteArray): Boolean {
        val channel = dataChannels[peerUserId] ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(data), true)
        return channel.send(buffer)
    }

    fun closeConnection(peerUserId: String) {
        dataChannels.remove(peerUserId)?.close()
        connections.remove(peerUserId)?.close()
    }

    private fun getOrCreateConnection(peerUserId: String, isOfferer: Boolean): PeerConnection {
        connections[peerUserId]?.let { return it }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        val observer = object : PeerConnectionObserverAdapter() {
            override fun onIceCandidate(candidate: IceCandidate) {
                eventListener?.invoke(PeerConnectionEvent.IceCandidateGenerated(peerUserId, candidate))
            }

            override fun onDataChannel(channel: DataChannel) {
                registerDataChannel(peerUserId, channel)
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                if (newState == PeerConnection.IceConnectionState.FAILED) {
                    eventListener?.invoke(PeerConnectionEvent.ConnectionFailed(peerUserId, "ICE failed"))
                }
            }
        }

        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
            ?: error("Failed to create PeerConnection for $peerUserId")

        connections[peerUserId] = pc

        if (isOfferer) {
            val init = DataChannel.Init().apply { ordered = true; negotiated = false }
            val channel = pc.createDataChannel("media-sync", init)
            registerDataChannel(peerUserId, channel)
        }

        return pc
    }

    private fun registerDataChannel(peerUserId: String, channel: DataChannel) {
        dataChannels[peerUserId] = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                when (channel.state()) {
                    DataChannel.State.OPEN -> eventListener?.invoke(PeerConnectionEvent.DataChannelOpen(peerUserId))
                    DataChannel.State.CLOSED -> eventListener?.invoke(PeerConnectionEvent.DataChannelClosed(peerUserId))
                    else -> {}
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                eventListener?.invoke(PeerConnectionEvent.MessageReceived(peerUserId, bytes))
            }
        })
    }
}

private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {}
    override fun onSetFailure(error: String) {}
}

private open class PeerConnectionObserverAdapter : PeerConnection.Observer {
    override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
    override fun onIceCandidate(candidate: IceCandidate) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
    override fun onAddStream(stream: MediaStream) {}
    override fun onRemoveStream(stream: MediaStream) {}
    override fun onDataChannel(channel: DataChannel) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
}
