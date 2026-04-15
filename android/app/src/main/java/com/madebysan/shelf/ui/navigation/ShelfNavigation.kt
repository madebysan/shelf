package com.madebysan.shelf.ui.navigation

import android.content.ComponentName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.common.util.concurrent.MoreExecutors
import com.madebysan.shelf.service.playback.PlaybackService
import com.madebysan.shelf.service.playback.PlaybackStateManager
import com.madebysan.shelf.ui.component.MiniPlayer
import com.madebysan.shelf.ui.screen.auth.AuthScreen
import com.madebysan.shelf.ui.screen.library.LibraryScreen
import com.madebysan.shelf.ui.screen.player.PlayerScreen
import com.madebysan.shelf.ui.screen.settings.SettingsScreen

object Routes {
    const val AUTH = "auth"
    const val LIBRARY = "library"
    const val PLAYER = "player/{bookId}?discover={discover}"
    const val SETTINGS = "settings"

    fun player(bookId: Long, discover: Boolean = false) = "player/$bookId?discover=$discover"
}

@Composable
fun ShelfNavigation(playbackStateManager: PlaybackStateManager) {
    val navController = rememberNavController()
    val playbackState by playbackStateManager.state.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // MiniPlayer should show on the library screen when a book is/was playing
    val showMiniPlayer = playbackState.currentBook != null && currentRoute == Routes.LIBRARY

    // Get a MediaController reference for MiniPlayer controls
    val context = LocalContext.current
    val mediaControllerRef = remember { arrayOfNulls<MediaController>(1) }

    // Connect to the service lazily when we have a playing book
    if (playbackState.currentBook != null && mediaControllerRef[0] == null) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                mediaControllerRef[0] = future.get()
            } catch (_: Exception) { }
        }, MoreExecutors.directExecutor())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Routes.AUTH
            ) {
                composable(Routes.AUTH) {
                    AuthScreen(
                        onSignedIn = {
                            navController.navigate(Routes.LIBRARY) {
                                popUpTo(Routes.AUTH) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onBookClick = { bookId ->
                            navController.navigate(Routes.player(bookId))
                        },
                        onDiscoverClick = { bookId ->
                            navController.navigate(Routes.player(bookId, discover = true))
                        },
                        onSettingsClick = {
                            navController.navigate(Routes.SETTINGS)
                        }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onSignedOut = {
                            navController.navigate(Routes.AUTH) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
                composable(
                    route = Routes.PLAYER,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.LongType },
                        navArgument("discover") {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) {
                    PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onDiscoverNext = { nextBookId ->
                            navController.navigate(Routes.player(nextBookId, discover = true)) {
                                popUpTo(Routes.LIBRARY)
                            }
                        }
                    )
                }
            }
        }

        // MiniPlayer at the bottom
        AnimatedVisibility(
            visible = showMiniPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val book = playbackState.currentBook
            if (book != null) {
                val progress = if (playbackState.duration > 0) {
                    playbackState.currentPosition.toFloat() / playbackState.duration.toFloat()
                } else 0f

                MiniPlayer(
                    book = book,
                    isPlaying = playbackState.isPlaying,
                    progress = progress,
                    onTap = {
                        navController.navigate(Routes.player(book.id))
                    },
                    onPlayPause = {
                        val ctrl = mediaControllerRef[0] ?: return@MiniPlayer
                        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
                    },
                    onSkipForward = {
                        val ctrl = mediaControllerRef[0] ?: return@MiniPlayer
                        ctrl.seekTo((ctrl.currentPosition + 30_000).coerceAtMost(ctrl.duration))
                    }
                )
            }
        }
    }
}
