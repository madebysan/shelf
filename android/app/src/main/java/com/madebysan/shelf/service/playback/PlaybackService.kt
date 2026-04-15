package com.madebysan.shelf.service.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.madebysan.shelf.MainActivity
import com.madebysan.shelf.data.local.dao.BookDao
import com.madebysan.shelf.service.auth.GoogleAuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var authManager: GoogleAuthManager
    @Inject lateinit var bookDao: BookDao
    @Inject lateinit var playbackStateManager: PlaybackStateManager


    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Track which book is playing so we can save position
    var currentBookId: Long? = null
        private set

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // OkHttp data source that injects the access token for Drive streaming
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = runBlocking { authManager.getAccessToken() }
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                val response = chain.proceed(request)
                // Retry on 401
                if (response.code == 401) {
                    response.close()
                    val freshToken = runBlocking { authManager.invalidateAndRefreshToken() }
                    if (freshToken != null) {
                        val retry = chain.request().newBuilder()
                            .header("Authorization", "Bearer $freshToken")
                            .build()
                        return@addInterceptor chain.proceed(retry)
                    }
                }
                response
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Track which book is playing and push state updates
        player!!.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentBookId = mediaItem?.mediaId?.toLongOrNull()
                // Load book data for the state manager
                currentBookId?.let { bookId ->
                    serviceScope.launch {
                        val book = bookDao.getBookById(bookId)
                        if (book != null) {
                            playbackStateManager.updateBook(book)
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackStateManager.updateIsPlaying(isPlaying)
            }
        })

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .build()

        // Periodically save playback position and update shared state
        serviceScope.launch {
            while (isActive) {
                delay(5000)
                savePlaybackPosition()
                // Push position to shared state
                val p = player
                if (p != null && p.playbackState != Player.STATE_IDLE) {
                    playbackStateManager.updatePlayback(
                        isPlaying = p.isPlaying,
                        position = p.currentPosition.coerceAtLeast(0),
                        duration = p.duration.coerceAtLeast(0)
                    )
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        // Save final position
        runBlocking { savePlaybackPosition() }
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private suspend fun savePlaybackPosition() {
        if (playbackStateManager.skipPositionSave) return
        val p = player ?: return
        val bookId = currentBookId ?: return
        if (p.playbackState == Player.STATE_IDLE) return

        val position = p.currentPosition
        val duration = p.duration.takeIf { it > 0 } ?: return

        bookDao.updatePlaybackPosition(
            bookId = bookId,
            position = position,
            lastPlayed = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        // Mark complete if within last 30 seconds
        if (duration - position < 30_000) {
            bookDao.updateCompleted(bookId, true)
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"

        fun buildMediaItem(
            bookId: Long,
            driveFileId: String,
            title: String,
            author: String?,
            coverArtUrl: String? = null,
            localFilePath: String? = null
        ): MediaItem {
            // Prefer local file if available
            val uri = if (localFilePath != null) {
                localFilePath
            } else {
                "https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media"
            }
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(author)
            if (coverArtUrl != null) {
                metadataBuilder.setArtworkUri(Uri.parse(coverArtUrl))
            }
            return MediaItem.Builder()
                .setMediaId(bookId.toString())
                .setUri(uri)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }
    }
}
