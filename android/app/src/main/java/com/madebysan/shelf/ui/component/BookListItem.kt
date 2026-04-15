package com.madebysan.shelf.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.madebysan.shelf.data.local.entity.BookEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    book: BookEntity,
    onClick: () -> Unit,
    onStarToggle: () -> Unit = {},
    onHideToggle: () -> Unit = {},
    onCompleteToggle: () -> Unit = {},
    onDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box {
        ListItem(
            headlineContent = {
                Text(
                    text = book.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                val parts = mutableListOf<String>()
                book.author?.let { parts.add(it) }
                if (book.fileSize > 0) {
                    parts.add("${book.fileSize / 1_048_576} MB")
                }
                if (parts.isNotEmpty()) {
                    Text(
                        text = parts.joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverArtUrl != null) {
                        AsyncImage(
                            model = book.coverArtUrl,
                            contentDescription = book.title,
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val hash = book.title.hashCode()
                        val hue = (hash and 0xFF) / 255f * 360f
                        Box(
                            modifier = Modifier
                                .size(48.dp)
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
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            trailingContent = {
                if (book.isDownloaded) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                } else if (book.isStarred) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Starred",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(18.dp)
                    )
                } else if (book.isCompleted) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (book.duration > 0 && book.playbackPosition > 0) {
                    val percent = ((book.playbackPosition.toFloat() / book.duration) * 100).toInt()
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            )
        )

        // Long-press context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (book.isStarred) "Unstar" else "Star") },
                onClick = { onStarToggle(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text(if (book.isHidden) "Unhide" else "Hide") },
                onClick = { onHideToggle(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text(if (book.isCompleted) "Mark Not Completed" else "Mark Completed") },
                onClick = { onCompleteToggle(); showMenu = false }
            )
            if (book.isDownloaded && onRemoveDownload != null) {
                DropdownMenuItem(
                    text = { Text("Remove Download") },
                    onClick = { onRemoveDownload(); showMenu = false }
                )
            } else if (!book.isDownloaded && onDownload != null) {
                DropdownMenuItem(
                    text = { Text("Download") },
                    onClick = { onDownload(); showMenu = false }
                )
            }
        }
    }
}
