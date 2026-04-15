package com.madebysan.shelf.domain.usecase

import com.madebysan.shelf.data.local.entity.BookEntity
import com.madebysan.shelf.data.remote.dto.DriveFileDto
import com.madebysan.shelf.data.repository.BookRepository
import com.madebysan.shelf.data.repository.LibraryRepository
import com.madebysan.shelf.service.drive.DriveFileManager
import javax.inject.Inject
import javax.inject.Singleton

data class ScanResult(
    val libraryId: Long,
    val totalFound: Int,
    val newAdded: Int
)

@Singleton
class ScanLibraryUseCase @Inject constructor(
    private val driveFileManager: DriveFileManager,
    private val libraryRepository: LibraryRepository,
    private val bookRepository: BookRepository
) {
    private companion object {
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac", "wma", "wav"
        )
        val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mkv", "avi", "mov", "webm"
        )
        val ALL_EXTENSIONS = AUDIO_EXTENSIONS + VIDEO_EXTENSIONS
    }

    suspend fun execute(driveFolderId: String, folderName: String): ScanResult {
        val library = libraryRepository.getOrCreateLibrary(driveFolderId, folderName)

        // Recursively collect all media files from Drive
        val allFiles = mutableListOf<Pair<DriveFileDto, String>>() // file + subfolder path
        scanFolder(driveFolderId, "", allFiles)

        // Convert to BookEntities, skipping files already in DB
        val newBooks = mutableListOf<BookEntity>()
        for ((file, path) in allFiles) {
            val existing = bookRepository.getBookByDriveFileId(file.id)
            if (existing != null) continue

            val parsed = parseFilename(file.name)
            newBooks.add(
                BookEntity(
                    libraryId = library.id,
                    title = parsed.title,
                    author = parsed.author,
                    driveFileId = file.id,
                    filePath = path.ifEmpty { null },
                    fileName = file.name,
                    fileSize = file.size?.toLongOrNull() ?: 0,
                    mimeType = file.mimeType
                )
            )
        }

        val insertedIds = bookRepository.insertBooks(newBooks)
        libraryRepository.updateLastSync(library.id)

        return ScanResult(
            libraryId = library.id,
            totalFound = allFiles.size,
            newAdded = insertedIds.count { it != -1L }
        )
    }

    private suspend fun scanFolder(
        folderId: String,
        currentPath: String,
        results: MutableList<Pair<DriveFileDto, String>>
    ) {
        val files = driveFileManager.listFiles(folderId)
        for (file in files) {
            if (file.isFolder) {
                val subPath = if (currentPath.isEmpty()) file.name else "$currentPath/${file.name}"
                scanFolder(file.id, subPath, results)
            } else {
                val ext = file.name.substringAfterLast('.', "").lowercase()
                if (ext in ALL_EXTENSIONS) {
                    results.add(file to currentPath)
                }
            }
        }
    }

    /**
     * Parses "Author - Title.m4b" or just "Title.m4b" from filename.
     * Strips file extension and common patterns.
     */
    private fun parseFilename(filename: String): ParsedBook {
        val nameWithoutExt = filename.substringBeforeLast('.')

        // Try "Author - Title" pattern
        val dashIndex = nameWithoutExt.indexOf(" - ")
        return if (dashIndex > 0) {
            ParsedBook(
                title = cleanTitle(nameWithoutExt.substring(dashIndex + 3).trim()),
                author = nameWithoutExt.substring(0, dashIndex).trim()
            )
        } else {
            ParsedBook(
                title = cleanTitle(nameWithoutExt.trim()),
                author = null
            )
        }
    }

    // Strip common audiobook suffixes from titles
    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("\\s*[\\(\\[]?unabridged[\\)\\]]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[\\(\\[]?audiobook[\\)\\]]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*audiobook", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[\\(\\[]\\d+(?:st|nd|rd|th)\\s+edition[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private data class ParsedBook(
        val title: String,
        val author: String?
    )
}
