import SwiftUI

/// Dewey's one palette, Dew (the brand teal), with a light and a dark value for every colour
/// (WCAG AA: text ≥ 4.5:1 on every surface; ThemeTests checks). Settings → Appearance
/// picks light, dark or the system's.
struct Palette {
    /// Colour name → (light, dark) hex.
    let hex: [String: (light: String, dark: String)]

    func color(_ name: String) -> Color { Color(light: hex[name]!.light, dark: hex[name]!.dark) }

    static let names = ["background", "card", "border", "divider", "text", "secondary", "tertiary",
                        "late", "lateSoft", "good", "goodSoft", "tagSoft", "accent"]

    /// `light` and `dark` list the colours in the order of `names`.
    init(light: String, dark: String) {
        let l = light.split(separator: " ").map(String.init), d = dark.split(separator: " ").map(String.init)
        precondition(l.count == Self.names.count && d.count == Self.names.count)
        hex = Dictionary(uniqueKeysWithValues: Self.names.indices.map { (Self.names[$0], (l[$0], d[$0])) })
    }

    static let dew = Palette(
        light: "#EEF6F2 #FFFFFF #CFE3DA #E0EDE7 #0E2A24 #3F5A53 #52685F #A63A0B #FBEDE5 #426E10 #E9F2DC #E1F0E9 #0F6E56",
        dark: "#0F1A18 #172623 #27403A #1F332E #E1F5EE #A9C7BD #8FB0A5 #FF9466 #3A2219 #B5DB6E #2B3A17 #21352F #5DCAA5")

    static let colors = Dictionary(uniqueKeysWithValues: names.map { ($0, dew.color($0)) })
}

/// The look in docs/ios/SCREENS.md ("Look"). Fonts fall back to SF Pro / SF Mono.
enum Theme {
    static let appearanceKey = "appearance"

    /// Settings → Appearance: "system" | "light" | "dark".
    static func colorScheme(_ appearance: String) -> ColorScheme? {
        switch appearance { case "light": .light; case "dark": .dark; default: nil }
    }

    /// The accent for the widgets (they lighten it in dark mode themselves).
    static let accentHex = Palette.dew.hex["accent"]!.light
    /// The accent, lighter in dark mode so it stays readable.
    static var accent: Color { c("accent") }
    static var accentSoft: Color { accent.opacity(0.11) }

    private static func c(_ name: String) -> Color { Palette.colors[name]! }
    static var background: Color { c("background") }
    static var card: Color { c("card") }
    static var border: Color { c("border") }
    static var divider: Color { c("divider") }
    static var text: Color { c("text") }
    static var secondary: Color { c("secondary") }
    static var tertiary: Color { c("tertiary") }
    static var late: Color { c("late") }
    static var lateSoft: Color { c("lateSoft") }
    static var good: Color { c("good") }
    static var goodSoft: Color { c("goodSoft") }
    static var tagSoft: Color { c("tagSoft") }

    // ── Type scale (SCREENS.md "Look") ──────────────────────────────────
    // Seven styles, all SF Pro on Apple's text styles, so they follow Dynamic
    // Type. Screens pick one of these and never a point size (ThemeTests checks).
    // Numbers add `.monospacedDigit()` so digits line up without a code font.
    // Plus two display styles in New York (the system serif), the one place the
    // type has character: big screen titles and the big grade letter, nothing else.

    /// 34 bold New York · screen titles (ScreenHeader, large nav titles), the big grade letter.
    static let display = Font.system(.largeTitle, design: .serif, weight: .bold)
    /// 22 bold New York · the grade letter on a Grades card.
    static let displaySmall = Font.system(.title2, design: .serif, weight: .bold)

    /// 34 bold · screen titles.
    static let largeTitle = Font.system(.largeTitle, weight: .bold)
    /// 22 bold · sections inside a screen, big numbers.
    static let title = Font.system(.title2, weight: .bold)
    /// 17 semibold · class headers, card titles.
    static let headline = Font.system(.headline)
    /// 17 · row titles, reading text.
    static let body = Font.system(.body)
    /// 15 · dates, scores, buttons.
    static let subhead = Font.system(.subheadline)
    /// 13 · status, counts, hints.
    static let footnote = Font.system(.footnote)
    /// 12 · tags and kickers only.
    static let caption = Font.system(.caption)
    /// Large navigation-bar titles (Classes) in `display`, set once at launch.
    static func styleNavigationBars() {
        let base = UIFont.preferredFont(forTextStyle: .largeTitle)
        guard let d = base.fontDescriptor.withDesign(.serif)?.withSymbolicTraits(.traitBold) else { return }
        UINavigationBar.appearance().largeTitleTextAttributes = [.font: UIFont(descriptor: d, size: 0)]
    }
    /// 17 bold New York · the Dewey number on a To Do catalog card.
    static let callNumber = Font.system(.headline, design: .serif, weight: .bold)
    /// A To Do catalog card's red rule under its heading, and its pale blue ruled lines.
    static let catalogRule = Color(light: "#C94B4B", dark: "#D9706B")
    static let catalogLine = Color(light: "#C9DDF0", dark: "#2E4258")
    /// 10 heavy · the word on a shelf book's bookmark ribbon only (like the type bar's
    /// label: a fixed size so the ribbon stays thinner than the spine's name).
    static let ribbon = Font.system(size: 10, weight: .heavy)
    /// Settings → Developer only: raw engine JSON. Never in the student-facing UI.
    static let code = Font.system(.caption, design: .monospaced)

    // ── Shapes and spacing ───────────────────────────────────────────────
    enum Radius {
        /// Tags, type bars, thumbnails.
        static let tag: CGFloat = 6
        /// Every card, sheet and field. Buttons are capsules, checkboxes circles.
        static let card: CGFloat = 14
    }
    enum Space {
        static let xs: CGFloat = 4, s: CGFloat = 8, m: CGFloat = 12, l: CGFloat = 16, xl: CGFloat = 24
    }

    /// Each class's own colour, in Course Dashboard order: the Classes shelf, the
    /// Grades letters, the catalog line. White text reads on every one (ThemeTests).
    static let classColors = ["#1D5EA8", "#23794A", "#6A4BA8", "#A8324A", "#9A5B0C", "#16707A", "#5E6670", "#8A4B1D"]
    /// For text and marks: lightened in dark mode, like the accent.
    static func classColor(_ i: Int) -> Color { Accent.color(hex: classColors[i % classColors.count]) }
    /// For a book spine under white text: the same in light and dark.
    static func spineColor(_ i: Int) -> Color { Color(hex: classColors[i % classColors.count]) }

    /// Text on a filled accent button: white in light mode, near-black in dark (accent is light there).
    static let onAccent = Color(light: "#FFFFFF", dark: "#101418")
}

extension Color {
    init(light: String, dark: String) {
        let l = UIColor(Color(hex: light)), d = UIColor(Color(hex: dark))
        self.init(UIColor { $0.userInterfaceStyle == .dark ? d : l })
    }

    init(hex: String) {
        let s = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        let v = UInt64(s, radix: 16) ?? 0
        self.init(.sRGB, red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
                  blue: Double(v & 0xFF) / 255, opacity: 1)
    }
}

/// A white rounded card with the border colour.
struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous).stroke(Theme.border))
    }
}

extension View {
    func card() -> some View { modifier(CardStyle()) }
    /// At most `n` lines, but no limit at the accessibility text sizes so nothing gets cut off.
    func maxLines(_ n: Int) -> some View { modifier(MaxLines(n: n)) }
    func oneLine() -> some View { maxLines(1) }
}

struct MaxLines: ViewModifier {
    let n: Int
    @Environment(\.dynamicTypeSize) private var size
    func body(content: Content) -> some View {
        content.lineLimit(size.isAccessibilitySize ? nil : n)
    }
}

/// An HStack that becomes a leading VStack at the accessibility text sizes.
struct AdaptiveStack<Content: View>: View {
    var spacing: CGFloat = 8
    @ViewBuilder let content: () -> Content
    @Environment(\.dynamicTypeSize) private var size

    var body: some View {
        let layout = size.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: spacing))
            : AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: spacing))
        layout { content() }
    }
}

/// HW / TEST / TASK / CW, as a vertical bar under the To Do checkbox.
/// A test is the one that stands out: black instead of grey.
struct TypeBar: View {
    let type: ItemType
    /// The tallest the label needs; the bar stretches to fill a taller row.
    static let minHeight: CGFloat = 38
    var body: some View {
        Text(type.label)
            .font(.system(size: 10, weight: .heavy))
            .tracking(0.6)
            .fixedSize()
            .rotationEffect(.degrees(-90))
            .frame(width: 18, height: Self.minHeight)
            .frame(maxHeight: .infinity)
            .foregroundStyle(type == .exam ? Theme.card : Theme.secondary)
            .background(type == .exam ? Theme.text : Theme.tagSoft,
                        in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
            .accessibilityLabel(type.label)
    }
}

/// HW / TEST / TASK / CW, inline.
struct TypeTag: View {
    let type: ItemType
    var body: some View {
        Text(type.label)
            .font(Theme.caption.weight(.semibold))
            .tracking(0.4)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .foregroundStyle(type == .exam ? Theme.card : Theme.secondary)
            .background(type == .exam ? Theme.text : Theme.tagSoft,
                        in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
    }
}

/// A pill button. `external` adds the ↗ arrow for anything that leaves the app
/// (an SF Symbol: the ↗ character turns into an emoji on iOS).
struct PillButton: View {
    let title: String
    var color: Color = Theme.accent
    var filled = false
    var external = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 3) {
                Text(title)
                if external { Image(systemName: "arrow.up.right").imageScale(.small).fontWeight(.bold) }
            }
            .font(Theme.footnote.weight(.semibold))
            .padding(.horizontal, Theme.Space.m).frame(minHeight: 30)
            .foregroundStyle(filled ? Theme.onAccent : color)
            .background(filled ? color : color.opacity(0.1), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// A full-width capsule button, 48 pt tall.
struct WideButton: View {
    let title: String
    var systemImage: String?
    var filled = true
    var color: Color = Theme.accent
    var external = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title)
                if external { Image(systemName: "arrow.up.right").imageScale(.small) }
            }
            .font(Theme.headline)
            .frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(filled ? Theme.onAccent : color)
            .background(filled ? color : Theme.card, in: Capsule())
            .overlay(Capsule().stroke(filled ? Color.clear : Theme.border))
        }
        .buttonStyle(.plain)
    }
}
