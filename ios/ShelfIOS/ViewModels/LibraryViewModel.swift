import Foundation
import CoreData
import Combine
import UIKit

/// Manages the audiobook library — syncs Drive file list with Core Data,
/// handles downloads, metadata extraction, and library filtering.
@MainActor
class LibraryViewModel: ObservableObject {

    @Published var books: [Book] = []
    @Published var isLoading: Bool = false
    @Published var isFetchingMetadata: Bool = false
    @Published var isLookingUpCovers: Bool = false
    @Published var coverLookupProgress: Int = 0
    @Published var coverLookupTotal: Int = 0
    @Published var error: String?
    @Published var searchText: String = ""

    enum SortOrder: String, CaseIterable {
        case title = "Title"
        case recent = "Recently Played"
        case author = "Author"
        case rating = "Rating"
        case subfolder = "Subfolder"
        case shortest = "Shortest First"
        case longest = "Longest First"
        case largestFile = "Largest File"
        case smallestFile = "Smallest File"
    }
    @Published var sortOrder: SortOrder = .title

    /// Filter chips for library organization
    enum LibraryFilter: String, CaseIterable {
        case all = "All"
        case inProgress = "In Progress"
        case completed = "Completed"
        case recentlyAdded = "Recently Added"
        case notStarted = "Not Started"
        case quickListens = "Under 6 Hours"
        case starred = "Starred"
        case downloaded = "Downloaded"
        case hidden = "Hidden"
    }

    /// Filters that should appear in the chip bar (hidden only shows when there are hidden books)
    var visibleFilters: [LibraryFilter] {
        var filters: [LibraryFilter] = [.all, .inProgress]
        // Show Starred right after In Progress, but only when there are starred books
        if books.contains(where: { $0.isStarred }) {
            filters.append(.starred)
        }
        filters.append(contentsOf: [.completed, .recentlyAdded, .notStarted])
        // Show "Under 6 Hours" only when the library has qualifying books
        if books.contains(where: { $0.duration > 0 && $0.duration < 6 * 3600 }) {
            filters.append(.quickListens)
        }
        // Show Downloaded only when there are downloaded books
        if books.contains(where: { $0.isDownloaded }) {
            filters.append(.downloaded)
        }
        if books.contains(where: { $0.isHidden }) {
            filters.append(.hidden)
        }
        return filters
    }
    @Published var filter: LibraryFilter = .all
    @Published var selectedGenre: String? = nil

    /// Available genres with book counts, sorted by count descending
    var availableGenres: [(name: String, count: Int)] {
        var genreCounts: [String: Int] = [:]
        for book in books where !book.isHidden {
            genreCounts[book.displayGenre, default: 0] += 1
        }
        return genreCounts.map { (name: $0.key, count: $0.value) }
            .sorted { $0.count > $1.count }
    }

    /// Returns up to `limit` cover images for books in the given genre.
    /// Prioritizes books that have real cover art over placeholders.
    func coversForGenre(_ genre: String, limit: Int = 4) -> [UIImage] {
        let genreBooks = books.filter { !$0.isHidden && $0.displayGenre == genre }
        // Prioritize books with actual cover art
        let withCovers = genreBooks.filter { $0.coverArtData != nil }
        let withoutCovers = genreBooks.filter { $0.coverArtData == nil }
        let ordered = withCovers + withoutCovers
        return Array(ordered.prefix(limit)).map { $0.coverImage }
    }

    let driveService: GoogleDriveService
    let downloadManager: DownloadManager
    private let auth: GoogleAuthService
    private let context: NSManagedObjectContext
    private var cancellables = Set<AnyCancellable>()
    private var coverLookupTask: Task<Void, Never>?

    init(driveService: GoogleDriveService, downloadManager: DownloadManager, auth: GoogleAuthService) {
        self.driveService = driveService
        self.downloadManager = downloadManager
        self.auth = auth
        self.context = PersistenceController.shared.container.viewContext

        // Subscribe to background download completions
        downloadManager.downloadCompleted
            .receive(on: DispatchQueue.main)
            .sink { [weak self] (driveFileId, localURL) in
                self?.handleDownloadCompleted(driveFileId: driveFileId, localURL: localURL)
            }
            .store(in: &cancellables)
    }

    /// Filtered and sorted books for display
    var displayBooks: [Book] {
        var result = books

        // Filter by genre if one is selected
        if let genre = selectedGenre {
            result = result.filter { $0.displayGenre == genre }
        }

        // Apply library filter
        switch filter {
        case .all:
            // Hide hidden books from the main view
            result = result.filter { !$0.isHidden }
        case .inProgress:
            result = result.filter { !$0.isHidden && $0.isInProgress }
        case .completed:
            result = result.filter { !$0.isHidden && $0.isCompleted }
        case .recentlyAdded:
            let cutoff = Calendar.current.date(byAdding: .day, value: -7, to: Date()) ?? Date()
            result = result.filter { !$0.isHidden && ($0.addedDate ?? .distantPast) > cutoff }
        case .notStarted:
            result = result.filter { !$0.isHidden && $0.playbackPosition == 0 && !$0.isCompleted }
        case .quickListens:
            result = result.filter { !$0.isHidden && $0.duration > 0 && $0.duration < 6 * 3600 }
        case .starred:
            result = result.filter { !$0.isHidden && $0.isStarred }
        case .downloaded:
            result = result.filter { !$0.isHidden && $0.isDownloaded }
        case .hidden:
            result = result.filter { $0.isHidden }
        }

        // Filter by search text
        if !searchText.isEmpty {
            let query = searchText.lowercased()
            result = result.filter {
                $0.displayTitle.lowercased().contains(query) ||
                $0.displayAuthor.lowercased().contains(query)
            }
        }

        // Sort
        switch sortOrder {
        case .title:
            result.sort { $0.displayTitle.localizedCaseInsensitiveCompare($1.displayTitle) == .orderedAscending }
        case .recent:
            result.sort { ($0.lastPlayedDate ?? .distantPast) > ($1.lastPlayedDate ?? .distantPast) }
        case .author:
            result.sort { $0.displayAuthor.localizedCaseInsensitiveCompare($1.displayAuthor) == .orderedAscending }
        case .rating:
            result.sort {
                // Highest rating first, unrated (0) go last, ties sorted by title
                if $0.rating == $1.rating {
                    return $0.displayTitle.localizedCaseInsensitiveCompare($1.displayTitle) == .orderedAscending
                }
                if $0.rating == 0 { return false }
                if $1.rating == 0 { return true }
                return $0.rating > $1.rating
            }
        case .subfolder:
            result.sort {
                let a = $0.displaySubfolder ?? ""
                let b = $1.displaySubfolder ?? ""
                if a == b {
                    return $0.displayTitle.localizedCaseInsensitiveCompare($1.displayTitle) == .orderedAscending
                }
                if a.isEmpty { return false }
                if b.isEmpty { return true }
                return a.localizedCaseInsensitiveCompare(b) == .orderedAscending
            }
        case .shortest:
            result.sort {
                // Books with no duration go last
                if $0.duration == 0 && $1.duration == 0 { return false }
                if $0.duration == 0 { return false }
                if $1.duration == 0 { return true }
                return $0.duration < $1.duration
            }
        case .longest:
            result.sort {
                if $0.duration == 0 && $1.duration == 0 { return false }
                if $0.duration == 0 { return false }
                if $1.duration == 0 { return true }
                return $0.duration > $1.duration
            }
        case .largestFile:
            result.sort {
                if $0.fileSize == 0 && $1.fileSize == 0 { return false }
                if $0.fileSize == 0 { return false }
                if $1.fileSize == 0 { return true }
                return $0.fileSize > $1.fileSize
            }
        case .smallestFile:
            result.sort {
                if $0.fileSize == 0 && $1.fileSize == 0 { return false }
                if $0.fileSize == 0 { return false }
                if $1.fileSize == 0 { return true }
                return $0.fileSize < $1.fileSize
            }
        }

        return result
    }

    /// Syncs the Drive folder contents with the local Core Data library.
    /// Creates Book entities for new files, removes ones that no longer exist in Drive.
    /// After sync, kicks off remote metadata extraction for books that don't have it yet.
    func syncWithDrive(folderId: String) async {
        isLoading = true
        error = nil

        do {
            // Use recursive listing to include subfolders
            let driveResults = try await driveService.listMediaFilesRecursive(in: folderId)

            // Ensure we have a Library entity (or create one)
            let library = getOrCreateLibrary(folderId: folderId)

            // Fetch existing books scoped to THIS library only
            let request: NSFetchRequest<Book> = Book.fetchRequest()
            request.predicate = NSPredicate(format: "library == %@", library)
            let existingBooks = (try? context.fetch(request)) ?? []
            var booksByDriveId: [String: Book] = [:]
            for book in existingBooks {
                if let driveId = book.driveFileId {
                    booksByDriveId[driveId] = book
                }
            }

            // Add new files, update existing
            var seenIds = Set<String>()
            for (file, subfolder) in driveResults {
                seenIds.insert(file.id)

                if let existing = booksByDriveId[file.id] {
                    // Update file size if it changed
                    if let size = file.size {
                        existing.fileSize = size
                    }
                    // Update subfolder name (may change if file was moved)
                    existing.subfolderName = subfolder
                } else {
                    // New book — create Core Data entity
                    let book = Book(context: context)
                    book.id = UUID()
                    book.driveFileId = file.id
                    book.filePath = file.name       // relative filename
                    book.fileSize = file.size ?? 0
                    book.fileModDate = file.modifiedTime
                    book.isDownloaded = false
                    book.addedDate = Date()
                    book.subfolderName = subfolder
                    book.library = library
                }
            }

            // Remove books that no longer exist in Drive (but keep downloaded files)
            for book in existingBooks {
                if let driveId = book.driveFileId, !seenIds.contains(driveId) {
                    context.delete(book)
                }
            }

            PersistenceController.shared.save()

            // Reload books scoped to this library
            loadBooks(for: library)

            // Step 1: Parse filenames immediately for all books without metadata.
            // This gives every book a title and author instantly (no network needed).
            let booksNeedingTitles = books.filter { !$0.metadataLoaded }
            if !booksNeedingTitles.isEmpty {
                print("[Shelf] Parsing filenames for \(booksNeedingTitles.count) books")
                for book in booksNeedingTitles {
                    if let name = book.filePath {
                        let parsed = MetadataExtractor.parseFilename(name)
                        if book.title == nil { book.title = parsed.title }
                        if book.author == nil { book.author = parsed.author }
                    }
                    book.metadataLoaded = true
                }
                PersistenceController.shared.save()
                loadBooks(for: library)
                print("[Shelf] Filename parsing done — all books have titles")
            }

            // Step 2: Look up cover art from APIs using the parsed titles (fast).
            // This runs before AVFoundation so covers appear quickly.
            await lookUpMissingCovers()

            // Step 3: AVFoundation extraction in background — fills in duration, genre,
            // year, chapters, and may upgrade titles from embedded metadata.
            await fetchRemoteMetadata()
        } catch {
            self.error = error.localizedDescription
        }

        isLoading = false
    }

    /// Loads books from Core Data, scoped to a specific library
    func loadBooks(for library: Library) {
        let request: NSFetchRequest<Book> = Book.fetchRequest()
        request.predicate = NSPredicate(format: "library == %@", library)
        request.sortDescriptors = [NSSortDescriptor(keyPath: \Book.title, ascending: true)]
        books = (try? context.fetch(request)) ?? []
    }

    /// Downloads a book from Drive, extracts metadata after download
    func downloadBook(_ book: Book) {
        guard let driveId = book.driveFileId, let fileName = book.filePath else { return }
        downloadManager.download(driveFileId: driveId, fileName: fileName)
    }

    /// Downloads all non-downloaded, non-hidden books
    func downloadAll() {
        let eligible = books.filter { !$0.isDownloaded && !$0.isHidden }
        downloadManager.queuedCount = eligible.count
        for book in eligible {
            downloadBook(book)
        }
    }

    /// Downloads all non-downloaded books in the current filtered view
    func downloadFiltered() {
        let eligible = displayBooks.filter { !$0.isDownloaded }
        downloadManager.queuedCount = eligible.count
        for book in eligible {
            downloadBook(book)
        }
    }

    /// Count of non-downloaded books (for confirmation dialogs)
    var nonDownloadedCount: Int {
        books.filter { !$0.isDownloaded && !$0.isHidden }.count
    }

    /// Count of non-downloaded books in current filtered view
    var nonDownloadedFilteredCount: Int {
        displayBooks.filter { !$0.isDownloaded }.count
    }

    /// Deletes the local file for a book but keeps metadata intact
    func deleteDownload(for book: Book) {
        if let fileName = book.filePath {
            DownloadManager.deleteLocalFile(fileName: fileName)
        }
        book.isDownloaded = false
        // Keep metadata (title, author, cover, duration) — it was fetched remotely
        PersistenceController.shared.save()
        objectWillChange.send()
    }

    /// Clears all cached metadata (covers, titles, authors, etc.) from Core Data.
    /// Books revert to showing filenames until the next sync re-fetches metadata.
    /// Returns the approximate bytes freed (mostly cover art).
    func clearMetadataCache() -> Int64 {
        var bytesFreed: Int64 = 0
        for book in books {
            if let coverData = book.coverArtData {
                bytesFreed += Int64(coverData.count)
            }
            book.coverArtData = nil
            book.coverFromAPI = false
            book.title = nil
            book.author = nil
            book.genre = nil
            book.year = 0
            book.rating = 0
            book.duration = 0
            book.hasChapters = false
            book.metadataLoaded = false
            book.metadataFetchDate = nil
            book.coverLookupAttempted = false
        }
        PersistenceController.shared.save()
        objectWillChange.send()
        return bytesFreed
    }

    // MARK: - Import / Export

    /// Exports all book progress and bookmarks to JSON data
    func exportProgressData() -> Data? {
        ProgressExporter.exportProgress(books: books)
    }

    /// Imports progress from JSON data, returns a summary string
    func importProgress(from data: Data) -> String? {
        let context = PersistenceController.shared.container.viewContext
        guard let result = ProgressExporter.importProgress(from: data, context: context) else {
            return nil
        }

        objectWillChange.send()

        var summary = "Updated \(result.booksUpdated) book(s)."
        if result.bookmarksCreated > 0 {
            summary += "\nImported \(result.bookmarksCreated) bookmark(s)."
        }
        if result.booksNotFound > 0 {
            summary += "\nSkipped \(result.booksNotFound) book(s) not in library."
        }
        return summary
    }

    // MARK: - Remote Metadata

    /// Forces a full re-fetch of metadata (cover art, title, author, etc.) for all
    /// books in the current library — even ones that already have metadata.
    func refreshAllMetadata() async {
        for book in books {
            book.metadataLoaded = false
            book.coverLookupAttempted = false
            book.coverArtData = nil
            book.duration = 0
        }
        PersistenceController.shared.save()

        // Same pipeline as syncWithDrive: filenames → API covers → AVFoundation
        let booksNeedingTitles = books.filter { !$0.metadataLoaded }
        for book in booksNeedingTitles {
            if let name = book.filePath {
                let parsed = MetadataExtractor.parseFilename(name)
                if book.title == nil { book.title = parsed.title }
                if book.author == nil { book.author = parsed.author }
            }
            book.metadataLoaded = true
        }
        PersistenceController.shared.save()

        await lookUpMissingCovers()
        await fetchRemoteMetadata()
    }

    /// Refreshes metadata for a single book by re-fetching from Drive.
    /// Uses a longer timeout than bulk sync since only one file is being fetched.
    func fetchMetadataForBook(_ book: Book) async {
        guard let driveId = book.driveFileId else { return }
        guard let token = await auth.refreshTokenIfNeeded() else { return }

        // Clear existing cover so AVFoundation doesn't use a stale cache
        book.coverArtData = nil
        book.metadataLoaded = false
        book.coverLookupAttempted = false
        PersistenceController.shared.save()
        objectWillChange.send()

        // Longer timeout for single-book refresh (no concurrency pressure)
        guard let metadata = await MetadataExtractor.extractRemoteWithTimeout(
            driveFileId: driveId, token: token, timeout: 45
        ) else {
            // Timed out — mark as loaded so it doesn't show as "fetching" forever
            book.metadataLoaded = true
            PersistenceController.shared.save()
            objectWillChange.send()
            return
        }

        applyMetadata(metadata, to: book, fileName: book.filePath)
        PersistenceController.shared.save()
        objectWillChange.send()

        // If no embedded cover was found, try the API waterfall
        if book.coverArtData == nil, let title = book.title {
            print("Cover lookup: trying API for \"\(title)\"")
            if let coverData = await CoverArtService.fetchCover(title: title, author: book.author) {
                book.coverArtData = coverData
                book.coverFromAPI = true
                print("Found cover for \"\(title)\"")
            }
            book.coverLookupAttempted = true
            PersistenceController.shared.save()
            objectWillChange.send()
        }
    }

    /// Fetches full metadata (duration, genre, year, chapters) from Drive via AVFoundation.
    /// Runs after filename parsing and cover lookup — this is the slow background pass.
    /// Books without duration haven't been through AVFoundation yet.
    private func fetchRemoteMetadata() async {
        let booksNeedingMetadata = books.filter { $0.duration == 0 && $0.driveFileId != nil }
        guard !booksNeedingMetadata.isEmpty else {
            print("[Shelf] All books already have full metadata")
            return
        }

        guard let token = await auth.refreshTokenIfNeeded() else { return }

        isFetchingMetadata = true
        let total = booksNeedingMetadata.count
        print("[Shelf] Starting metadata fetch for \(total) books")

        // Pass 1: 15s timeout — enough for well-structured files, fast failure for huge ones
        let pass1 = await fetchMetadataBatch(booksNeedingMetadata, token: token, timeout: 15)
        print("[Shelf] Pass 1 complete: \(pass1.success) succeeded, \(pass1.fail) failed out of \(total)")

        // Pass 2: Quick retry for failed books — fresh token, slightly longer timeout.
        // Only retry a few; large files that timed out at 15s won't succeed at 20s either.
        let stillNeedDuration = books.filter { $0.duration == 0 && $0.driveFileId != nil }
        if !stillNeedDuration.isEmpty && stillNeedDuration.count <= 10 {
            print("[Shelf] Retrying \(stillNeedDuration.count) failed books...")
            try? await Task.sleep(for: .seconds(2))

            if let retryToken = await auth.refreshTokenIfNeeded() {
                let pass2 = await fetchMetadataBatch(stillNeedDuration, token: retryToken, timeout: 20)
                print("[Shelf] Pass 2 complete: \(pass2.success) succeeded, \(pass2.fail) still failed")
            }
        } else if !stillNeedDuration.isEmpty {
            print("[Shelf] Skipping retry — \(stillNeedDuration.count) books failed (too many to retry)")
        }

        // Pass 3: Cover art fallback — for books where the API didn't find a cover,
        // try extracting the embedded cover from the audio file via AVFoundation.
        // This uses skipCoverArt: false, so it's slower but catches embedded art.
        let needEmbeddedCover = books.filter {
            $0.coverLookupAttempted && $0.coverArtData == nil && $0.driveFileId != nil
        }
        if !needEmbeddedCover.isEmpty {
            print("[Shelf] Trying embedded cover extraction for \(needEmbeddedCover.count) books (API had no results)")
            if let coverToken = await auth.refreshTokenIfNeeded() {
                let pass3 = await fetchMetadataBatch(needEmbeddedCover, token: coverToken, timeout: 15, extractCoverArt: true)
                print("[Shelf] Embedded cover pass: \(pass3.success) succeeded, \(pass3.fail) failed")
            }
        }

        let finalNoDuration = books.filter { $0.duration == 0 && $0.driveFileId != nil }.count
        print("[Shelf] Metadata fetch done. \(total - finalNoDuration)/\(total) got full metadata, \(finalNoDuration) still missing duration")

        isFetchingMetadata = false
    }

    /// Processes a batch of books for metadata extraction with the given timeout.
    /// Runs 3 concurrent extractions to stay within iOS memory limits, refreshes token every 50 books, saves every 10.
    /// When `extractCoverArt` is true, AVFoundation also extracts embedded cover art (slower but catches embedded covers).
    private func fetchMetadataBatch(
        _ booksToProcess: [Book],
        token: String,
        timeout: TimeInterval,
        extractCoverArt: Bool = false
    ) async -> (success: Int, fail: Int) {
        var successCount = 0
        var failCount = 0
        var saveCounter = 0
        var currentToken = token
        var booksSinceRefresh = 0

        await withTaskGroup(of: Bool.self) { group in
            var active = 0
            for book in booksToProcess {
                guard let driveId = book.driveFileId else { continue }

                // Refresh token every 50 books to avoid OAuth expiry mid-batch
                booksSinceRefresh += 1
                if booksSinceRefresh >= 50 {
                    if let refreshed = await auth.refreshTokenIfNeeded() {
                        currentToken = refreshed
                    }
                    booksSinceRefresh = 0
                }

                // Limit to 3 concurrent extractions — cover art is skipped for remote
                // files (fetched via API instead), so memory usage is much lower.
                if active >= 3 {
                    if let result = await group.next() {
                        if result { successCount += 1 } else { failCount += 1 }
                        active -= 1
                        saveCounter += 1
                        if saveCounter >= 10 {
                            PersistenceController.shared.save()
                            objectWillChange.send()
                            saveCounter = 0
                        }
                    }
                }

                let tokenForTask = currentToken
                let fileName = book.filePath
                let skipCover = !extractCoverArt
                active += 1
                group.addTask { [weak self] in
                    guard let self else { return false }
                    guard let metadata = await MetadataExtractor.extractRemoteWithTimeout(
                        driveFileId: driveId, token: tokenForTask, timeout: timeout, skipCoverArt: skipCover
                    ) else {
                        print("[Shelf] Metadata timeout: \(fileName ?? driveId)")
                        return false
                    }

                    await MainActor.run {
                        self.applyMetadata(metadata, to: book, fileName: fileName)
                    }
                    return true
                }
            }

            // Collect remaining task results
            for await result in group {
                if result { successCount += 1 } else { failCount += 1 }
                saveCounter += 1
                if saveCounter >= 10 {
                    PersistenceController.shared.save()
                    objectWillChange.send()
                    saveCounter = 0
                }
            }
        }

        // Final save for any remaining unsaved books
        PersistenceController.shared.save()
        objectWillChange.send()

        return (successCount, failCount)
    }

    /// Applies extracted metadata to a book, with filename parsing as a fallback
    /// for missing title/author.
    private func applyMetadata(_ metadata: AudiobookMetadata, to book: Book, fileName: String?) {
        var title = metadata.title
        var author = metadata.author

        // Filename fallback for missing title/author
        if (title == nil || author == nil), let name = fileName {
            let parsed = MetadataExtractor.parseFilename(name)
            if title == nil { title = parsed.title }
            if author == nil { author = parsed.author }
        }

        if let title = title { book.title = title }
        if let author = author { book.author = author }
        if let genre = metadata.genre { book.genre = genre }
        if metadata.year > 0 { book.year = metadata.year }
        if metadata.duration > 0 { book.duration = metadata.duration }
        if let coverData = metadata.coverArtData { book.coverArtData = coverData }
        book.hasChapters = metadata.hasChapters
        if metadata.rating > 0 { book.rating = metadata.rating }
        book.metadataLoaded = true
        book.metadataFetchDate = Date()
    }

    /// Called when a background download completes — marks book as downloaded and extracts metadata
    private func handleDownloadCompleted(driveFileId: String, localURL: URL?) {
        // Clean up the download progress entry regardless of success/failure
        // (keep it briefly so the UI can show the error, then remove)
        if localURL == nil {
            let errorMsg = downloadManager.activeDownloads[driveFileId]?.error
            if let errorMsg {
                // Surface as a playback error briefly — this is the simplest path for now
                print("Download failed for \(driveFileId): \(errorMsg)")
            }
            downloadManager.activeDownloads.removeValue(forKey: driveFileId)
            objectWillChange.send()
            return
        }

        downloadManager.activeDownloads.removeValue(forKey: driveFileId)
        guard let localURL = localURL else { return }

        // Find the book by drive ID
        guard let book = books.first(where: { $0.driveFileId == driveFileId }) else { return }

        book.isDownloaded = true
        PersistenceController.shared.save()

        // Extract metadata from the local file (more reliable than remote for some formats)
        Task {
            if let metadata = await MetadataExtractor.extractWithTimeout(from: localURL) {
                applyMetadata(metadata, to: book, fileName: book.filePath)
                PersistenceController.shared.save()
            }

            self.objectWillChange.send()
        }
    }

    // MARK: - Cover Art Management

    /// Clears the cover art for a single book and prevents re-fetching from API.
    func clearCover(for book: Book) {
        book.coverArtData = nil
        book.coverFromAPI = false
        book.coverLookupAttempted = true  // Don't re-fetch from API
        PersistenceController.shared.save()
        objectWillChange.send()
    }

    /// Clears all generated covers (API-fetched and video thumbnails), keeping
    /// embedded cover art from audio metadata. Cleared books show a placeholder
    /// until the next sync regenerates covers.
    /// Returns the number of covers cleared.
    @discardableResult
    func clearAllAPICovers() -> Int {
        var cleared = 0
        for book in books {
            if book.isVideo {
                // Video covers are always generated (thumbnail or API) — clear and
                // reset duration so fetchRemoteMetadata re-extracts on next sync
                if book.coverArtData != nil { cleared += 1 }
                book.coverArtData = nil
                book.coverFromAPI = false
                book.duration = 0
            } else if book.coverFromAPI {
                // Audio: only clear API-fetched covers, keep embedded metadata art
                book.coverArtData = nil
                book.coverFromAPI = false
                book.coverLookupAttempted = false
                cleared += 1
            }
        }
        PersistenceController.shared.save()
        // Force Core Data to re-fire KVO for each book so @ObservedObject picks up
        // the coverArtData change (external binary storage doesn't always notify)
        for book in books {
            context.refresh(book, mergeChanges: true)
        }
        objectWillChange.send()
        return cleared
    }

    // MARK: - Cover Art Lookup

    /// Searches free APIs (iTunes, Google Books, Open Library) for cover art
    /// on books that have metadata but no cover image. Called automatically after
    /// metadata extraction, or manually from Settings.
    func lookUpMissingCovers() async {
        // Find books that need cover lookup: have metadata + title, but no cover and haven't been tried
        let candidates = books.filter {
            $0.metadataLoaded &&
            $0.coverArtData == nil &&
            !$0.coverLookupAttempted &&
            $0.title != nil
        }

        guard !candidates.isEmpty else {
            let total = books.count
            let noMeta = books.filter { !$0.metadataLoaded }.count
            let noCover = books.filter { $0.coverArtData == nil }.count
            let alreadyTried = books.filter { $0.coverLookupAttempted }.count
            let noTitle = books.filter { $0.title == nil }.count
            print("Cover lookup: 0 candidates. total=\(total), noMetadata=\(noMeta), noCover=\(noCover), alreadyTried=\(alreadyTried), noTitle=\(noTitle)")
            return
        }

        isLookingUpCovers = true
        coverLookupProgress = 0
        coverLookupTotal = candidates.count

        print("Cover lookup: \(candidates.count) books to search")

        for book in candidates {
            guard !Task.isCancelled else { break }

            guard let title = book.title else { continue }

            if let coverData = await CoverArtService.fetchCover(title: title, author: book.author) {
                book.coverArtData = coverData
                book.coverFromAPI = true
                print("Found cover for \"\(title)\"")
            }

            book.coverLookupAttempted = true
            coverLookupProgress += 1

            // Save and refresh after each book so covers appear progressively
            PersistenceController.shared.save()
            objectWillChange.send()

            // Rate limit: 3 seconds between books (up to 6 API calls per book)
            try? await Task.sleep(for: .seconds(3))
        }

        let found = candidates.filter { $0.coverArtData != nil }.count
        print("Cover lookup complete: \(found)/\(candidates.count) covers found")

        isLookingUpCovers = false
    }

    // MARK: - Private

    private func getOrCreateLibrary(folderId: String) -> Library {
        let request: NSFetchRequest<Library> = Library.fetchRequest()
        request.predicate = NSPredicate(format: "folderPath == %@", folderId)
        request.fetchLimit = 1

        if let existing = try? context.fetch(request).first {
            return existing
        }

        let library = Library(context: context)
        library.id = UUID()
        library.folderPath = folderId
        library.createdDate = Date()
        return library
    }
}
