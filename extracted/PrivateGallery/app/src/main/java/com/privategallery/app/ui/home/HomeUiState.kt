package com.privategallery.app.ui.home

import com.privategallery.app.domain.model.Folder

data class HomeUiState(
    val isLoading: Boolean = true,
    val folders: List<Folder> = emptyList(),
    val isOffline: Boolean = false,
    val currentUsername: String = "",
    val error: String? = null
)
