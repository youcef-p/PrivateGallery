package com.privategallery.app.ui.folder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.privategallery.app.domain.model.MediaItem
import com.privategallery.app.domain.model.MediaSyncStatus
import com.privategallery.app.domain.model.MediaType
import com.privategallery.app.ui.components.EmptyState
import com.privategallery.app.ui.components.ErrorBanner
import com.privategallery.app.ui.components.SyncStatusBadge
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId: String,
    onOpenMedia: (String) -> Unit,
    onOpenInvite: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncStatus: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
            pullRefreshState.endRefresh()
        }
    }
    LaunchedEffect(uiState.isRefreshing) {
        if (!uiState.isRefreshing) pullRefreshState.endRefresh()
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val cacheFile = File(context.cacheDir, "pending_upload_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input -> cacheFile.outputStream().use { input.copyTo(it) } }
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.addMedia(cacheFile, mimeType)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.folder?.name ?: "Folder", style = MaterialTheme.typography.titleLarge)
                        SyncStatusBadge(state = uiState.connectionState)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (uiState.folder?.participantUserId == null) {
                        IconButton(onClick = onOpenInvite) { Icon(Icons.Default.PersonAdd, contentDescription = "Invite partner") }
                    }
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Sync status") }, onClick = { menuExpanded = false; onOpenSyncStatus() })
                        DropdownMenuItem(text = { Text("Folder settings") }, onClick = { menuExpanded = false; onOpenSettings() })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                pickMedia.launch(ActivityResultContracts.PickVisualMedia.ImageAndVideo.let {
                    androidx.activity.result.PickVisualMediaRequest(it)
                })
            }) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Add photo or video")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column {
                uiState.error?.let { ErrorBanner(it, onRetry = { viewModel.refresh() }) }

                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    uiState.media.isEmpty() -> EmptyState(
                        title = "Nothing here yet",
                        subtitle = "Add a photo or video, or wait for your partner's first share to sync in.",
                        icon = Icons.Default.PhotoLibrary
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.media, key = { it.id }) { item ->
                            MediaThumbnail(item = item, onClick = { onOpenMedia(item.id) })
                        }
                    }
                }
            }
            PullToRefreshContainer(state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun MediaThumbnail(item: MediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier.aspectRatio(1f).padding(2.dp).background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (item.thumbnailPath != null) {
            AsyncImage(
                model = item.thumbnailPath, contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(if (item.syncStatus == MediaSyncStatus.PENDING) 0.5f else 1f)
                    .clickable(onClick = onClick)
            )
        } else {
            Box(Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        if (item.type == MediaType.VIDEO) {
            Icon(
                Icons.Default.PlayCircle, contentDescription = "Video",
                tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
        }
        if (item.syncStatus == MediaSyncStatus.UPLOADING || item.syncStatus == MediaSyncStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { item.transferProgress / 100f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
            )
        }
    }
}
