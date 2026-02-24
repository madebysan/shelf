import SwiftUI

/// Main window layout: sidebar + library grid, with now-playing bar at the bottom
struct ContentView: View {
    @EnvironmentObject var libraryVM: LibraryViewModel
    @EnvironmentObject var playerVM: PlayerViewModel

    @State private var showPlayer: Bool = false
    @State private var keyMonitor: Any?

    var body: some View {
        NavigationSplitView {
            SidebarView()
        } detail: {
            VStack(spacing: 0) {
                // Library grid takes most of the space
                LibraryGridView()

                // Now playing bar at the bottom (when a book is loaded)
                if playerVM.currentBook != nil {
                    Divider()
                    NowPlayingBar(showPlayer: $showPlayer)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
        .sheet(isPresented: $showPlayer) {
            PlayerView()
                .environmentObject(playerVM)
                .frame(minWidth: 500, idealWidth: 600, minHeight: 520, idealHeight: 600)
        }
        .onAppear {
            setupSpacebarMonitor()
        }
        .onChange(of: libraryVM.filteredBooks.count) { _, newCount in
            NSApp.mainWindow?.subtitle = newCount > 0 ? "\(newCount) books" : ""
        }
        .onDisappear {
            if let monitor = keyMonitor {
                NSEvent.removeMonitor(monitor)
                keyMonitor = nil
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.willTerminateNotification)) { _ in
            // Save position when the app quits
            playerVM.audioService.stop()
        }
        // Show an alert when playback fails (e.g., cloud file unreachable)
        .alert("Playback Error", isPresented: playbackErrorBinding) {
            Button("OK") {
                playerVM.audioService.playbackError = nil
            }
        } message: {
            Text(playerVM.audioService.playbackError ?? "An unknown error occurred.")
        }
    }

    /// Binding that converts the optional error string into a Bool for .alert()
    private var playbackErrorBinding: Binding<Bool> {
        Binding(
            get: { playerVM.audioService.playbackError != nil },
            set: { if !$0 { playerVM.audioService.playbackError = nil } }
        )
    }

    /// Intercepts spacebar globally within the app window for play/pause.
    /// Ignores it when a text field has focus so search still works.
    private func setupSpacebarMonitor() {
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            // Space key = keyCode 49
            guard event.keyCode == 49 else { return event }

            // Don't intercept if a text field is focused (e.g., the search bar)
            if let responder = event.window?.firstResponder,
               responder is NSTextView || responder is NSTextField {
                return event
            }

            // Toggle play/pause if a book is loaded
            if playerVM.currentBook != nil {
                playerVM.audioService.togglePlayPause()
                return nil // consume the event
            }

            return event
        }
    }
}
