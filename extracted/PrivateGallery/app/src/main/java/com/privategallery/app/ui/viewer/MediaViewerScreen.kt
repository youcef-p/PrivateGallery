package com.privategallery.app.ui.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.privategallery.app.domain.model.MediaType
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    folderId: String,
    initialMediaId: String,
    onNavigateBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = uiState.currentIndex) { uiState.items.size.coerceAtLeast(1) }

    LaunchedEffect(pagerState.currentPage) { viewModel.onPageChanged(pagerState.currentPage) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                },
                actions = {
                    if (uiState.secureModeEnabled) {
                        Icon(Icons.Default.Lock, contentDescription = "Secure mode: screenshots blocked", tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            if (uiState.items.isEmpty()) {
                if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val item = uiState.items[page]
                    val pathForThisPage = if (page == uiState.currentIndex) uiState.decryptedPath else null
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            pathForThisPage == null -> CircularProgressIndicator(color = Color.White)
                            item.type == MediaType.IMAGE -> ZoomableImage(path = pathForThisPage)
                            else -> VideoPlayer(path = pathForThisPage)
                        }
                    }
                }

                val current = uiState.items.getOrNull(uiState.currentIndex)
                if (current != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Text(
                            DateFormat.getDateTimeInstance().format(Date(current.createdAt)),
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            uiState.error?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this item?") },
            text = { Text("This will remove it for both of you once your partner's device syncs the deletion.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCurrent(); showDeleteConfirm = false; onNavigateBack() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ZoomableImage(path: String) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    AsyncImage(
        model = path, contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    )
}

@Composable
private fun VideoPlayer(path: String) {
    val context = LocalContext.current
    val exoPlayer = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(path))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(path) { onDispose { exoPlayer.release() } }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { PlayerView(it).apply { player = exoPlayer } },
        modifier = Modifier.fillMaxSize()
    )
}
