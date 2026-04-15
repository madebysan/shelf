package com.madebysan.shelf.service.playback

import com.madebysan.shelf.data.local.entity.BookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Shared playback state observable from any screen (Library, Player, etc.)
data class PlaybackState(
    val currentBook: BookEntity? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0
)

@Singleton
class PlaybackStateManager @Inject constructor() {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** When true, PlaybackService skips saving position (discover mode) */
    @Volatile
    var skipPositionSave: Boolean = false

    fun updateBook(book: BookEntity) {
        _state.value = _state.value.copy(currentBook = book)
    }

    fun updatePlayback(isPlaying: Boolean, position: Long, duration: Long) {
        _state.value = _state.value.copy(
            isPlaying = isPlaying,
            currentPosition = position,
            duration = duration
        )
    }

    fun updateIsPlaying(isPlaying: Boolean) {
        _state.value = _state.value.copy(isPlaying = isPlaying)
    }

    fun clear() {
        _state.value = PlaybackState()
    }
}
