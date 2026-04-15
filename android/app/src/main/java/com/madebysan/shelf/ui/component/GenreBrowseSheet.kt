package com.madebysan.shelf.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.madebysan.shelf.data.local.entity.BookEntity

data class GenreInfo(
    val name: String,
    val count: Int,
    val covers: List<ByteArray>
)

@Composable
fun GenreBrowseSheet(
    books: List<BookEntity>,
    selectedGenre: String?,
    onSelectGenre: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val genres = remember(books) {
        val genreCounts = mutableMapOf<String, MutableList<BookEntity>>()
        for (book in books) {
            if (book.isHidden) continue
            val genre = book.genre ?: "Unknown"
            genreCounts.getOrPut(genre) { mutableListOf() }.add(book)
        }
        genreCounts.map { (name, genreBooks) ->
            val covers = genreBooks
                .filter { it.coverArtData != null }
                .take(4)
                .mapNotNull { it.coverArtData }
            GenreInfo(name, genreBooks.size, covers)
        }.sortedByDescending { it.count }
    }

    val totalCount = remember(books) { books.count { !it.isHidden } }
    val allGenresCovers = remember(genres) {
        val covers = mutableListOf<ByteArray>()
        for (genre in genres) {
            for (cover in genre.covers.take(1)) {
                covers.add(cover)
                if (covers.size >= 4) break
            }
            if (covers.size >= 4) break
        }
        covers
    }

    Column(modifier = modifier.padding(bottom = 32.dp)) {
        Text(
            text = "Genres",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // "All Genres" card
            item {
                GenreCard(
                    name = "All Genres",
                    count = totalCount,
                    covers = allGenresCovers,
                    isSelected = selectedGenre == null,
                    onClick = { onSelectGenre(null) }
                )
            }

            items(genres) { genre ->
                GenreCard(
                    name = genre.name,
                    count = genre.count,
                    covers = genre.covers,
                    isSelected = selectedGenre == genre.name,
                    onClick = { onSelectGenre(genre.name) }
                )
            }
        }
    }
}

@Composable
private fun GenreCard(
    name: String,
    count: Int,
    covers: List<ByteArray>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        // Cover collage
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = if (isSelected) {
                androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            when (covers.size) {
                0 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.LibraryBooks,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                1 -> {
                    CoverImage(covers[0], Modifier.fillMaxSize())
                }
                2 -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        CoverImage(covers[0], Modifier.weight(1f).fillMaxSize())
                        CoverImage(covers[1], Modifier.weight(1f).fillMaxSize())
                    }
                }
                3 -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        CoverImage(covers[0], Modifier.weight(1f).fillMaxSize())
                        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                            CoverImage(covers[1], Modifier.weight(1f).fillMaxWidth())
                            CoverImage(covers[2], Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            CoverImage(covers[0], Modifier.weight(1f).fillMaxSize())
                            CoverImage(covers[1], Modifier.weight(1f).fillMaxSize())
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            CoverImage(covers[2], Modifier.weight(1f).fillMaxSize())
                            CoverImage(covers[3], Modifier.weight(1f).fillMaxSize())
                        }
                    }
                }
            }
        }

        // Genre name and count
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "$count ${if (count == 1) "book" else "books"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CoverImage(data: ByteArray, modifier: Modifier = Modifier) {
    val bitmap = remember(data) {
        BitmapFactory.decodeByteArray(data, 0, data.size)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(0.dp))
        )
    }
}
