import SwiftUI

/// Dewey's one signature motion: when a checkbox turns on (Done, or Studied for a test),
/// two thin accent rings spread out from it, like the ripples around the logo's drop.
/// About 400 ms, only on marking done (never on undo), and not at all with Reduce Motion.
struct DoneRipple: ViewModifier {
    let isOn: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var wave = 0

    func body(content: Content) -> some View {
        // Read here: the animator's closure isn't on the main actor.
        let rested = wave == 0
        return content
            .background {
                Color.clear
                    .keyframeAnimator(initialValue: 1.0, trigger: wave) { view, p in
                        view.overlay { RippleRings(p: rested ? 1 : p) }
                    } keyframes: { _ in
                        MoveKeyframe(0.0)
                        CubicKeyframe(1.0, duration: 0.42)
                    }
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
            .onChange(of: isOn) { was, now in
                if now && !was && !reduceMotion { wave += 1 }
            }
    }
}

/// The rings at progress `p` (0 → 1): an outer one leads, an inner one follows a beat later.
private struct RippleRings: View {
    let p: Double

    var body: some View {
        let inner = max(0, (p - 0.22) / 0.78)
        ZStack {
            ring(scale: 1 + 1.15 * p, opacity: 0.55 * (1 - p), width: 2 - p)
            ring(scale: 1 + 0.7 * inner, opacity: 0.4 * (1 - inner), width: 1.6 - inner * 0.8)
        }
    }

    private func ring(scale: Double, opacity: Double, width: Double) -> some View {
        Circle().stroke(Theme.accent, lineWidth: width)
            .scaleEffect(scale)
            .opacity(opacity)
    }
}

extension View {
    /// Plays the done ripple when `isOn` turns true.
    func doneRipple(_ isOn: Bool) -> some View { modifier(DoneRipple(isOn: isOn)) }
}
