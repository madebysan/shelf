import SwiftUI

@main
struct ShelfApp: App {

    @StateObject private var libraryVM = LibraryViewModel()
    @StateObject private var audioService = AudioPlayerService()
    @StateObject private var playerVM: PlayerViewModel
    @StateObject private var miniPlayerController = MiniPlayerController()

    init() {
        let service = AudioPlayerService()
        _audioService = StateObject(wrappedValue: service)
        _playerVM = StateObject(wrappedValue: PlayerViewModel(audioService: service))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(libraryVM)
                .environmentObject(playerVM)
                .environmentObject(miniPlayerController)
                .frame(minWidth: 800, minHeight: 600)
                .onAppear {
                    // Wire up cross-references
                    playerVM.libraryVM = libraryVM

                    // On first launch (no libraries), prompt to add one
                    if libraryVM.libraries.isEmpty {
                        libraryVM.addLibrary()
                    } else {
                        // Scan the active library
                        Task { await libraryVM.scanLibrary() }
                    }
                }
        }
        .commands {
            // File menu
            CommandGroup(after: .newItem) {
                Button("Refresh Library") {
                    Task { await libraryVM.scanLibrary() }
                }
                .keyboardShortcut("r", modifiers: .command)
                .disabled(libraryVM.isScanning || libraryVM.activeLibrary == nil)

                Button("Add Library...") {
                    libraryVM.addLibrary()
                }
                .keyboardShortcut("o", modifiers: [.command, .shift])

                Divider()

                Button("Export Progress...") {
                    libraryVM.exportProgress()
                }
                .keyboardShortcut("e", modifiers: [.command, .shift])

                Button("Import Progress...") {
                    libraryVM.importProgress()
                }
                .keyboardShortcut("i", modifiers: [.command, .shift])
            }

            // Playback menu
            CommandMenu("Playback") {
                Button("Play/Pause") {
                    playerVM.audioService.togglePlayPause()
                }

                Button("Skip Forward 30s") {
                    playerVM.audioService.skipForward()
                }
                .keyboardShortcut(.rightArrow, modifiers: .command)

                Button("Skip Back 30s") {
                    playerVM.audioService.skipBackward()
                }
                .keyboardShortcut(.leftArrow, modifiers: .command)

                Divider()

                Button("Add Bookmark") {
                    playerVM.showAddBookmark = true
                }
                .keyboardShortcut("b", modifiers: .command)
                .disabled(playerVM.currentBook == nil)

                Divider()

                Button("Mini Player") {
                    miniPlayerController.toggle(playerVM: playerVM)
                }
                .keyboardShortcut("m", modifiers: [.command, .shift])
                .disabled(playerVM.currentBook == nil)

                Divider()

                Menu("Speed") {
                    ForEach(AudioPlayerService.speeds, id: \.self) { speed in
                        Button(speed == Float(Int(speed)) ? "\(Int(speed))x" : String(format: "%.2gx", speed)) {
                            playerVM.audioService.setSpeed(speed)
                        }
                    }
                }
            }
        }

        Settings {
            PreferencesView()
                .environmentObject(libraryVM)
        }
    }
}
