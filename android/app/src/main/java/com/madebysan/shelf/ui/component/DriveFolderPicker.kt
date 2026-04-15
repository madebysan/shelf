package com.madebysan.shelf.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.madebysan.shelf.data.remote.dto.DriveFileDto
import com.madebysan.shelf.service.drive.DriveFileManager
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DriveFileManagerEntryPoint {
    fun driveFileManager(): DriveFileManager
}

data class FolderBreadcrumb(
    val id: String,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveFolderPicker(
    onFolderSelected: (DriveFileDto) -> Unit
) {
    val context = LocalContext.current
    val driveFileManager = remember {
        EntryPointAccessors.fromApplication(context, DriveFileManagerEntryPoint::class.java)
            .driveFileManager()
    }

    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<DriveFileDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val breadcrumbs = remember { mutableStateListOf(FolderBreadcrumb("root", "My Drive")) }

    fun loadFolders(parentId: String) {
        scope.launch {
            isLoading = true
            error = null
            try {
                folders = driveFileManager.listFolders(parentId)
                isLoading = false
            } catch (e: Exception) {
                error = e.message ?: "Failed to load folders"
                isLoading = false
            }
        }
    }

    LaunchedEffect(breadcrumbs.size) {
        loadFolders(breadcrumbs.last().id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(breadcrumbs.last().name) },
                navigationIcon = {
                    if (breadcrumbs.size > 1) {
                        IconButton(onClick = {
                            breadcrumbs.removeLast()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (breadcrumbs.size > 1) {
                        TextButton(onClick = {
                            val current = breadcrumbs.last()
                            onFolderSelected(
                                DriveFileDto(
                                    id = current.id,
                                    name = current.name,
                                    mimeType = "application/vnd.google-apps.folder"
                                )
                            )
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Text("Select")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            folders.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No subfolders", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    items(folders) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = {
                                Icon(Icons.Filled.Folder, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                breadcrumbs.add(FolderBreadcrumb(folder.id, folder.name))
                            }
                        )
                    }
                }
            }
        }
    }
}
