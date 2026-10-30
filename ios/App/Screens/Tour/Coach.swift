import SwiftUI
import UIKit

/// The guided practice: Dewey and a spotlight over the real screens. A dark
/// layer covers everything but a hole around the one thing to do next; taps
/// outside the hole are swallowed. It lives in its own window above the app,
/// so it also covers sheets. Screens say where things are with `.coachTarget`
/// (practice only); a step ends when the practice model shows it happened.
/// If the target goes missing (another tab, a page pushed over it, a closed
/// sheet, scrolled away), the coach is "lost": it stops blocking and offers
/// "Take me there", which opens the step's tab fresh and rewinds to the step
/// that leads back, so a wrong tap never strands anyone.
@MainActor
@Observable
final class Coach {
    static let shared = Coach()

    struct Step {
        /// The `.coachTarget` key to spotlight; nil = no hole, the bubble in the middle.
        var target: String?
        var text: String
        var hint: Hint = .tap
        /// A button in the bubble moves on (and the hole takes no taps).
        var button: String?
        /// What the button does before moving on (asking iOS for a permission, saving a choice).
        var action: (@MainActor () async -> Void)?
        /// A second, plain button that moves on without `action` ("Not now").
        var other: String?
        /// Moves on by itself once this is true.
        var done: (@MainActor (AppModel) -> Bool)?
        /// The hole spans the screen's width: a list row, whose content slides
        /// aside to show its swipe buttons.
        var wide = false
        /// No dimming and every tap goes through: for a press-and-hold menu or a
        /// back button, which draw outside any hole.
        var free = false
        /// The tab the target lives on, for "Take me there".
        var tab: Int?
        /// How many steps back "Take me there" goes: to the step that opens this one's screen.
        var rewind = 0
    }

    enum Hint { case tap, swipeLeft, none }

    private(set) var steps: [Step] = []
    private(set) var index = 0
    private(set) var running = false
    /// A step was just done: Dewey cheers before the next one.
    private(set) var cheering = false
    /// Where each `.coachTarget` is, in screen points. `tab.N` is the Nth tab
    /// of the practice's tab bar (found in UIKit; SwiftUI doesn't expose it).
    var frames: [String: CGRect] = [:]
    /// Which view set each frame: a list that rebuilds its rows (Plan, after a drop) shows the new
    /// row before the old one goes, and the old one's leaving mustn't clear the new one's frame.
    @ObservationIgnored var owners: [String: UUID] = [:]
    /// Things a screen says happened that the model doesn't hold (`note`), e.g. a what-if score typed.
    private(set) var flags: Set<String> = []
    /// The overlay's own controls (bubble, Skip), which take taps.
    var controls: [String: CGRect] = [:]
    /// The step's target has been off screen for a moment: nothing is blocked, and the bubble offers a way back.
    private(set) var lost = false
    /// Bumped by "Take me there": MainTabs opens `jumpTab` and resets its screens.
    private(set) var jump = 0
    private(set) var jumpTab = 0
    private var lostTask: Task<Void, Never>?

    private weak var model: AppModel?
    private var window: CoachWindow?
    private var onFinish: (() -> Void)?

    var step: Step? { running && index < steps.count ? steps[index] : nil }
    /// The practice model's revision, so the overlay re-checks when a mark changes.
    var revision: Int { model?.revision ?? 0 }

    var hole: CGRect? {
        guard let s = step, let key = s.target else { return nil }
        if key.hasPrefix("tab."), let i = Int(key.dropFirst(4)) { return tabFrame(i)?.insetBy(dx: -2, dy: -2) }
        guard let f = frames[key], f.width > 0 else { return nil }
        if s.wide, let w = window?.bounds.width {
            return CGRect(x: Theme.Space.s, y: f.minY - 6, width: w - 2 * Theme.Space.s, height: f.height + 12)
        }
        return f.insetBy(dx: -8, dy: -6)
    }

    /// The hole's middle is on screen.
    private var onScreen: Bool {
        guard let h = hole, let b = window?.bounds else { return false }
        return b.contains(CGPoint(x: h.midX, y: h.midY))
    }

    /// Notices a missing target (after 1.5 s, so screens can slide in first).
    func watch() {
        guard let s = step, s.target != nil, s.tab != nil, !onScreen else {
            lostTask?.cancel()
            lostTask = nil
            lost = false
            return
        }
        guard lostTask == nil, !lost else { return }
        lostTask = Task {
            try? await Task.sleep(for: .milliseconds(1500))
            guard !Task.isCancelled else { return }
            lostTask = nil
            if !onScreen { lost = true }
        }
    }

    /// "Take me there": back to the step that opens this screen, on its tab, started fresh.
    func recover() {
        guard let s = step, let t = s.tab else { return }
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        index = max(0, index - s.rewind)
        flags = []
        lostTask?.cancel()
        lostTask = nil
        lost = false
        jumpTab = t
        jump += 1
        announce()
        Task {
            try? await Task.sleep(for: .milliseconds(600))
            check()
            watch()
        }
    }

    func start(_ steps: [Step], model: AppModel, from first: Int = 0, onFinish: @escaping () -> Void) {
        self.steps = steps
        self.model = model
        self.onFinish = onFinish
        index = min(max(first, 0), steps.count - 1)
        cheering = false
        lost = false
        flags = []
        running = true
        guard window == nil, let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first(where: { $0.activationState == .foregroundActive })
            ?? UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first else { return }
        let w = CoachWindow(windowScene: scene)
        w.windowLevel = .normal + 1
        w.backgroundColor = .clear
        let host = UIHostingController(rootView: CoachOverlay())
        host.view.backgroundColor = .clear
        w.rootViewController = host
        w.isHidden = false
        window = w
        announce()
    }

    /// A screen reports something the steps wait for.
    func note(_ flag: String) {
        guard running, !flags.contains(flag) else { return }
        flags.insert(flag)
        check()
    }

    /// The Nth tab button of the topmost tab bar (the practice's), in screen points.
    private func tabFrame(_ i: Int) -> CGRect? {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        guard let app = windows.first(where: { $0 !== window && !$0.isHidden && $0.windowLevel == .normal }) else { return nil }
        var vc = app.rootViewController
        while let p = vc?.presentedViewController { vc = p }
        guard let root = vc?.view, let bar = Self.find(UITabBar.self, in: root) else { return nil }
        let f = bar.convert(bar.bounds, to: nil)
        // The tab buttons, left to right; else the bar in equal parts.
        let buttons = Self.all(in: bar).filter { v in
            let n = String(describing: type(of: v))
            return (n.contains("TabBarButton") || n.contains("TabButton")) && v.bounds.width > 20
        }
        .map { $0.convert($0.bounds, to: nil) }
        .sorted { $0.minX < $1.minX }
        if buttons.count == 5, i < 5 { return buttons[i] }
        let w = f.width / 5
        return CGRect(x: f.minX + w * CGFloat(i), y: f.minY, width: w, height: f.height)
    }

    private static func find<T: UIView>(_ type: T.Type, in v: UIView) -> T? {
        if let t = v as? T, !t.isHidden { return t }
        for s in v.subviews { if let t = find(type, in: s) { return t } }
        return nil
    }

    private static func all(in v: UIView) -> [UIView] { v.subviews + v.subviews.flatMap { all(in: $0) } }

    /// Re-checks the current step (after the model or a frame changes).
    func check() {
        guard let s = step, !cheering, let m = model, let done = s.done, done(m) else { return }
        advance()
    }

    /// The bubble's button: its action first (a permission prompt waits for the answer).
    func next() {
        guard let s = step, s.button != nil else { return }
        guard let action = s.action else { move(); return }
        let at = index
        Task {
            await action()
            if running && index == at { move() }
        }
    }

    /// The bubble's second button: on without the action.
    func pass() { if step?.other != nil { move() } }

    private func advance() {
        cheering = true
        Task {
            try? await Task.sleep(for: .milliseconds(900))
            cheering = false
            move()
        }
    }

    private func move() {
        // A what-if box keeps the keyboard up; the next step may be under it.
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        index += 1
        lost = false
        if index >= steps.count { finish() } else { announce(); check(); watch() }
    }

    func finish() {
        guard running else { return }
        running = false
        window?.isHidden = true
        window = nil
        frames = [:]
        owners = [:]
        controls = [:]
        flags = []
        lost = false
        lostTask?.cancel()
        lostTask = nil
        let f = onFinish
        onFinish = nil
        f?()
    }

    private func announce() {
        guard let s = step else { return }
        UIAccessibility.post(notification: .announcement, argument: s.text)
    }
}

/// Passes taps inside the hole through to the app, keeps the overlay's own
/// controls, and swallows the rest. With VoiceOver on, nothing is swallowed.
final class CoachWindow: UIWindow {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let c = Coach.shared
        guard let step = c.step else { return nil }
        if c.controls.values.contains(where: { $0.contains(point) }) { return super.hitTest(point, with: event) }
        if UIAccessibility.isVoiceOverRunning || step.free || c.lost { return nil }
        if step.button == nil, let hole = c.hole, hole.contains(point) { return nil }
        return rootViewController?.view
    }
}

// MARK: - Targets

extension View {
    /// Where the tour's spotlight can point. Does nothing outside practice.
    func coachTarget(_ key: String) -> some View { modifier(CoachTarget(key: key)) }
}

private struct CoachTarget: ViewModifier {
    @Environment(AppModel.self) private var model
    let key: String
    @State private var id = UUID()

    func body(content: Content) -> some View {
        if model.practice {
            content
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: {
                    Coach.shared.frames[key] = $0
                    Coach.shared.owners[key] = id
                }
                .onDisappear {
                    guard Coach.shared.owners[key] == id else { return }
                    Coach.shared.frames[key] = nil
                    Coach.shared.owners[key] = nil
                }
        } else {
            content
        }
    }
}

// MARK: - Overlay

private struct CoachOverlay: View {
    @AppStorage(Theme.appearanceKey) private var appearanceSetting = "system"
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var coach: Coach { Coach.shared }

    var body: some View {
        GeometryReader { geo in
            let hole = coach.hole
            ZStack(alignment: .topLeading) {
                SpotlightShape(hole: hole ?? .zero)
                    .fill(Color.black.opacity(coach.step?.free == true || coach.lost ? 0 : 0.55), style: FillStyle(eoFill: true))
                    .accessibilityHidden(true)
                if let hole, coach.step?.button == nil, coach.step?.hint != Coach.Hint.none, !coach.lost {
                    HintMark(hole: hole, hint: coach.step?.hint ?? .none, reduceMotion: reduceMotion)
                }
                bubble(hole: hole, size: geo.size)
            }
            .animation(reduceMotion ? nil : .spring(duration: 0.45), value: hole)
            .animation(reduceMotion ? nil : .spring(duration: 0.45), value: coach.index)
        }
        .ignoresSafeArea()
        .preferredColorScheme(Theme.colorScheme(appearanceSetting))
        .onChange(of: coach.frames) { coach.check(); coach.watch() }
        .onChange(of: coach.index) { coach.watch() }
        .onChange(of: coach.revision) { coach.check() }
        .onChange(of: coach.flags) { coach.check() }
    }

    @ViewBuilder
    private func bubble(hole: CGRect?, size: CGSize) -> some View {
        if let step = coach.step, (step.free && hole != nil) || coach.lost {
            // A press-and-hold menu opens next to its target, and a lost student needs the
            // screen: keep out of the way, above the tab bar.
            Bubble(step: step, cheering: coach.cheering, lost: coach.lost, reduceMotion: reduceMotion)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { coach.controls["bubble"] = $0 }
                .padding(.horizontal, Theme.Space.l)
                .frame(width: size.width, height: max(0, size.height - 110), alignment: .bottom)
        } else if let step = coach.step {
            let above = (hole?.midY ?? 0) > size.height * 0.5
            Bubble(step: step, cheering: coach.cheering, lost: false, reduceMotion: reduceMotion)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { coach.controls["bubble"] = $0 }
                .padding(.horizontal, Theme.Space.l)
                .frame(width: size.width)
                // Never negative: the first pass can have a zero size, or a hole near an edge.
                .frame(height: max(0, hole.map { above ? $0.minY - 10 : size.height - $0.maxY - 10 } ?? size.height),
                       alignment: hole == nil ? .center : above ? .bottom : .top)
                .offset(y: hole.map { above ? 0 : $0.maxY + 10 } ?? 0)
        }
    }

}

/// The screen with a rounded hole cut out (even-odd fill), animatable so the
/// hole glides from one target to the next.
private struct SpotlightShape: Shape {
    var hole: CGRect

    var animatableData: AnimatablePair<AnimatablePair<CGFloat, CGFloat>, AnimatablePair<CGFloat, CGFloat>> {
        get { .init(.init(hole.origin.x, hole.origin.y), .init(hole.size.width, hole.size.height)) }
        set { hole = CGRect(x: newValue.first.first, y: newValue.first.second, width: newValue.second.first, height: newValue.second.second) }
    }

    func path(in rect: CGRect) -> Path {
        var p = Path(rect)
        if hole.width > 0 { p.addRoundedRect(in: hole, cornerSize: CGSize(width: Theme.Radius.card, height: Theme.Radius.card), style: .continuous) }
        return p
    }
}

/// A pulsing ring for a tap, or a hand sliding left for a swipe.
private struct HintMark: View {
    let hole: CGRect
    let hint: Coach.Hint
    let reduceMotion: Bool
    @State private var on = false

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous)
                .stroke(Theme.accent, lineWidth: 2.5)
                .frame(width: hole.width, height: hole.height)
                .scaleEffect(on && hint == .tap ? 1.04 : 1)
                .opacity(on && hint == .tap ? 0.4 : 1)
                .offset(x: hole.minX, y: hole.minY)
            if hint == .swipeLeft {
                Image(systemName: "hand.point.up.left.fill")
                    .font(.title)
                    .foregroundStyle(.white)
                    .shadow(radius: 3)
                    .offset(x: on ? hole.minX + hole.width * 0.3 : hole.maxX - 44, y: hole.midY - 4)
                    .opacity(on ? 0 : 1)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: hint == .swipeLeft ? 1.2 : 0.9).repeatForever(autoreverses: hint != .swipeLeft)) { on = true }
        }
    }
}

/// Dewey and what he says. He stays still; the text changes when a step is done.
private struct Bubble: View {
    let step: Coach.Step
    let cheering: Bool
    let lost: Bool
    let reduceMotion: Bool

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Space.m) {
            Image("DeweyHello")
                .resizable().scaledToFit()
                .frame(width: 52, height: 52)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Theme.Space.s) {
                if cheering {
                    Label("Done", systemImage: "checkmark.circle.fill")
                        .font(Theme.headline).foregroundStyle(Theme.good)
                } else {
                    Text(step.text).font(Theme.subhead).foregroundStyle(Theme.text)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if lost {
                    Text("Lost your place? I'll take you back.").font(Theme.footnote).foregroundStyle(Theme.secondary)
                    Button("Take me there") { Coach.shared.recover() }
                        .font(Theme.subhead.weight(.semibold))
                        .buttonStyle(.borderedProminent)
                        .buttonBorderShape(.capsule)
                        .foregroundStyle(Theme.onAccent)
                } else if let b = step.button, !cheering {
                    HStack(spacing: Theme.Space.s) {
                        Button(b) { Coach.shared.next() }
                            .font(Theme.subhead.weight(.semibold))
                            .buttonStyle(.borderedProminent)
                            .buttonBorderShape(.capsule)
                            .foregroundStyle(Theme.onAccent)
                        if let o = step.other {
                            Button(o) { Coach.shared.pass() }
                                .font(Theme.subhead.weight(.semibold))
                                .buttonStyle(.bordered)
                                .buttonBorderShape(.capsule)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // Skip lives in the bubble, so it never covers a target.
            Button { Coach.shared.finish() } label: {
                Text("Skip").font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.secondary)
                    .frame(minWidth: 44, minHeight: 32)
            }
            .buttonStyle(.plain)
            .padding(.top, -6).padding(.trailing, -8)
            .accessibilityLabel("Skip tour")
        }
        .padding(Theme.Space.m)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
        .shadow(color: .black.opacity(0.25), radius: 12, y: 4)
        .tint(Theme.accent)
        .accessibilityElement(children: .contain)
    }
}
