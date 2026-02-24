import SwiftUI

// MARK: - Staggered Appear

/// Animates a view in with a delay based on its index in a list.
/// Each item gets ~30ms of additional delay, capped at 15 items to avoid
/// the tail end of a long list feeling sluggish.
struct StaggeredAppear: ViewModifier {
    let index: Int
    @State private var isVisible = false

    private var delay: Double {
        Double(min(index, 15)) * 0.03
    }

    func body(content: Content) -> some View {
        content
            .opacity(isVisible ? 1 : 0)
            .offset(y: isVisible ? 0 : 8)
            .onAppear {
                withAnimation(AppAnimation.appear.delay(delay)) {
                    isVisible = true
                }
            }
    }
}

// MARK: - Empty State Appear

/// Fade + scale for empty-state views so they don't just snap in.
struct EmptyStateAppear: ViewModifier {
    @State private var isVisible = false

    func body(content: Content) -> some View {
        content
            .opacity(isVisible ? 1 : 0)
            .scaleEffect(isVisible ? 1 : 0.92)
            .onAppear {
                withAnimation(AppAnimation.appear) {
                    isVisible = true
                }
            }
    }
}

// MARK: - Convenience extensions

extension View {
    /// Apply staggered entrance animation based on list index
    func staggeredAppear(index: Int) -> some View {
        modifier(StaggeredAppear(index: index))
    }

    /// Apply fade+scale entrance for empty states
    func emptyStateAppear() -> some View {
        modifier(EmptyStateAppear())
    }
}
