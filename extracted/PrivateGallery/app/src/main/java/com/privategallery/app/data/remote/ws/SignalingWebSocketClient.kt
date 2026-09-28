package com.privategallery.app.data.remote.ws

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

sealed class SignalingConnectionEvent {
    data object Connected : SignalingConnectionEvent()
    data class Disconnected(val reason: String) : SignalingConnectionEvent()
    data class Failed(val throwable: Throwable) : SignalingConnectionEvent()
}

/**
 * Persistent authenticated WebSocket to the signaling server. Never contains decrypted media or
 * folder keys — see docs/SYNC_PROTOCOL.md for the full message catalogue. This class only owns
 * the transport; SyncEngine interprets messages.
 */
@Singleton
class SignalingWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val _incoming = MutableSharedFlow<SignalingEnvelope>(extraBufferCapacity = 64)
    val incoming: Flow<SignalingEnvelope> get() = _incoming

    fun connect(wsUrl: String, accessToken: String): Flow<SignalingConnectionEvent> = callbackFlow {
        val request = Request.Builder()
            .url("$wsUrl?token=$accessToken")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(SignalingConnectionEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { json.decodeFromString(SignalingEnvelope.serializer(), text) }
                    .onSuccess { _incoming.tryEmit(it) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(SignalingConnectionEvent.Disconnected(reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(SignalingConnectionEvent.Failed(t))
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)

        awaitClose {
            webSocket?.close(1000, "client closing")
            webSocket = null
        }
    }

    fun send(envelope: SignalingEnvelope) {
        val text = json.encodeToString(SignalingEnvelope.serializer(), envelope)
        webSocket?.send(text)
    }

    fun disconnect() {
        webSocket?.close(1000, "client requested disconnect")
        webSocket = null
    }
}
