import SwiftUI

/// The look in docs/ios/SCREENS.md ("Look"). Fonts fall back to SF Pro / SF Mono.
enum Theme {
    static let accentOptions: [String] = ["#1D5EA8", "#23794A", "#6A4BA8", "#A8324A"]
    static let accentKey = "accentHex"
    static let appearanceKey = "appearance"

    /// Settings → Look: "system" | "light" | "dark".
    static func colorScheme(_ appearance: String) -> ColorScheme? {
        switch appearance { case "light": .light; case "dark": .dark; default: nil }
    }

    static var accentHex: String { UserDefaults.standard.string(forKey: accentKey) ?? accentOptions[0] }
    /// The chosen accent, lightened in dark mode so it stays readable.
    static var accent: Color { Accent.color(hex: accentHex) }
    static var accentSoft: Color { accent.opacity(0.11) }

    // Every colour has a light and a dark value (WCAG AA: text ≥ 4.5:1 on its card;
    // ThemeTests checks every text colour against every surface).
    static let hex: [String: (light: String, dark: String)] = [
        "background": ("#F2F3F5", "#000000"),
        "card": ("#FFFFFF", "#1C1C1E"),
        "border": ("#D9DDE2", "#38383A"),
        "divider": ("#E4E7EB", "#2C2C2E"),
        "text": ("#15191D", "#F2F2F7"),
        "secondary": ("#4F5761", "#C1C5CC"),
        "tertiary": ("#5F6771", "#A3A8B0"),
        "late": ("#A63A0B", "#FF8A5C"),
        "lateSoft": ("#FBEDE5", "#3A1E12"),
        "good": ("#1C6B3F", "#5FD08D"),
        "goodSoft": ("#E5F2EA", "#14301F"),
        "tagSoft": ("#E9ECF0", "#2C2C2E"),
    ]
    private static func pair(_ name: String) -> Color { Color(light: hex[name]!.light, dark: hex[name]!.dark) }
    static let background = pair("background")
    static let card = pair("card")
    static let border = pair("border")
    static let divider = pair("divider")
    static let text = pair("text")
    static let secondary = pair("secondary")
    static let tertiary = pair("tertiary")
    static let late = pair("late")
    static let lateSoft = pair("lateSoft")
    static let good = pair("good")
    static let goodSoft = pair("goodSoft")
    static let tagSoft = pair("tagSoft")

    // ── Type scale (SCREENS.md "Look") ──────────────────────────────────
    // Seven styles, all SF Pro on Apple's text styles, so they follow Dynamic
    // Type. Screens pick one of these and never a point size (ThemeTests checks).
    // Numbers add `.monospacedDigit()` so digits line up without a code font.

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
