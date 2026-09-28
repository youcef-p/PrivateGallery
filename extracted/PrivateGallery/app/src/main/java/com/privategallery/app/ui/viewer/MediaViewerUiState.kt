package com.privategallery.app.ui.viewer

import com.privategallery.app.domain.model.MediaItem

data class MediaViewerUiState(
    val isLoading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val decryptedPath: String? = null,
    val isDecrypting: Boolean = false,
    val error: String? = null,
    val secureModeEnabled: Boolean = true
)
