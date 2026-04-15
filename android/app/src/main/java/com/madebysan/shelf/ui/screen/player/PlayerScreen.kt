package com.madebysan.shelf.ui.screen.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.madebysan.shelf.ui.component.AddBookmarkDialog
import com.madebysan.shelf.ui.component.BookmarkList
import com.madebysan.shelf.ui.component.ChapterListSheet
import com.madebysan.shelf.ui.component.SleepTimerDialog
import com.madebysan.shelf.ui.component.SpeedSelector

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onDiscoverNext: (Long) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val book = uiState.book
    val exoPlayer by viewModel.player.collectAsState()

    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showBookmarkList by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Sleep timer
                    IconButton(onClick = { showSleepTimer = true }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (uiState.sleepTimerMinutes != 0 || uiState.sleepEndOfChapter)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Chapters (only show if available)
                    if (uiState.chapters.isNotEmpty()) {
                        IconButton(onClick = { showChapterList = true }) {
                            Icon(Icons.Filled.FormatListNumbered, contentDescription = "Chapters")
                        }
                    }
                    // Bookmarks
                    IconButton(onClick = { showBookmarkList = true }) {
                        Icon(Icons.Filled.Bookmarks, contentDescription = "Bookmarks")
                    }
                    // Add bookmark
                    IconButton(onClick = { showBookmarkDialog = true }) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Add bookmark")
                    }
                    // Actions menu (download, mark complete, etc.)
                    Box {
                        IconButton(onClick = { showActionsMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false }
                        ) {
                            val isDownloading = uiState.downloadProgress != null
                            val isDownloaded = book?.isDownloaded == true

                            if (isDownloading) {
                                val progress = uiState.downloadProgress
                                DropdownMenuItem(
                                    text = {
                                        Text("Cancel Download" + if (progress != null && progress.totalBytes > 0)
                                            " (${(progress.fraction * 100).toInt()}%)" else "")
                                    },
                                    onClick = {
                                        viewModel.cancelDownload()
                                        showActionsMenu = false
                                    }
                                )
                            } else if (isDownloaded) {
                                DropdownMenuItem(
                                    text = { Text("Remove Download") },
                                    onClick = {
                                        viewModel.removeDownload()
                                        showActionsMenu = false
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Download") },
                                    onClick = {
                                        viewModel.downloadBook()
                                        showActionsMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (book == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isVideo && exoPlayer != null) {
                // Video surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                    },
                    update = { view -> view.player = exoPlayer },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                // Cover art
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverArtUrl != null) {
                        AsyncImage(
                            model = book.coverArtUrl,
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val hash = book.title.hashCode()
                        val hue = (hash and 0xFF) / 255f * 360f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.hsl(hue, 0.4f, 0.35f),
                                            Color.hsl((hue + 40f) % 360f, 0.3f, 0.25f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Headphones,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (book.author != null) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Current chapter name
            if (uiState.currentChapterName != null) {
                Text(
                    text = uiState.currentChapterName!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Discover mode banner
            if (uiState.isDiscoverMode) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFFFFF3E0),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Discover \u2014 progress not saved",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFE65100),
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.getRandomBookId { nextId ->
                                if (nextId != null) onDiscoverNext(nextId)
                            }
                        }
                    ) {
                        Text("Next", style = MaterialTheme.typography.labelMedium)
                    }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.exitDiscoverMode()
                            onBack()
                        }
                    ) {
                        Text("Exit", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Sleep timer indicator
            if (uiState.sleepTimerMinutes > 0 || uiState.sleepEndOfChapter) {
                Spacer(modifier = Modifier.height(8.dp))
                val timerText = if (uiState.sleepEndOfChapter) {
                    "Sleep at end of chapter"
                } else {
                    val remaining = uiState.sleepTimerRemaining / 1000
                    val min = remaining / 60
                    val sec = remaining % 60
                    "Sleep in ${min}:${String.format("%02d", sec)}"
                }
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Scrubber
            var isSeeking by remember { mutableStateOf(false) }
            var seekPosition by remember { mutableFloatStateOf(0f) }
            val displayPosition = if (isSeeking) seekPosition else uiState.currentPosition.toFloat()
            val maxDuration = uiState.duration.toFloat().coerceAtLeast(1f)

            Slider(
                value = displayPosition,
                onValueChange = {
                    isSeeking = true
                    seekPosition = it
                },
                onValueChangeFinished = {
                    viewModel.seekTo(seekPosition.toLong())
                    isSeeking = false
                },
                valueRange = 0f..maxDuration,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(displayPosition.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(uiState.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedSelector(
                    currentSpeed = uiState.playbackSpeed,
                    onSpeedSelected = { viewModel.setSpeed(it) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { viewModel.skipBack() }) {
                    Icon(
                        if (uiState.isVideo) Icons.Filled.Replay10 else Icons.Filled.Replay30,
                        contentDescription = if (uiState.isVideo) "Back 10s" else "Back 30s",
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                    } else {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { viewModel.skipForward() }) {
                    Icon(
                        if (uiState.isVideo) Icons.Filled.Forward10 else Icons.Filled.Forward30,
                        contentDescription = if (uiState.isVideo) "Forward 10s" else "Forward 30s",
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Dialogs
    if (showBookmarkDialog) {
        AddBookmarkDialog(
            currentPositionFormatted = formatTime(uiState.currentPosition),
            onDismiss = { showBookmarkDialog = false },
            onConfirm = { name, note ->
                viewModel.addBookmark(name, note)
                showBookmarkDialog = false
            }
        )
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            currentMinutes = uiState.sleepTimerMinutes,
            hasChapters = uiState.chapters.isNotEmpty(),
            onDismiss = { showSleepTimer = false },
            onSelect = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimer = false
            }
        )
    }

    // Bookmark bottom sheet
    if (showBookmarkList) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarkList = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Text(
                text = "Bookmarks",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            BookmarkList(
                bookmarks = uiState.bookmarks,
                onBookmarkTap = { bookmark ->
                    viewModel.jumpToBookmark(bookmark)
                    showBookmarkList = false
                },
                onBookmarkDelete = { viewModel.deleteBookmark(it) },
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }

    // Chapter list bottom sheet
    if (showChapterList) {
        ModalBottomSheet(
            onDismissRequest = { showChapterList = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            ChapterListSheet(
                chapters = uiState.chapters,
                currentChapterIndex = uiState.currentChapterIndex,
                onChapterTap = { chapter ->
                    viewModel.goToChapter(chapter)
                    showChapterList = false
                },
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}
