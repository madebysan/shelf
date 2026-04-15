package com.madebysan.shelf.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.madebysan.shelf.data.local.entity.BookEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Column {
                // Cover art area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                ) {
                    if (book.coverArtUrl != null) {
                        AsyncImage(
                            model = book.coverArtUrl,
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Placeholder with gradient
                        val hash = book.title.hashCode()
                        val hue = (hash and 0xFF) / 255f * 360f
                        val color1 = Color.hsl(hue, 0.4f, 0.35f)
                        val color2 = Color.hsl((hue + 40f) % 360f, 0.3f, 0.25f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(color1, color2))),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Headphones,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Star badge
                    if (book.isStarred) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Starred",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }

                    // Progress bar at bottom of cover
                    if (book.duration > 0 && book.playbackPosition > 0) {
                        LinearProgressIndicator(
                            progress = { (book.playbackPosition.toFloat() / book.duration).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Black.copy(alpha = 0.3f)
                        )
                    }
                }

                // Title + author below cover (fixed height for uniform cards)
                Column(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .height(48.dp)
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.author ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

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
}
