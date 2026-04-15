package com.madebysan.shelf.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madebysan.shelf.data.local.dao.BookDao
import com.madebysan.shelf.data.local.dao.LibraryDao
import com.madebysan.shelf.data.local.entity.BookEntity
import com.madebysan.shelf.data.local.entity.LibraryEntity
import com.madebysan.shelf.data.preferences.UserPreferences
import com.madebysan.shelf.data.remote.dto.DriveFileDto
import com.madebysan.shelf.data.repository.BookRepository
import com.madebysan.shelf.domain.usecase.FetchCoverArtUseCase
import com.madebysan.shelf.domain.usecase.ScanLibraryUseCase
import com.madebysan.shelf.service.download.DownloadManager as BookDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BookFilter(val label: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    NOT_STARTED("Not Started"),
    COMPLETED("Completed"),
    RECENTLY_ADDED("Recently Added"),
    STARRED("Starred"),
    DOWNLOADED("Downloaded"),
    HIDDEN("Hidden")
}

enum class BookSort(val label: String) {
    TITLE("Title"),
    RECENTLY_PLAYED("Recently Played"),
    AUTHOR("Author"),
    RATING("Rating"),
    SHORTEST("Shortest"),
    LONGEST("Longest"),
    LARGEST("Largest"),
    SMALLEST("Smallest")
}

data class LibraryUiState(
    val isPickingFolder: Boolean = true,
    val libraryFolderName: String? = null,
    val allBooks: List<BookEntity> = emptyList(),
    val books: List<BookEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val isFetchingCovers: Boolean = false,
    val scanStatus: String? = null,
    val isGrid: Boolean = true,
    val error: String? = null,
    val filter: BookFilter = BookFilter.ALL,
    val sort: BookSort = BookSort.TITLE,
    val searchQuery: String = "",
    val selectedGenre: String? = null,
    val libraries: List<LibraryEntity> = emptyList(),
    val activeLibraryId: Long? = null,
    val bookCounts: Map<Long, Int> = emptyMap()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val scanLibraryUseCase: ScanLibraryUseCase,
    private val fetchCoverArtUseCase: FetchCoverArtUseCase,
    private val bookRepository: BookRepository,
    private val userPreferences: UserPreferences,
    private val downloadManager: BookDownloadManager,
    private val libraryDao: LibraryDao,
    private val bookDao: BookDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var currentLibraryId: Long? = null

    init {
        viewModelScope.launch {
            val folderId = userPreferences.libraryFolderId.first()
            val folderName = userPreferences.libraryFolderName.first()
            if (folderId != null && folderName != null) {
                _uiState.value = LibraryUiState(
                    isPickingFolder = false,
                    libraryFolderName = folderName,
                    isScanning = true,
                    scanStatus = "Scanning library..."
                )
                scanAndLoad(folderId, folderName)
            }
        }
        // Observe custom library name changes (from Settings)
        viewModelScope.launch {
            userPreferences.libraryCustomName.collect { customName ->
                if (customName != null) {
                    _uiState.value = _uiState.value.copy(libraryFolderName = customName)
                } else {
                    val folderName = userPreferences.libraryFolderName.first()
                    _uiState.value = _uiState.value.copy(libraryFolderName = folderName)
                }
            }
        }

        // Observe all libraries for the library manager
        viewModelScope.launch {
            libraryDao.getAllLibraries().collect { libraries ->
                val counts = mutableMapOf<Long, Int>()
                for (lib in libraries) {
                    counts[lib.id] = bookDao.getBookCount(lib.id)
                }
                _uiState.value = _uiState.value.copy(
                    libraries = libraries,
                    activeLibraryId = currentLibraryId,
                    bookCounts = counts
                )
            }
        }
    }

    fun selectFolder(folder: DriveFileDto) {
        viewModelScope.launch {
            userPreferences.saveLibraryFolder(folder.id, folder.name)
            _uiState.value = LibraryUiState(
                isPickingFolder = false,
                libraryFolderName = folder.name,
                isScanning = true,
                scanStatus = "Scanning library..."
            )
            scanAndLoad(folder.id, folder.name)
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val folderId = userPreferences.libraryFolderId.first() ?: return@launch
            val folderName = userPreferences.libraryFolderName.first() ?: return@launch
            _uiState.value = _uiState.value.copy(isScanning = true, scanStatus = "Syncing...")
            scanAndLoad(folderId, folderName)
        }
    }

    fun toggleViewMode() {
        _uiState.value = _uiState.value.copy(isGrid = !_uiState.value.isGrid)
    }

    fun setFilter(filter: BookFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyFilterSortSearch()
    }

    fun setSort(sort: BookSort) {
        _uiState.value = _uiState.value.copy(sort = sort)
        applyFilterSortSearch()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilterSortSearch()
    }

    fun setGenre(genre: String?) {
        _uiState.value = _uiState.value.copy(selectedGenre = genre)
        applyFilterSortSearch()
    }

    fun toggleStarred(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.updateStarred(book.id, !book.isStarred)
        }
    }

    fun toggleHidden(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.updateHidden(book.id, !book.isHidden)
        }
    }

    fun toggleCompleted(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.updateCompleted(book.id, !book.isCompleted)
        }
    }

    fun downloadBook(book: BookEntity) {
        downloadManager.download(book.id, book.driveFileId, book.fileName)
    }

    fun removeDownload(book: BookEntity) {
        downloadManager.deleteDownload(book.id, book.fileName)
    }

    // Library management
    fun switchLibrary(library: LibraryEntity) {
        viewModelScope.launch {
            userPreferences.saveLibraryFolder(library.driveFolderId, library.driveFolderName)
            userPreferences.setLibraryCustomName(null)
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                scanStatus = "Switching library...",
                libraryFolderName = library.name
            )
            scanAndLoad(library.driveFolderId, library.driveFolderName)
        }
    }

    fun deleteLibrary(library: LibraryEntity) {
        viewModelScope.launch {
            libraryDao.delete(library.id) // CASCADE deletes books
        }
    }

    fun addLibrary() {
        _uiState.value = _uiState.value.copy(isPickingFolder = true)
    }

    fun setRating(bookId: Long, rating: Int) {
        viewModelScope.launch {
            bookRepository.updateRating(bookId, rating)
        }
    }

    private suspend fun scanAndLoad(folderId: String, folderName: String) {
        try {
            val result = scanLibraryUseCase.execute(folderId, folderName)
            currentLibraryId = result.libraryId

            // Launch cover art fetch in background
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isFetchingCovers = true)
                try {
                    fetchCoverArtUseCase.execute(result.libraryId)
                } catch (_: Exception) {
                    // Cover art is best-effort, don't fail the UI
                }
                _uiState.value = _uiState.value.copy(isFetchingCovers = false)
            }

            // Observe ALL books (including hidden) from Room so filters work
            bookRepository.getAllBooksByLibrary(result.libraryId).collect { books ->
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    isLoading = false,
                    scanStatus = null,
                    allBooks = books,
                    activeLibraryId = result.libraryId,
                    error = null
                )
                applyFilterSortSearch()
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                isLoading = false,
                scanStatus = null,
                error = e.message ?: "Failed to scan library"
            )
        }
    }

    private fun applyFilterSortSearch() {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 24 * 60 * 60 * 1000L

        // Filter
        var filtered = when (state.filter) {
            BookFilter.ALL -> state.allBooks.filter { !it.isHidden }
            BookFilter.IN_PROGRESS -> state.allBooks.filter {
                !it.isHidden && it.playbackPosition > 0 && !it.isCompleted
            }
            BookFilter.NOT_STARTED -> state.allBooks.filter {
                !it.isHidden && it.playbackPosition == 0L && !it.isCompleted
            }
            BookFilter.COMPLETED -> state.allBooks.filter { !it.isHidden && it.isCompleted }
            BookFilter.RECENTLY_ADDED -> state.allBooks.filter {
                !it.isHidden && it.addedDate >= sevenDaysAgo
            }
            BookFilter.STARRED -> state.allBooks.filter { !it.isHidden && it.isStarred }
            BookFilter.DOWNLOADED -> state.allBooks.filter { !it.isHidden && it.isDownloaded }
            BookFilter.HIDDEN -> state.allBooks.filter { it.isHidden }
        }

        // Genre filter
        if (state.selectedGenre != null) {
            filtered = filtered.filter { (it.genre ?: "Unknown") == state.selectedGenre }
        }

        // Search
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.lowercase()
            filtered = filtered.filter { book ->
                book.title.lowercase().contains(q) ||
                        (book.author?.lowercase()?.contains(q) == true) ||
                        (book.genre?.lowercase()?.contains(q) == true)
            }
        }

        // Sort
        val sorted = when (state.sort) {
            BookSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            BookSort.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastPlayedDate ?: 0L }
            BookSort.AUTHOR -> filtered.sortedBy { it.author?.lowercase() ?: "" }
            BookSort.RATING -> filtered.sortedByDescending { it.rating }
            BookSort.SHORTEST -> filtered.sortedBy { it.duration }
            BookSort.LONGEST -> filtered.sortedByDescending { it.duration }
            BookSort.LARGEST -> filtered.sortedByDescending { it.fileSize }
            BookSort.SMALLEST -> filtered.sortedBy { it.fileSize }
        }

        _uiState.value = _uiState.value.copy(books = sorted)
    }
}
