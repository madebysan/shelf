import AppKit
import SwiftUI

/// Manages a floating NSPanel for the mini player — always on top, follows across spaces
@MainActor
class MiniPlayerController: NSObject, ObservableObject, NSWindowDelegate {

    @Published var isVisible: Bool = false

    private var panel: NSPanel?

    /// Shows the mini player floating panel
    func show(playerVM: PlayerViewModel) {
        if let existing = panel {
            existing.makeKeyAndOrderFront(nil)
            isVisible = true
            return
        }

        // Create the SwiftUI view hosted in the panel
        let miniView = MiniPlayerView()
            .environmentObject(playerVM)

        let hostingController = NSHostingController(rootView: miniView)

        // Configure the panel
        let panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: 300, height: 200),
            styleMask: [.titled, .closable, .nonactivatingPanel, .hudWindow],
            backing: .buffered,
            defer: false
        )

        panel.contentViewController = hostingController
        panel.title = "Mini Player"
        panel.titlebarAppearsTransparent = true
        panel.titleVisibility = .hidden
        panel.isFloatingPanel = true
        panel.level = .floating
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        panel.isMovableByWindowBackground = true
        panel.delegate = self
        panel.isReleasedWhenClosed = false

        // Position in the bottom-right of the screen
        if let screen = NSScreen.main {
            let screenFrame = screen.visibleFrame
            let x = screenFrame.maxX - 320
            let y = screenFrame.minY + 20
            panel.setFrameOrigin(NSPoint(x: x, y: y))
        }

        panel.makeKeyAndOrderFront(nil)
        self.panel = panel
        isVisible = true
    }

    /// Hides the mini player panel
    func hide() {
        panel?.orderOut(nil)
        isVisible = false
    }

    /// Toggles the mini player visibility
    func toggle(playerVM: PlayerViewModel) {
        if isVisible {
            hide()
        } else {
            show(playerVM: playerVM)
        }
    }

    // MARK: - NSWindowDelegate

    nonisolated func windowWillClose(_ notification: Notification) {
        Task { @MainActor in
            isVisible = false
        }
    }
}
