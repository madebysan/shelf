import SwiftUI

/// Centralized color palette for the app.
/// All views should reference these instead of hardcoding colors inline.
enum AppColors {
    // MARK: - Accent & Brand
    static let accent = Color.accentColor
    static let accentSoft = Color.accentColor.opacity(0.15)

    // MARK: - Surface & Background
    static let secondaryFill = Color(.secondarySystemFill)
    static let materialOverlay = Color.black.opacity(0.6)

    // MARK: - Status
    static let downloaded = Color.green
    static let destructive = Color.red
    static let warning = Color.orange

    // MARK: - Progress
    static let progressTrack = Color.secondary.opacity(0.2)
    static let progressFill = Color.accentColor

    // MARK: - Scrubber / Drag Handle
    static let dragHandle = Color.secondary.opacity(0.5)
}
