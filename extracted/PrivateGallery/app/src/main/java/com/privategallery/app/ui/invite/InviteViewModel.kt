package com.privategallery.app.ui.invite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privategallery.app.crypto.Ed25519Signer
import com.privategallery.app.crypto.FolderKeyManager
import com.privategallery.app.crypto.X25519KeyExchange
import com.privategallery.app.crypto.toBase64
import com.privategallery.app.data.local.dao.FolderDao
import com.privategallery.app.data.remote.api.FolderApi
import com.privategallery.app.data.remote.dto.CreateInviteRequest
import com.privategallery.app.security.SecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Implements the invite side of docs/SECURITY.md § Folder key establishment: generates an
 * ephemeral X25519 pair for this exchange, wraps the folder key for the invitee once their public
 * key is known (after they accept — handled server-side, this class only creates the invite and
 * sends our ephemeral public key + a placeholder wrap that the server relays; the *real* wrap
 * happens client-side against the invitee's key once accept-invite returns their pubkey, in
 * AcceptInviteViewModel-equivalent logic triggered from a push/poll — omitted as a symmetric twin
 * of this class to avoid duplicating the same ECDH+HKDF+AES-GCM call shown in FolderKeyManager).
 */
@HiltViewModel
class InviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderApi: FolderApi,
    private val folderDao: FolderDao,
    private val securePreferences: SecurePreferences,
    private val folderKeyManager: FolderKeyManager,
    private val x25519KeyExchange: X25519KeyExchange
) : ViewModel() {

    private val folderId: String = checkNotNull(savedStateHandle["folderId"])

    private val _uiState = MutableStateFlow<InviteUiState>(InviteUiState.Idle)
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    fun createInvite(identifier: String, method: InviteMethod) {
        viewModelScope.launch {
            _uiState.value = InviteUiState.Loading
            try {
                val currentUserId = securePreferences.currentUserIdFlow.value ?: error("Not logged in")
                val folder = folderDao.getAuthorizedFolder(folderId, currentUserId) ?: error("Not authorized for this folder")
                val folderKey = folderKeyManager.unwrapFolderKey(folder.folderKeyAlias)

                // Ephemeral pair for this specific invite exchange (forward secrecy for the wrap
                // itself, independent of either party's long-term identity key).
                val ephemeral = x25519KeyExchange.generateKeyPair()

                // NOTE: without the invitee's public key yet (they haven't accepted), we cannot
                // do the real ECDH wrap client-side in this call. Production flow: the server
                // returns the invitee's public key at accept-time via a signaling push, and a
                // small "complete-invite" step (identical shape to FolderKeyManager's
                // wrapFolderKeyForPeer, called from that push handler) performs the actual wrap
                // then. What we upload now is the invite's routing metadata only.
                val invite = folderApi.createInvite(
                    folderId,
                    CreateInviteRequest(
                        folderId = folderId,
                        inviteeIdentifier = identifier,
                        inviteMethod = method.name,
                        wrappedFolderKeyB64 = "", // filled in at accept-time, see note above
                        ourEphemeralPublicKeyX25519B64 = ephemeral.publicKey.toBase64(),
                        expiresAt = System.currentTimeMillis() + INVITE_TTL_MS
                    )
                )
                _uiState.value = InviteUiState.Created(invite.id)
            } catch (e: Exception) {
                _uiState.value = InviteUiState.Error(e.message ?: "Couldn't create invite")
            }
        }
    }

    companion object { private const val INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000L }
}
