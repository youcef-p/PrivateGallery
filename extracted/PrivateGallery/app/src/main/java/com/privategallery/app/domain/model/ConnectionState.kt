package com.privategallery.app.domain.model

sealed class ConnectionState {
    data object Offline : ConnectionState()
    data object Connecting : ConnectionState()
    data object OnlineRelay : ConnectionState() // signaling connected, peer via relay fallback
    data object OnlineP2P : ConnectionState()   // WebRTC data channel established with peer
    data class Error(val message: String) : ConnectionState()
}
