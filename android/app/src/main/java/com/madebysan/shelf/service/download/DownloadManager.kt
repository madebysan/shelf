package com.madebysan.shelf.service.download

import android.content.Context
import com.madebysan.shelf.data.local.dao.BookDao
import com.madebysan.shelf.service.auth.GoogleAuthManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val bookId: Long,
    val driveFileId: String,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
) {
    val fraction: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
    val isComplete: Boolean
        get() = totalBytes > 0 && bytesDownloaded >= totalBytes
}

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: GoogleAuthManager,
    private val bookDao: BookDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<Long, Job>()

    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, DownloadProgress>> = _activeDownloads.asStateFlow()

    private val audiobooksDir: File
        get() {
            val dir = File(context.filesDir, "Audiobooks")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getLocalFile(fileName: String): File = File(audiobooksDir, fileName)

    fun localFileExists(fileName: String): Boolean = getLocalFile(fileName).exists()

    fun download(bookId: Long, driveFileId: String, fileName: String) {
        if (activeJobs.containsKey(bookId)) return // already downloading

        val job = scope.launch {
            try {
                _activeDownloads.value = _activeDownloads.value + (bookId to DownloadProgress(
                    bookId = bookId,
                    driveFileId = driveFileId
                ))

                val token = authManager.getAccessToken()
                    ?: throw Exception("Not authenticated")

                val url = "https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media"
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorMsg = when (response.code) {
                        401 -> "Authentication expired. Please sign out and back in."
                        403 -> "Download quota exceeded. Try again in a few hours."
                        404 -> "File not found on Google Drive."
                        else -> "Download failed (HTTP ${response.code})."
                    }
                    updateProgress(bookId) { it.copy(error = errorMsg) }
                    return@launch
                }

                val body = response.body ?: throw Exception("Empty response")
                val totalBytes = body.contentLength()
                updateProgress(bookId) { it.copy(totalBytes = totalBytes) }

                // Download to temp file, then move atomically
                val tempFile = File(audiobooksDir, "$fileName.tmp")
                val finalFile = getLocalFile(fileName)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            updateProgress(bookId) { it.copy(bytesDownloaded = totalRead) }
                        }
                    }
                }

                // Validate file size
                if (tempFile.length() < 1024) {
                    tempFile.delete()
                    updateProgress(bookId) {
                        it.copy(error = "Downloaded file is too small — likely an API error.")
                    }
                    return@launch
                }

                // Move to final location
                tempFile.renameTo(finalFile)

                // Mark as downloaded in DB
                bookDao.updateDownloaded(bookId, true)

                // Remove from active downloads
                _activeDownloads.value = _activeDownloads.value - bookId

            } catch (e: Exception) {
                updateProgress(bookId) {
                    it.copy(error = e.message ?: "Download failed")
                }
            } finally {
                activeJobs.remove(bookId)
            }
        }

        activeJobs[bookId] = job
    }

    fun cancel(bookId: Long) {
        activeJobs[bookId]?.cancel()
        activeJobs.remove(bookId)
        _activeDownloads.value = _activeDownloads.value - bookId
        // Clean up temp file
        scope.launch {
            val book = bookDao.getBookById(bookId) ?: return@launch
            val tempFile = File(audiobooksDir, "${book.fileName}.tmp")
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun deleteDownload(bookId: Long, fileName: String) {
        scope.launch {
            val file = getLocalFile(fileName)
            if (file.exists()) file.delete()
            bookDao.updateDownloaded(bookId, false)
        }
    }

    fun deleteAllDownloads(books: List<Pair<Long, String>>) {
        scope.launch {
            for ((bookId, fileName) in books) {
                val file = getLocalFile(fileName)
                if (file.exists()) file.delete()
                bookDao.updateDownloaded(bookId, false)
            }
        }
    }

    fun isDownloading(bookId: Long): Boolean = activeJobs.containsKey(bookId)

    fun totalDownloadedSize(): Long {
        val dir = audiobooksDir
        if (!dir.exists()) return 0
        return dir.listFiles()
            ?.filter { !it.name.endsWith(".tmp") }
            ?.sumOf { it.length() } ?: 0
    }

    private fun updateProgress(bookId: Long, update: (DownloadProgress) -> DownloadProgress) {
        val current = _activeDownloads.value[bookId] ?: return
        _activeDownloads.value = _activeDownloads.value + (bookId to update(current))
    }
}
