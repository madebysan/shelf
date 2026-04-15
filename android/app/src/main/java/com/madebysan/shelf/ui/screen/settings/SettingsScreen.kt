package com.madebysan.shelf.ui.screen.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonData = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                if (jsonData != null) {
                    viewModel.importProgress(jsonData)
                }
            } catch (e: Exception) {
                // Error handled in viewModel
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── ACCOUNT ──
            SectionHeader("Account")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = uiState.userName ?: "Signed in",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (uiState.userEmail != null) {
                        Text(
                            text = uiState.userEmail!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsRow(
                text = "Sign Out",
                textColor = MaterialTheme.colorScheme.error,
                onClick = { viewModel.showSignOutConfirm(true) }
            )

            SectionDivider()

            // ── APPEARANCE ──
            SectionHeader("Appearance")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val options = listOf("System", "Light", "Dark")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            selected = uiState.themeMode == index,
                            onClick = { viewModel.setThemeMode(index) }
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            SectionDivider()

            // ── LIBRARY ──
            SectionHeader("Library")

            SettingsRow(
                text = "Name",
                value = uiState.libraryName,
                onClick = {
                    renameText = uiState.libraryCustomName ?: ""
                    showRenameDialog = true
                }
            )

            if (uiState.isRefreshingCovers) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Refreshing covers...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                SettingsRow(
                    text = "Refresh Covers & Metadata",
                    onClick = { viewModel.showRefreshCoversConfirm(true) }
                )
            }

            SectionDivider()

            // ── BACKUP ──
            SectionHeader("Backup")

            SettingsRow(
                text = "Export Progress",
                icon = { Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = {
                    val json = viewModel.exportProgress()
                    if (json != null) {
                        try {
                            val file = File(context.cacheDir, "audiobook-progress.json")
                            file.writeText(json)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Progress"))
                        } catch (_: Exception) { }
                    }
                }
            )

            SettingsRow(
                text = "Import Progress",
                icon = { Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = { importLauncher.launch("application/json") }
            )

            Text(
                text = "Export saves your listening positions and bookmarks as a JSON file. Import restores them by matching file paths.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SectionDivider()

            // ── DOWNLOADS ──
            SectionHeader("Downloads")

            SettingsRow(
                text = "Download All",
                value = "${uiState.nonDownloadedCount} books",
                onClick = if (uiState.nonDownloadedCount > 0) {
                    { viewModel.showDownloadAllConfirm(true) }
                } else null
            )

            SectionDivider()

            // ── STORAGE ──
            SectionHeader("Storage")

            SettingsRow(
                text = "Downloaded Files",
                value = formatBytes(uiState.downloadedSize)
            )

            if (uiState.downloadedCount > 0) {
                SettingsRow(
                    text = "Clear All Downloads",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { viewModel.showClearDownloadsConfirm(true) }
                )
            }

            SettingsRow(
                text = "Cover Art Cache",
                value = formatBytes(uiState.coverCacheSize)
            )

            SettingsRow(
                text = "Clear Cover Art Cache",
                textColor = MaterialTheme.colorScheme.error,
                onClick = { viewModel.showClearCacheConfirm(true) }
            )

            SectionDivider()

            // ── ABOUT ──
            SectionHeader("About")

            SettingsRow(
                text = "Version",
                value = viewModel.getAppVersion()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Dialogs ──

    if (uiState.showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showSignOutConfirm(false) },
            title = { Text("Sign Out?") },
            text = { Text("You'll need to sign in again to access your audiobooks.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.showSignOutConfirm(false)
                    viewModel.signOut(onSignedOut)
                }) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showSignOutConfirm(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showRefreshCoversConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showRefreshCoversConfirm(false) },
            title = { Text("Refresh Metadata?") },
            text = { Text("This will re-fetch cover art for all books. It may take a while.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.showRefreshCoversConfirm(false)
                    viewModel.refreshCovers()
                }) {
                    Text("Refresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showRefreshCoversConfirm(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearCacheConfirm(false) },
            title = { Text("Clear Cover Art Cache?") },
            text = { Text("Cover art will be re-fetched on the next refresh. Listening progress is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.showClearCacheConfirm(false)
                    viewModel.clearCoverCache()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearCacheConfirm(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showDownloadAllConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDownloadAllConfirm(false) },
            title = { Text("Download All?") },
            text = { Text("Download ${uiState.nonDownloadedCount} books for offline listening.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.showDownloadAllConfirm(false)
                    viewModel.downloadAll()
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDownloadAllConfirm(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showClearDownloadsConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearDownloadsConfirm(false) },
            title = { Text("Clear All Downloads?") },
            text = { Text("Downloaded audiobook files will be deleted. Listening progress is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.showClearDownloadsConfirm(false)
                    viewModel.clearAllDownloads()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearDownloadsConfirm(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Library") },
            text = {
                Column {
                    Text(
                        "Leave empty to use the Drive folder name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        placeholder = { Text(uiState.libraryName) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameLibrary(renameText)
                    showRenameDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.importResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportResult() },
            title = { Text("Import Complete") },
            text = { Text(uiState.importResult!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearImportResult() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SettingsRow(
    text: String,
    value: String? = null,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}
