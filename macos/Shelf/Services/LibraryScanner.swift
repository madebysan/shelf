import Foundation
import CoreData

/// Scans a folder for audiobook files and populates Core Data.
/// Uses a two-pass approach so books appear instantly (Pass 1)
/// and metadata loads progressively in the background (Pass 2).
class LibraryScanner {

    // Audio file extensions we support
    private static let supportedExtensions: Set<String> = ["m4b", "m4a", "mp3"]

    // MARK: - Pass 1: Fast File Discovery

    /// Discovers audio files and creates Book entities with just file paths.
    /// Books appear in the grid immediately with filename-based titles and placeholder covers.
    /// Returns the list of Books that still need metadata extraction.
    static func scanFiles(folder: URL, library: Library, context: NSManagedObjectContext) -> ScanFilesResult {
        var result = ScanFilesResult()

        // Find all audio files in the folder
        let audioFiles = findAudioFiles(in: folder)
        result.totalFilesFound = audioFiles.count

        // Fetch existing books for THIS library only
        let existingBooks = fetchBooks(for: library, context: context)
        var existingByPath: [String: Book] = [:]
        for book in existingBooks {
            if let path = book.filePath {
                existingByPath[path] = book
            }
        }

        let foundPaths = Set(audioFiles.map { $0.path })

        // Remove books whose files no longer exist
        for (path, book) in existingByPath {
            if !foundPaths.contains(path) {
                context.delete(book)
                result.removed += 1
            }
        }

        // Create entries for new files (no metadata extraction yet)
        var booksNeedingMetadata: [Book] = []

        for fileURL in audioFiles {
            let path = fileURL.path

            if let existing = existingByPath[path] {
                // Check if file was modified since last scan
                let currentModDate = fileModificationDate(for: fileURL)
                if existing.metadataLoaded,
                   let savedModDate = existing.fileModDate,
                   let currentModDate = currentModDate,
                   savedModDate >= currentModDate {
                    // File unchanged and metadata already loaded — skip
                    result.skipped += 1
                    continue
                }
                // File changed or metadata not yet loaded — queue for extraction
                booksNeedingMetadata.append(existing)
                result.queued += 1
            } else {
                // New file — create a Book entity with just the file path
                let book = Book(context: context)
                book.id = UUID()
                book.filePath = path
                book.library = library
                book.metadataLoaded = false
                booksNeedingMetadata.append(book)
                result.added += 1
            }
        }

        // Save immediately so books appear in the grid right away
        do {
            if context.hasChanges {
                try context.save()
            }
        } catch {
            print("Failed to save after file scan: \(error)")
        }

        result.booksNeedingMetadata = booksNeedingMetadata
        return result
    }

    // MARK: - Pass 2: Background Metadata Extraction

    /// Extracts metadata for books in batches with progress reporting.
    /// Uses concurrent extraction (batches of 5) with per-file timeouts.
    /// Reports progress via the callback so the UI can show a progress bar.
    /// Saves to Core Data after each batch so progress is durable.
    static func extractMetadataInBackground(
        books: [Book],
        context: NSManagedObjectContext,
        onProgress: @MainActor @Sendable (Int, Int) -> Void
    ) async {
        let total = books.count
        var completed = 0
        let batchSize = 5

        // Process in batches to avoid overwhelming the filesystem (especially cloud mounts)
        for batchStart in stride(from: 0, to: total, by: batchSize) {
            // Check for cancellation between batches
            if Task.isCancelled { break }

            let batchEnd = min(batchStart + batchSize, total)
            let batch = Array(books[batchStart..<batchEnd])

            // Extract metadata for this batch concurrently
            await withTaskGroup(of: (NSManagedObjectID, AudiobookMetadata?, Date?)?.self) { group in
                for book in batch {
                    guard let path = book.filePath else { continue }
                    let fileURL = URL(fileURLWithPath: path)
                    let objectID = book.objectID
                    let modDate = fileModificationDate(for: fileURL)

                    group.addTask {
                        let metadata = await MetadataExtractor.extractWithTimeout(from: fileURL, timeout: 10)
                        return (objectID, metadata, modDate)
                    }
                }

                // Collect results and update Core Data
                for await result in group {
                    guard let (objectID, metadata, modDate) = result else { continue }
                    guard let book = try? context.existingObject(with: objectID) as? Book else { continue }

                    if let metadata = metadata {
                        // Only mark as loaded if we got meaningful data (duration > 0).
                        // Google Drive's FUSE mount can timeout mid-read, returning an
                        // empty AudiobookMetadata struct. Without this check, those books
                        // get permanently marked "done" with no title/author/cover.
                        if metadata.duration > 0 {
                            updateBook(book, with: metadata, modDate: modDate)
                            book.metadataLoaded = true
                        }
                        // duration == 0 means AVFoundation couldn't read the file — leave
                        // metadataLoaded = false so it gets retried on the next scan
                    }
                    // If metadata is nil (extractWithTimeout expired), also left for retry
                }
            }

            // Save after each batch so progress is durable
            do {
                if context.hasChanges {
                    try context.save()
                }
            } catch {
                print("Failed to save metadata batch: \(error)")
            }

            // Update progress
            completed += batch.count
            await onProgress(completed, total)

            // Brief yield between batches so the UI stays responsive
            // and Drive's filesystem isn't overwhelmed
            try? await Task.sleep(for: .milliseconds(100))
        }
    }

    // MARK: - Legacy scan() for backwards compatibility

    /// Full scan — combines both passes sequentially.
    /// Used for small local libraries where the two-pass approach isn't needed.
    static func scan(folder: URL, library: Library, context: NSManagedObjectContext) async -> ScanResult {
        let filesResult = scanFiles(folder: folder, library: library, context: context)

        // Extract metadata for all books that need it
        await extractMetadataInBackground(
            books: filesResult.booksNeedingMetadata,
            context: context,
            onProgress: { _, _ in }
        )

        return ScanResult(
            totalFilesFound: filesResult.totalFilesFound,
            added: filesResult.added,
            updated: filesResult.queued,
            removed: filesResult.removed,
            skipped: filesResult.skipped
        )
    }

    // MARK: - Private Helpers

    /// Recursively finds all supported audio files in a folder
    private static func findAudioFiles(in folder: URL) -> [URL] {
        var audioFiles: [URL] = []

        guard let enumerator = FileManager.default.enumerator(
            at: folder,
            includingPropertiesForKeys: [.isRegularFileKey, .contentModificationDateKey],
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else {
            return audioFiles
        }

        for case let fileURL as URL in enumerator {
            let ext = fileURL.pathExtension.lowercased()
            if supportedExtensions.contains(ext) {
                audioFiles.append(fileURL)
            }
        }

        return audioFiles
    }

    /// Fetches Book entities for a specific library
    private static func fetchBooks(for library: Library, context: NSManagedObjectContext) -> [Book] {
        let request: NSFetchRequest<Book> = Book.fetchRequest()
        request.predicate = NSPredicate(format: "library == %@", library)
        do {
            return try context.fetch(request)
        } catch {
            print("Failed to fetch books: \(error)")
            return []
        }
    }

    /// Returns the modification date of a file
    private static func fileModificationDate(for url: URL) -> Date? {
        try? FileManager.default.attributesOfItem(atPath: url.path)[.modificationDate] as? Date
    }

    /// Updates a Book entity with extracted metadata
    private static func updateBook(_ book: Book, with metadata: AudiobookMetadata, modDate: Date?) {
        book.title = metadata.title
        book.author = metadata.author
        book.genre = metadata.genre
        book.year = metadata.year
        book.duration = metadata.duration
        book.coverArtData = metadata.coverArtData
        book.hasChapters = metadata.hasChapters
        book.fileModDate = modDate
        // Don't overwrite playbackPosition or lastPlayedDate — those are user data
    }
}

/// Results from Pass 1 (fast file discovery)
struct ScanFilesResult {
    var totalFilesFound: Int = 0
    var added: Int = 0
    var queued: Int = 0    // Existing books needing metadata refresh
    var removed: Int = 0
    var skipped: Int = 0
    var booksNeedingMetadata: [Book] = []

    var summary: String {
        "Found \(totalFilesFound) files: \(added) new, \(queued) need metadata, \(removed) removed, \(skipped) unchanged"
    }
}

/// Results from a full library scan operation (both passes)
struct ScanResult {
    var totalFilesFound: Int = 0
    var added: Int = 0
    var updated: Int = 0
    var removed: Int = 0
    var skipped: Int = 0

    var summary: String {
        "Found \(totalFilesFound) files: \(added) added, \(updated) updated, \(removed) removed, \(skipped) unchanged"
    }
}
