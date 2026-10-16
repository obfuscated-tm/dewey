import SwiftUI

/// One colour theme from Settings → Look, with a light and a dark value for every colour
/// (WCAG AA: text ≥ 4.5:1 on every surface; ThemeTests checks every palette).
struct Palette: Identifiable {
    let id: String
    let name: String
    /// Colour name → (light, dark) hex.
    let hex: [String: (light: String, dark: String)]
    /// Themes other than Standard and Midnight bring their own accent, so the accent dots hide.
    var accent: (light: String, dark: String)?

    func color(_ name: String) -> Color { Color(light: hex[name]!.light, dark: hex[name]!.dark) }

    static let names = ["background", "card", "border", "divider", "text", "secondary", "tertiary",
                        "late", "lateSoft", "good", "goodSoft", "tagSoft"]

    /// `light` and `dark` list the colours in the order of `names`.
    init(_ id: String, _ name: String, light: String, dark: String, accent: (light: String, dark: String)? = nil) {
        let l = light.split(separator: " ").map(String.init), d = dark.split(separator: " ").map(String.init)
        precondition(l.count == Self.names.count && d.count == Self.names.count, id)
        self.id = id
        self.name = name
        hex = Dictionary(uniqueKeysWithValues: Self.names.indices.map { (Self.names[$0], (l[$0], d[$0])) })
        self.accent = accent
    }

    private static let standardLight = "#F2F3F5 #FFFFFF #D9DDE2 #E4E7EB #15191D #4F5761 #5F6771 #A63A0B #FBEDE5 #1C6B3F #E5F2EA #E9ECF0"

    static let all: [Palette] = [
        // Light as before; dark is soft graphite instead of pure black.
        Palette("standard", "Standard", light: standardLight,
                dark: "#16171A #212327 #34373D #2C2E33 #ECEDEF #B9BDC4 #9DA2AA #FF9466 #3A2219 #6BD497 #1A3325 #2D3035"),
        // Standard with a true-black dark mode (saves battery on OLED screens).
        Palette("midnight", "Midnight", light: standardLight,
                dark: "#000000 #1C1C1E #38383A #2C2C2E #F2F2F7 #C1C5CC #A3A8B0 #FF8A5C #3A1E12 #5FD08D #14301F #2C2C2E"),
        // Dewey's brand teal.
        Palette("dew", "Dew",
                light: "#EEF6F2 #FFFFFF #CFE3DA #E0EDE7 #0E2A24 #3F5A53 #52685F #A63A0B #FBEDE5 #426E10 #E9F2DC #E1F0E9",
                dark: "#0F1A18 #172623 #27403A #1F332E #E1F5EE #A9C7BD #8FB0A5 #FF9466 #3A2219 #B5DB6E #2B3A17 #21352F",
                accent: ("#0F6E56", "#5DCAA5")),
        // Cool blue-grey, after Nord.
        Palette("frost", "Frost",
                light: "#ECEFF4 #FFFFFF #D3D9E3 #E2E6ED #2E3440 #4C566A #5A6478 #A5402D #F8E9E5 #3B6E3B #E6F0E2 #E5E9F0",
                dark: "#242933 #2E3440 #434C5E #3B4252 #ECEFF4 #C8CFDB #AEB6C4 #EFA08A #4A3434 #B4D98F #354236 #3B4252",
                accent: ("#3F6A9A", "#88C0D0")),
        // Soft lavender, after Catppuccin.
        Palette("pastel", "Pastel",
                light: "#EFF1F5 #FFFFFF #DCE0E8 #E6E9EF #4C4F69 #5C5F77 #626579 #B8203F #FBE6EA #2A6E1B #E5F3E0 #E6E9EF",
                dark: "#1E1E2E #28283B #3E3E55 #34344A #CDD6F4 #BAC2DE #A6ADC8 #F38BA8 #45283A #A6E3A1 #26402F #34344A",
                accent: ("#7032D6", "#CBA6F7")),
        // Warm sepia for reading.
        Palette("paper", "Paper",
                light: "#F4EEE3 #FBF7F0 #E2D8C6 #EAE2D4 #2B2620 #5A5145 #6B6152 #A3380E #F7E4D8 #3E6B1F #E6EDD8 #ECE3D3",
                dark: "#1D1B18 #282521 #3D3832 #34302A #EBE3D5 #C7BCAA #ACA290 #F0956A #3D261B #9FD27F #263821 #35312B",
                accent: ("#9A5B13", "#E0A458")),
    ]

    static func named(_ id: String) -> Palette { all.first { $0.id == id } ?? all[0] }
}

/// The chosen palette and accent. Observable, so every view that reads a `Theme`
/// colour redraws when either changes in Settings.
@Observable final class ThemeStore {
    static let shared = ThemeStore()

    var paletteID: String {
        didSet { UserDefaults.standard.set(paletteID, forKey: Theme.paletteKey); build() }
    }
    var accentHex: String {
        didSet { UserDefaults.standard.set(accentHex, forKey: Theme.accentKey); build() }
    }
    private(set) var colors: [String: Color] = [:]
    var palette: Palette { Palette.named(paletteID) }

    private init() {
        paletteID = UserDefaults.standard.string(forKey: Theme.paletteKey) ?? Palette.all[0].id
        accentHex = UserDefaults.standard.string(forKey: Theme.accentKey) ?? Theme.accentOptions[0]
        build()
    }

    private func build() {
        let p = palette
        var c = Dictionary(uniqueKeysWithValues: Palette.names.map { ($0, p.color($0)) })
        c["accent"] = p.accent.map { Color(light: $0.light, dark: $0.dark) } ?? Accent.color(hex: accentHex)
        colors = c
    }
}

/// The look in docs/ios/SCREENS.md ("Look"). Fonts fall back to SF Pro / SF Mono.
enum Theme {
    static let accentOptions: [String] = ["#1D5EA8", "#23794A", "#6A4BA8", "#A8324A"]
    static let accentKey = "accentHex"
    static let paletteKey = "palette"
    static let appearanceKey = "appearance"

    /// Settings → Look: "system" | "light" | "dark".
    static func colorScheme(_ appearance: String) -> ColorScheme? {
        switch appearance { case "light": .light; case "dark": .dark; default: nil }
    }

    /// The accent for the widgets: the theme's own, or the chosen one.
    static var accentHex: String { ThemeStore.shared.palette.accent?.light ?? ThemeStore.shared.accentHex }
    /// The accent, lighter in dark mode so it stays readable.
    static var accent: Color { c("accent") }
    static var accentSoft: Color { accent.opacity(0.11) }

    private static func c(_ name: String) -> Color { ThemeStore.shared.colors[name]! }
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
