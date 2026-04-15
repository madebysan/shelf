import Foundation
import AVFoundation
import MediaPlayer
import AppKit

/// Manages audio playback using AVPlayer with media key and Now Playing integration
@MainActor
class AudioPlayerService: ObservableObject {

    // MARK: - Published State

    @Published var isPlaying: Bool = false
    @Published var currentTime: Double = 0
    @Published var duration: Double = 0
    @Published var playbackRate: Float = 1.0
    @Published var playbackError: String?

    // MARK: - Private

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var statusObservation: NSKeyValueObservation?
    private var currentBook: Book?
    private var saveTimer: Timer?

    // Available playback speeds
    static let speeds: [Float] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]

    // MARK: - Lifecycle

    init() {
        setupRemoteCommands()
    }

    // Cleanup is done via stop() called on app termination

    // MARK: - Playback Controls

    /// Loads and plays a book from its saved position
    func play(book: Book) {
        guard let path = book.filePath else { return }
        let url = URL(fileURLWithPath: path)

        // Clear any previous error
        playbackError = nil

        // Check that the file is reachable (catches Drive not mounted, file moved, etc.)
        if !FileManager.default.isReadableFile(atPath: path) {
            playbackError = "Cannot access \"\(url.lastPathComponent)\". The file may be unavailable — check that the folder is accessible and try again."
            return
        }

        // If switching books, save the current one's position first
        if let current = currentBook, current.objectID != book.objectID {
            savePosition()
        }

        currentBook = book

        // Create a new player item
        let item = AVPlayerItem(url: url)

        if player == nil {
            player = AVPlayer(playerItem: item)
        } else {
            player?.replaceCurrentItem(with: item)
        }

        // Observe player item status for load errors (e.g., cloud file can't buffer)
        observePlayerItemStatus(item)

        // Observe time updates
        setupTimeObserver()

        // Load duration then start playback once the asset is ready
        let savedPosition = book.playbackPosition
        let rate = playbackRate
        Task {
            // Wait for the asset's duration to load (also confirms the file is readable)
            do {
                let loadedDuration = try await item.asset.load(.duration)
                let secs = CMTimeGetSeconds(loadedDuration)
                if secs.isFinite {
                    self.duration = secs
                }
            } catch {
                self.playbackError = "Could not load \"\(url.lastPathComponent)\": \(error.localizedDescription)"
                return
            }

            // Seek to saved position if needed
            if savedPosition > 0 {
                let seekTime = CMTime(seconds: savedPosition, preferredTimescale: 600)
                await player?.seek(to: seekTime)
            }

            // Start playback
            player?.play()
            if rate != 1.0 {
                player?.rate = rate
            }
        }

        isPlaying = true

        // Start periodic save (every 30 seconds)
        startSaveTimer()

        // Update Now Playing info
        updateNowPlayingInfo()

        // Update last played date
        book.lastPlayedDate = Date()
        PersistenceController.shared.save()
    }

    /// Toggles play/pause
    func togglePlayPause() {
        guard let player = player else { return }

        if isPlaying {
            player.pause()
            isPlaying = false
            savePosition()
        } else {
            player.rate = playbackRate
            isPlaying = true
            startSaveTimer()
        }
        updateNowPlayingInfo()
    }

    /// Pauses playback and saves position
    func pause() {
        player?.pause()
        isPlaying = false
        savePosition()
        updateNowPlayingInfo()
    }

    /// Skips forward by the given number of seconds
    func skipForward(_ seconds: Double = 30) {
        guard let player = player else { return }
        let target = min(currentTime + seconds, duration)
        let time = CMTime(seconds: target, preferredTimescale: 600)
        player.seek(to: time)
        updateNowPlayingInfo()
    }

    /// Skips backward by the given number of seconds
    func skipBackward(_ seconds: Double = 30) {
        guard let player = player else { return }
        let target = max(currentTime - seconds, 0)
        let time = CMTime(seconds: target, preferredTimescale: 600)
        player.seek(to: time)
        updateNowPlayingInfo()
    }

    /// Seeks to a specific time
    func seek(to seconds: Double) {
        guard let player = player else { return }
        let time = CMTime(seconds: seconds, preferredTimescale: 600)
        player.seek(to: time)
        updateNowPlayingInfo()
    }

    /// Sets the playback speed
    func setSpeed(_ speed: Float) {
        playbackRate = speed
        if isPlaying {
            player?.rate = speed
        }
        updateNowPlayingInfo()
    }

    /// Saves current position and stops playback (called on app quit)
    func stop() {
        savePosition()
        player?.pause()
        isPlaying = false
        removeTimeObserver()
        saveTimer?.invalidate()
    }

    /// The currently loaded book
    var activeBook: Book? { currentBook }

    // MARK: - Position Persistence

    /// When true, playback position changes won't be saved (used by discover mode)
    var skipPositionSave: Bool = false

    /// Saves the current playback position to Core Data
    func savePosition() {
        guard !skipPositionSave, let book = currentBook else { return }
        book.playbackPosition = currentTime
        PersistenceController.shared.save()
    }

    // MARK: - Player Item Status

    /// Observes the player item's status to catch errors during buffering/playback
    /// (e.g., cloud file connection lost mid-stream)
    private func observePlayerItemStatus(_ item: AVPlayerItem) {
        statusObservation?.invalidate()
        statusObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                if item.status == .failed, let error = item.error {
                    self?.playbackError = "Playback failed: \(error.localizedDescription)"
                    self?.isPlaying = false
                }
            }
        }
    }

    // MARK: - Time Observation

    private func setupTimeObserver() {
        removeTimeObserver()

        // Update currentTime every 0.5 seconds
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserver = player?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self else { return }
            let seconds = CMTimeGetSeconds(time)
            if seconds.isFinite {
                Task { @MainActor in
                    self.currentTime = seconds
                }
            }
        }
    }

    private func removeTimeObserver() {
        if let observer = timeObserver {
            player?.removeTimeObserver(observer)
            timeObserver = nil
        }
    }

    // MARK: - Periodic Save

    private func startSaveTimer() {
        saveTimer?.invalidate()
        saveTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.savePosition()
            }
        }
    }

    // MARK: - Media Keys & Now Playing

    /// Sets up media key handling via MPRemoteCommandCenter
    private func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }
                if !self.isPlaying { self.togglePlayPause() }
            }
            return .success
        }

        commandCenter.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }
                if self.isPlaying { self.togglePlayPause() }
            }
            return .success
        }

        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                self?.togglePlayPause()
            }
            return .success
        }

        commandCenter.skipForwardCommand.preferredIntervals = [30]
        commandCenter.skipForwardCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                self?.skipForward()
            }
            return .success
        }

        commandCenter.skipBackwardCommand.preferredIntervals = [30]
        commandCenter.skipBackwardCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                self?.skipBackward()
            }
            return .success
        }

        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let posEvent = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in
                self?.seek(to: posEvent.positionTime)
            }
            return .success
        }
    }

    /// Updates the macOS Now Playing widget info
    private func updateNowPlayingInfo() {
        var info = [String: Any]()
        info[MPMediaItemPropertyTitle] = currentBook?.displayTitle ?? "Audiobook"
        info[MPMediaItemPropertyArtist] = currentBook?.displayAuthor ?? ""
        info[MPMediaItemPropertyPlaybackDuration] = duration
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? playbackRate : 0.0

        // Set cover art
        if let data = currentBook?.coverArtData, let image = NSImage(data: data) {
            let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            info[MPMediaItemPropertyArtwork] = artwork
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}
