package com.privategallery.app.ui.folder

import com.privategallery.app.domain.model.ConnectionState
import com.privategallery.app.domain.model.Folder
import com.privategallery.app.domain.model.MediaItem

data class FolderUiState(
    val isLoading: Boolean = true,
    val folder: Folder? = null,
    val media: List<MediaItem> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Offline,
    val isRefreshing: Boolean = false,
    val error: String? = null
)
