import Foundation

/// Shared file system utilities
enum FileUtils {

    /// Returns true if the file exists on a cloud mount but has no local bytes (0 disk blocks).
    /// Google Drive in "Stream files" mode shows files as regular entries but doesn't
    /// download them until explicitly requested. AVPlayer can't play these.
    static func isCloudOnly(path: String) -> Bool {
        do {
            let attrs = try FileManager.default.attributesOfItem(atPath: path)
            let size = attrs[.size] as? Int64 ?? 0
            guard size > 0 else { return false }

            var s = stat()
            guard stat(path, &s) == 0 else { return false }
            return s.st_blocks == 0
        } catch {
            return false
        }
    }

    /// Triggers the macOS file provider (e.g. Google Drive) to download a cloud-only file.
    /// Uses NSFileCoordinator which is the system-blessed way to request file bytes from
    /// cloud storage FUSE mounts. Blocks until the file is available locally.
    /// - Parameters:
    ///   - url: The file URL to download.
    ///   - timeout: Maximum time to wait for the download (default 5 minutes).
    /// - Throws: If the download fails or times out.
    static func startCloudDownload(url: URL, timeout: TimeInterval = 300) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let queue = DispatchQueue(label: "shelf.cloud-download", qos: .userInitiated)
            let coordinator = NSFileCoordinator(filePresenter: nil)
            let intent = NSFileAccessIntent.readingIntent(with: url, options: .withoutChanges)

            // Timeout: cancel the coordinator if it takes too long
            let workItem = DispatchWorkItem {
                coordinator.cancel()
                continuation.resume(throwing: NSError(
                    domain: "ShelfCloudDownload",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "Download timed out after \(Int(timeout))s"]
                ))
            }
            queue.asyncAfter(deadline: .now() + timeout, execute: workItem)

            coordinator.coordinate(with: [intent], queue: OperationQueue()) { error in
                // Cancel the timeout since we got a response
                workItem.cancel()

                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }
}
