package com.madebysan.shelf.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madebysan.shelf.data.local.dao.BookDao
import com.madebysan.shelf.data.local.dao.BookmarkDao
import com.madebysan.shelf.data.local.dao.LibraryDao
import com.madebysan.shelf.data.local.entity.BookmarkEntity
import com.madebysan.shelf.data.preferences.UserPreferences
import com.madebysan.shelf.domain.usecase.FetchCoverArtUseCase
import com.madebysan.shelf.service.auth.GoogleAuthManager
import com.madebysan.shelf.service.download.DownloadManager as BookDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class SettingsUiState(
    val userName: String? = null,
    val userEmail: String? = null,
    val themeMode: Int = 0,
    val libraryName: String = "Library",
    val libraryCustomName: String? = null,
    val isRefreshingCovers: Boolean = false,
    val coverCacheSize: Long = 0,
    val downloadedSize: Long = 0,
    val downloadedCount: Int = 0,
    val nonDownloadedCount: Int = 0,
    val bookCount: Int = 0,
    val importResult: String? = null,
    val showSignOutConfirm: Boolean = false,
    val showClearCacheConfirm: Boolean = false,
    val showClearDownloadsConfirm: Boolean = false,
    val showDownloadAllConfirm: Boolean = false,
    val showRefreshCoversConfirm: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val userPreferences: UserPreferences,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val libraryDao: LibraryDao,
    private val fetchCoverArtUseCase: FetchCoverArtUseCase,
    private val bookDownloadManager: BookDownloadManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var libraryId: Long? = null

    init {
        // Observe user prefs
        viewModelScope.launch {
            combine(
                userPreferences.userName,
                userPreferences.userEmail,
                userPreferences.themeMode,
                userPreferences.libraryFolderName,
                userPreferences.libraryCustomName
            ) { name, email, theme, folderName, customName ->
                _uiState.value.copy(
                    userName = name,
                    userEmail = email,
                    themeMode = theme,
                    libraryName = customName ?: folderName ?: "Library",
                    libraryCustomName = customName
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // Resolve library ID and observe books
        viewModelScope.launch {
            val folderId = userPreferences.libraryFolderId.first() ?: return@launch
            val library = libraryDao.getLibraryByFolderId(folderId) ?: return@launch
            libraryId = library.id

            bookDao.getAllBooksByLibrary(library.id).collect { books ->
                _uiState.value = _uiState.value.copy(
                    coverCacheSize = books.sumOf { (it.coverArtData?.size ?: 0).toLong() },
                    downloadedSize = bookDownloadManager.totalDownloadedSize(),
                    downloadedCount = books.count { it.isDownloaded },
                    nonDownloadedCount = books.count { !it.isDownloaded && !it.isHidden },
                    bookCount = books.size
                )
            }
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun renameLibrary(name: String) {
        viewModelScope.launch {
            userPreferences.setLibraryCustomName(name.trim().ifBlank { null })
        }
    }

    fun refreshCovers() {
        val libId = libraryId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshingCovers = true)
            try {
                fetchCoverArtUseCase.execute(libId)
            } catch (_: Exception) { }
            _uiState.value = _uiState.value.copy(isRefreshingCovers = false)
        }
    }

    fun clearCoverCache() {
        val libId = libraryId ?: return
        viewModelScope.launch {
            bookDao.getAllBooksByLibrary(libId).first().forEach { book ->
                if (book.coverArtUrl != null || book.coverArtData != null) {
                    bookDao.updateCoverArt(book.id, null, null)
                }
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authManager.signOut()
            onSignedOut()
        }
    }

    fun exportProgress(): String? {
        val libId = libraryId ?: return null
        return kotlinx.coroutines.runBlocking {
            val books = bookDao.getAllBooksByLibrary(libId).first()
            val exportData = ProgressExportData(
                exportDate = java.time.Instant.now().toString(),
                version = "1.0",
                books = books.map { book ->
                    val bookmarks = bookmarkDao.getBookmarksByBookOnce(book.id)
                    BookProgressData(
                        filePath = book.filePath ?: book.fileName,
                        playbackPosition = book.playbackPosition,
                        lastPlayedDate = book.lastPlayedDate,
                        isCompleted = book.isCompleted,
                        bookmarks = bookmarks.map { bm ->
                            BookmarkData(
                                timestamp = bm.timestamp,
                                name = bm.name,
                                note = bm.note,
                                createdDate = bm.createdDate
                            )
                        }
                    )
                }
            )
            Json.encodeToString(exportData)
        }
    }

    fun importProgress(jsonData: String) {
        val libId = libraryId ?: return
        viewModelScope.launch {
            try {
                val data = Json.decodeFromString<ProgressExportData>(jsonData)
                var booksUpdated = 0
                var bookmarksCreated = 0
                var booksNotFound = 0
                val allBooks = bookDao.getAllBooksByLibrary(libId).first()

                for (entry in data.books) {
                    val book = allBooks.find { (it.filePath ?: it.fileName) == entry.filePath }
                    if (book == null) {
                        booksNotFound++
                        continue
                    }
                    bookDao.updatePlaybackPosition(
                        bookId = book.id,
                        position = entry.playbackPosition,
                        lastPlayed = entry.lastPlayedDate ?: System.currentTimeMillis(),
                        modified = System.currentTimeMillis()
                    )
                    if (entry.isCompleted) {
                        bookDao.updateCompleted(book.id, true)
                    }
                    booksUpdated++

                    val existingBookmarks = bookmarkDao.getBookmarksByBookOnce(book.id)
                    for (bmData in entry.bookmarks) {
                        if (existingBookmarks.none { it.timestamp == bmData.timestamp }) {
                            bookmarkDao.insert(
                                BookmarkEntity(
                                    bookId = book.id,
                                    timestamp = bmData.timestamp,
                                    name = bmData.name,
                                    note = bmData.note,
                                    createdDate = bmData.createdDate
                                )
                            )
                            bookmarksCreated++
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    importResult = "Updated $booksUpdated book(s). Imported $bookmarksCreated bookmark(s). Skipped $booksNotFound not in library."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(importResult = "Import failed: ${e.message}")
            }
        }
    }

    fun downloadAll() {
        val libId = libraryId ?: return
        viewModelScope.launch {
            val books = bookDao.getAllBooksByLibrary(libId).first()
                .filter { !it.isDownloaded && !it.isHidden }
            for (book in books) {
                bookDownloadManager.download(book.id, book.driveFileId, book.fileName)
            }
        }
    }

    fun clearAllDownloads() {
        val libId = libraryId ?: return
        viewModelScope.launch {
            val books = bookDao.getAllBooksByLibrary(libId).first()
                .filter { it.isDownloaded }
                .map { it.id to it.fileName }
            bookDownloadManager.deleteAllDownloads(books)
        }
    }

    fun showClearDownloadsConfirm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearDownloadsConfirm = show)
    }

    fun showDownloadAllConfirm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDownloadAllConfirm = show)
    }

    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    fun showSignOutConfirm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSignOutConfirm = show)
    }

    fun showClearCacheConfirm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearCacheConfirm = show)
    }

    fun showRefreshCoversConfirm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showRefreshCoversConfirm = show)
    }

    fun getAppVersion(): String {
        return try {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (_: Exception) { "Unknown" }
    }
}

@Serializable
data class ProgressExportData(
    val exportDate: String,
    val version: String,
    val books: List<BookProgressData>
)

@Serializable
data class BookProgressData(
    val filePath: String,
    val playbackPosition: Long,
    val lastPlayedDate: Long? = null,
    val isCompleted: Boolean = false,
    val bookmarks: List<BookmarkData> = emptyList()
)

@Serializable
data class BookmarkData(
    val timestamp: Long,
    val name: String,
    val note: String? = null,
    val createdDate: Long = System.currentTimeMillis()
)
