import SwiftUI

extension AnyTransition {
    /// Slide down from top with fade — for banners, alerts
    static var slideAndFade: AnyTransition {
        .asymmetric(
            insertion: .opacity.combined(with: .move(edge: .top)),
            removal: .opacity.combined(with: .move(edge: .top))
        )
    }

    /// Slight scale-up with fade — for expanding sections (bookmarks, chapters)
    static var expandSection: AnyTransition {
        .asymmetric(
            insertion: .opacity.combined(with: .scale(scale: 0.96, anchor: .top)),
            removal: .opacity.combined(with: .scale(scale: 0.96, anchor: .top))
        )
    }

    /// Centered scale + fade — for empty states
    static var emptyState: AnyTransition {
        .opacity.combined(with: .scale(scale: 0.92))
    }
}
