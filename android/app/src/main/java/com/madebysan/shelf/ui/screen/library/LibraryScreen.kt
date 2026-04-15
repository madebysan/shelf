package com.madebysan.shelf.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.madebysan.shelf.ui.component.BookCard
import com.madebysan.shelf.ui.component.BookListItem
import com.madebysan.shelf.ui.component.DriveFolderPicker
import com.madebysan.shelf.ui.component.GenreBrowseSheet
import com.madebysan.shelf.ui.component.LibraryManagerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (Long) -> Unit = {},
    onDiscoverClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isPickingFolder) {
        DriveFolderPicker(
            onFolderSelected = { folder -> viewModel.selectFolder(folder) }
        )
        return
    }

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showLibraryManager by remember { mutableStateOf(false) }
    var showGenreBrowse by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (showSearch) {
                TopAppBar(
                    title = {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search books...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            showSearch = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Close search")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            modifier = if (uiState.libraries.size > 1) {
                                Modifier.clickable { showLibraryManager = true }
                            } else Modifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(uiState.libraryFolderName ?: "Library")
                                val total = uiState.allBooks.count { !it.isHidden }
                                val showing = uiState.books.size
                                if (total > 0) {
                                    val subtitle = if (showing != total) {
                                        "$showing of $total books"
                                    } else {
                                        "$total books"
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (uiState.libraries.size > 1) {
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = "Switch library",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        // Sort menu
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                BookSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sort.label,
                                                fontWeight = if (sort == uiState.sort)
                                                    androidx.compose.ui.text.font.FontWeight.Bold
                                                else null
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSort(sort)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        // Overflow menu
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (uiState.isGrid) "List View" else "Grid View") },
                                    onClick = {
                                        viewModel.toggleViewMode()
                                        showOverflowMenu = false
                                    },
                                    leadingIcon = {
                                        @Suppress("DEPRECATION")
                                        val icon = if (uiState.isGrid) Icons.Filled.ViewList else Icons.Filled.GridView
                                        Icon(icon, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.selectedGenre != null) "Genres (${uiState.selectedGenre})"
                                            else "Genres"
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showGenreBrowse = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Category,
                                            contentDescription = null,
                                            tint = if (uiState.selectedGenre != null)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                                if (uiState.books.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Discover") },
                                        onClick = {
                                            showOverflowMenu = false
                                            val randomBook = uiState.books.random()
                                            onDiscoverClick(randomBook.id)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Shuffle, contentDescription = null)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Refresh Library") },
                                    onClick = {
                                        viewModel.refreshLibrary()
                                        showOverflowMenu = false
                                    },
                                    enabled = !uiState.isScanning,
                                    leadingIcon = {
                                        Icon(Icons.Filled.Refresh, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onSettingsClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Settings, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BookFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = filter == uiState.filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            // Cover art loading indicator
            if (uiState.isFetchingCovers) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading cover art...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                uiState.isScanning -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            if (uiState.scanStatus != null) {
                                Text(
                                    text = uiState.scanStatus!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                uiState.books.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "No results"
                                else if (uiState.filter != BookFilter.ALL) "No ${uiState.filter.label.lowercase()} books"
                                else "No audiobooks found",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "Try a different search"
                                else if (uiState.filter != BookFilter.ALL) "Try a different filter"
                                else "Add audio files to your Drive folder",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                uiState.isGrid -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.books, key = { it.id }) { book ->
                            BookCard(
                                book = book,
                                onClick = { onBookClick(book.id) },
                                onStarToggle = { viewModel.toggleStarred(book) },
                                onHideToggle = { viewModel.toggleHidden(book) },
                                onCompleteToggle = { viewModel.toggleCompleted(book) },
                                onDownload = { viewModel.downloadBook(book) },
                                onRemoveDownload = { viewModel.removeDownload(book) }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.books, key = { it.id }) { book ->
                            BookListItem(
                                book = book,
                                onClick = { onBookClick(book.id) },
                                onStarToggle = { viewModel.toggleStarred(book) },
                                onHideToggle = { viewModel.toggleHidden(book) },
                                onCompleteToggle = { viewModel.toggleCompleted(book) },
                                onDownload = { viewModel.downloadBook(book) },
                                onRemoveDownload = { viewModel.removeDownload(book) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLibraryManager) {
        ModalBottomSheet(
            onDismissRequest = { showLibraryManager = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            LibraryManagerSheet(
                libraries = uiState.libraries,
                activeLibraryId = uiState.activeLibraryId,
                bookCounts = uiState.bookCounts,
                onSelectLibrary = { library ->
                    viewModel.switchLibrary(library)
                    showLibraryManager = false
                },
                onDeleteLibrary = { library ->
                    viewModel.deleteLibrary(library)
                },
                onAddLibrary = {
                    viewModel.addLibrary()
                    showLibraryManager = false
                }
            )
        }
    }

    if (showGenreBrowse) {
        ModalBottomSheet(
            onDismissRequest = { showGenreBrowse = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            GenreBrowseSheet(
                books = uiState.allBooks,
                selectedGenre = uiState.selectedGenre,
                onSelectGenre = { genre ->
                    viewModel.setGenre(genre)
                    showGenreBrowse = false
                }
            )
        }
    }
}
