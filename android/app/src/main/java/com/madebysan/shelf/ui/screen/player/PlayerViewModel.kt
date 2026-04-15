package com.madebysan.shelf.ui.screen.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.madebysan.shelf.data.local.entity.BookEntity
import com.madebysan.shelf.data.local.entity.BookmarkEntity
import com.madebysan.shelf.service.download.DownloadProgress
import com.madebysan.shelf.ui.component.ChapterInfo
import com.madebysan.shelf.data.repository.BookRepository
import com.madebysan.shelf.data.repository.BookmarkRepository
import com.madebysan.shelf.service.download.DownloadManager as BookDownloadManager
import com.madebysan.shelf.service.playback.PlaybackService
import com.madebysan.shelf.service.playback.PlaybackStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val book: BookEntity? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val isLoading: Boolean = true,
    val error: String? = null,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemaining: Long = 0, // ms remaining
    val sleepEndOfChapter: Boolean = false,
    val downloadProgress: DownloadProgress? = null,
    val chapters: List<ChapterInfo> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentChapterName: String? = null,
    val isVideo: Boolean = false,
    val isDiscoverMode: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val downloadManager: BookDownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val bookId: Long = savedStateHandle.get<Long>("bookId") ?: -1L
    private val isDiscover: Boolean = savedStateHandle.get<Boolean>("discover") ?: false

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var mediaController: MediaController? = null
    private var sleepTimerJob: Job? = null
    private var discoverSeeked = false

    // Expose the player for video rendering
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    init {
        viewModelScope.launch {
            val book = bookRepository.getBookById(bookId)
            if (book == null) {
                _uiState.value = PlayerUiState(isLoading = false, error = "Book not found")
                return@launch
            }
            _uiState.value = PlayerUiState(
                book = book,
                isLoading = true,
                playbackSpeed = book.playbackSpeed,
                isVideo = book.isVideo,
                isDiscoverMode = isDiscover
            )
            if (isDiscover) {
                playbackStateManager.skipPositionSave = true
            }
            playbackStateManager.updateBook(book)
            connectToService(book)
        }

        // Observe bookmarks
        viewModelScope.launch {
            bookmarkRepository.getBookmarksByBook(bookId).collect { bookmarks ->
                _uiState.value = _uiState.value.copy(bookmarks = bookmarks)
            }
        }

        // Observe download progress
        viewModelScope.launch {
            downloadManager.activeDownloads.collect { downloads ->
                _uiState.value = _uiState.value.copy(downloadProgress = downloads[bookId])
            }
        }
    }

    private fun connectToService(book: BookEntity) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            val controller = controllerFuture.get()
            mediaController = controller
            _player.value = controller

            // Use local file if downloaded, otherwise stream from Drive
            val localPath = if (book.isDownloaded && downloadManager.localFileExists(book.fileName)) {
                downloadManager.getLocalFile(book.fileName).absolutePath
            } else null

            val mediaItem = PlaybackService.buildMediaItem(
                bookId = book.id,
                driveFileId = book.driveFileId,
                title = book.title,
                author = book.author,
                coverArtUrl = book.coverArtUrl,
                localFilePath = localPath
            )

            controller.setMediaItem(mediaItem)
            controller.prepare()

            if (!isDiscover && book.playbackPosition > 0) {
                controller.seekTo(book.playbackPosition)
            }
            if (book.playbackSpeed != 1.0f) {
                controller.setPlaybackSpeed(book.playbackSpeed)
            }

            controller.play()

            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = playbackState == Player.STATE_BUFFERING
                    )
                    if (playbackState == Player.STATE_READY) {
                        val duration = controller.duration.coerceAtLeast(0)
                        _uiState.value = _uiState.value.copy(duration = duration)
                        extractChapters(controller)
                        // In discover mode, seek to a random position (10-80%)
                        if (isDiscover && !discoverSeeked && duration > 0) {
                            discoverSeeked = true
                            val minPos = (duration * 0.1).toLong()
                            val maxPos = (duration * 0.8).toLong()
                            val randomPos = (minPos..maxPos).random()
                            controller.seekTo(randomPos)
                        }
                    }
                }
                override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                    // Some formats provide chapter info via metadata updates
                    extractChapters(controller)
                }
            })

            // Poll position and push to shared state
            viewModelScope.launch {
                while (isActive) {
                    val ctrl = mediaController ?: break
                    val pos = ctrl.currentPosition.coerceAtLeast(0)
                    val dur = ctrl.duration.coerceAtLeast(0)
                    val playing = ctrl.isPlaying
                    val chapterUpdate = updateCurrentChapter(pos)
                    _uiState.value = _uiState.value.copy(
                        currentPosition = pos,
                        duration = dur,
                        isPlaying = playing,
                        currentChapterIndex = chapterUpdate.first,
                        currentChapterName = chapterUpdate.second
                    )
                    playbackStateManager.updatePlayback(playing, pos, dur)
                    delay(500)
                }
            }
        }, MoreExecutors.directExecutor())
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun skipForward() {
        val controller = mediaController ?: return
        val amount = if (_uiState.value.isVideo) 10_000L else 30_000L
        controller.seekTo((controller.currentPosition + amount).coerceAtMost(controller.duration))
    }

    fun skipBack() {
        val controller = mediaController ?: return
        val amount = if (_uiState.value.isVideo) 10_000L else 30_000L
        controller.seekTo((controller.currentPosition - amount).coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        viewModelScope.launch {
            val book = _uiState.value.book ?: return@launch
            bookRepository.updateBook(book.copy(playbackSpeed = speed))
        }
    }

    // Bookmarks
    fun addBookmark(name: String, note: String?) {
        val position = mediaController?.currentPosition ?: return
        viewModelScope.launch {
            bookmarkRepository.addBookmark(bookId, position, name, note)
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmark.id)
        }
    }

    fun jumpToBookmark(bookmark: BookmarkEntity) {
        mediaController?.seekTo(bookmark.timestamp)
    }

    // Sleep timer
    // minutes: 0=off, -1=end of chapter, >0=timed
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes == 0) {
            _uiState.value = _uiState.value.copy(
                sleepTimerMinutes = 0, sleepTimerRemaining = 0, sleepEndOfChapter = false
            )
            return
        }

        if (minutes == -1) {
            // End of chapter mode
            _uiState.value = _uiState.value.copy(
                sleepTimerMinutes = -1,
                sleepTimerRemaining = 0,
                sleepEndOfChapter = true
            )
            val startChapterIndex = _uiState.value.currentChapterIndex
            sleepTimerJob = viewModelScope.launch {
                // Watch for chapter change
                while (isActive) {
                    delay(500)
                    if (_uiState.value.currentChapterIndex != startChapterIndex) {
                        mediaController?.pause()
                        _uiState.value = _uiState.value.copy(
                            sleepTimerMinutes = 0, sleepTimerRemaining = 0, sleepEndOfChapter = false
                        )
                        break
                    }
                }
            }
            return
        }

        _uiState.value = _uiState.value.copy(
            sleepTimerMinutes = minutes,
            sleepTimerRemaining = minutes * 60_000L,
            sleepEndOfChapter = false
        )

        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60_000L
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1000
                _uiState.value = _uiState.value.copy(sleepTimerRemaining = remaining.coerceAtLeast(0))
            }
            mediaController?.pause()
            _uiState.value = _uiState.value.copy(sleepTimerMinutes = 0, sleepTimerRemaining = 0)
        }
    }

    // Chapters
    private fun extractChapters(controller: MediaController) {
        if (_uiState.value.chapters.isNotEmpty()) return // already extracted

        val timeline = controller.currentTimeline
        if (timeline.windowCount <= 1) {
            // Single window — no chapter markers from timeline
            // For M4B/M4A, ExoPlayer doesn't always split into windows
            // Chapters will remain empty for now; future: parse metadata directly
            return
        }

        val chapters = mutableListOf<ChapterInfo>()
        val window = androidx.media3.common.Timeline.Window()
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window)
            val title = window.mediaItem.mediaMetadata.title?.toString()
                ?: "Chapter ${i + 1}"
            val startMs = window.defaultPositionMs
            val durationMs = window.durationMs.coerceAtLeast(0)
            chapters.add(ChapterInfo(title, startMs, durationMs))
        }
        if (chapters.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(chapters = chapters)
        }
    }

    private fun updateCurrentChapter(positionMs: Long): Pair<Int, String?> {
        val chapters = _uiState.value.chapters
        if (chapters.isEmpty()) return Pair(0, null)

        val index = chapters.indexOfLast { it.startTimeMs <= positionMs }
            .coerceAtLeast(0)
        return Pair(index, chapters[index].title)
    }

    fun goToChapter(chapter: ChapterInfo) {
        mediaController?.seekTo(chapter.startTimeMs)
    }

    fun previousChapter() {
        val chapters = _uiState.value.chapters
        if (chapters.isEmpty()) return
        val currentIndex = _uiState.value.currentChapterIndex
        val currentPos = mediaController?.currentPosition ?: return
        val currentChapter = chapters.getOrNull(currentIndex) ?: return

        if (currentPos - currentChapter.startTimeMs > 3000) {
            // More than 3s into chapter — restart current
            mediaController?.seekTo(currentChapter.startTimeMs)
        } else if (currentIndex > 0) {
            // Go to previous chapter
            mediaController?.seekTo(chapters[currentIndex - 1].startTimeMs)
        }
    }

    fun nextChapter() {
        val chapters = _uiState.value.chapters
        val nextIndex = _uiState.value.currentChapterIndex + 1
        if (nextIndex < chapters.size) {
            mediaController?.seekTo(chapters[nextIndex].startTimeMs)
        }
    }

    // Discover mode
    fun getRandomBookId(callback: (Long?) -> Unit) {
        viewModelScope.launch {
            val book = _uiState.value.book ?: return@launch callback(null)
            val allBooks = bookRepository.getAllBooksByLibraryOnce(book.libraryId)
            val eligible = allBooks.filter { !it.isHidden && it.id != book.id }
            callback(eligible.randomOrNull()?.id)
        }
    }

    fun exitDiscoverMode() {
        playbackStateManager.skipPositionSave = false
        mediaController?.stop()
    }

    // Downloads
    fun downloadBook() {
        val book = _uiState.value.book ?: return
        downloadManager.download(book.id, book.driveFileId, book.fileName)
    }

    fun cancelDownload() {
        downloadManager.cancel(bookId)
    }

    fun removeDownload() {
        val book = _uiState.value.book ?: return
        downloadManager.deleteDownload(book.id, book.fileName)
        _uiState.value = _uiState.value.copy(book = book.copy(isDownloaded = false))
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        if (isDiscover) {
            playbackStateManager.skipPositionSave = false
        }
        mediaController?.release()
        mediaController = null
        super.onCleared()
    }
}
