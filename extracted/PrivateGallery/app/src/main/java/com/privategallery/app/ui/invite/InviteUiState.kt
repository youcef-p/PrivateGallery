package com.privategallery.app.ui.invite

sealed class InviteUiState {
    data object Idle : InviteUiState()
    data object Loading : InviteUiState()
    data class Created(val inviteCode: String) : InviteUiState()
    data class Error(val message: String) : InviteUiState()
}

enum class InviteMethod { USERNAME, EMAIL, CODE, QR }
