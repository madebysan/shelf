import SwiftUI

/// Named animation curves for the entire app.
/// Bounce only on things with perceived "mass" (cards appearing, lists expanding).
/// No bounce on toggles and crossfades.
enum AppAnimation {
    /// Cards/panels growing into view
    static let expand = Animation.spring(duration: 0.4, bounce: 0.18)

    /// Panels collapsing out of view
    static let collapse = Animation.spring(duration: 0.25, bounce: 0.06)

    /// Individual element appearing (rows, empty states)
    static let appear = Animation.spring(duration: 0.35, bounce: 0.12)

    /// Switching between view modes, crossfades
    static let viewSwitch = Animation.easeInOut(duration: 0.2)

    /// Hover states, small toggles
    static let quickToggle = Animation.easeInOut(duration: 0.12)
}
