package com.madebysan.shelf.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madebysan.shelf.data.local.entity.BookmarkEntity

@Composable
fun BookmarkList(
    bookmarks: List<BookmarkEntity>,
    onBookmarkTap: (BookmarkEntity) -> Unit,
    onBookmarkDelete: (BookmarkEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bookmarks.isEmpty()) {
        Text(
            text = "No bookmarks yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    LazyColumn(modifier = modifier) {
        items(bookmarks, key = { it.id }) { bookmark ->
            ListItem(
                headlineContent = { Text(bookmark.name) },
                supportingContent = {
                    val time = formatBookmarkTime(bookmark.timestamp)
                    if (bookmark.note != null) {
                        Text("$time · ${bookmark.note}")
                    } else {
                        Text(time)
                    }
                },
                leadingContent = {
                    IconButton(onClick = { onBookmarkTap(bookmark) }) {
                        Icon(Icons.Filled.Bookmark, contentDescription = "Jump to bookmark")
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onBookmarkDelete(bookmark) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    }
}

private fun formatBookmarkTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}
