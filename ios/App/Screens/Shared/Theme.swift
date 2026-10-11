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
    static var accent: Color {
        let base = UIColor(Color(hex: accentHex))
        return Color(UIColor { t in
            guard t.userInterfaceStyle == .dark else { return base }
            var h: CGFloat = 0, sat: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
            base.getHue(&h, saturation: &sat, brightness: &b, alpha: &a)
            return UIColor(hue: h, saturation: sat * 0.6, brightness: 1, alpha: a)
        })
    }
    static var accentSoft: Color { accent.opacity(0.11) }

    // Every colour has a light and a dark value (WCAG AA: text ≥ 4.5:1 on its card).
    static let background = Color(light: "#F2F3F5", dark: "#000000")
    static let card = Color(light: "#FFFFFF", dark: "#1C1C1E")
    static let border = Color(light: "#D9DDE2", dark: "#38383A")
    static let divider = Color(light: "#E4E7EB", dark: "#2C2C2E")
    static let text = Color(light: "#15191D", dark: "#F2F2F7")
    static let secondary = Color(light: "#4F5761", dark: "#C1C5CC")
    static let tertiary = Color(light: "#5F6771", dark: "#A3A8B0")
    static let late = Color(light: "#A63A0B", dark: "#FF8A5C")
    static let lateSoft = Color(light: "#FBEDE5", dark: "#3A1E12")
    static let good = Color(light: "#1C6B3F", dark: "#5FD08D")
    static let goodSoft = Color(light: "#E5F2EA", dark: "#14301F")
    static let tagSoft = Color(light: "#E9ECF0", dark: "#2C2C2E")

    /// Scales with Dynamic Type (text size in Settings).
    static func font(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .system(size: UIFontMetrics.default.scaledValue(for: size), weight: weight)
    }

    /// Text on a filled accent button: white in light mode, near-black in dark (accent is light there).
    static let onAccent = Color(light: "#FFFFFF", dark: "#101418")

    static func mono(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .system(size: UIFontMetrics.default.scaledValue(for: size), weight: weight, design: .monospaced)
    }
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
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
    }
}

extension View {
    func card() -> some View { modifier(CardStyle()) }
}

/// HW / TEST / TASK / CW.
struct TypeTag: View {
    let type: ItemType
    var body: some View {
        Text(type.label)
            .font(Theme.mono(10.5, .semibold))
            .tracking(0.4)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .foregroundStyle(type == .exam ? Theme.card : Theme.secondary)
            .background(type == .exam ? Theme.text : Theme.tagSoft, in: RoundedRectangle(cornerRadius: 4))
    }
}

/// A pill button ("Submit ↗").
struct PillButton: View {
    let title: String
    var color: Color = Theme.accent
    var filled = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Theme.font(13, .semibold))
                .padding(.horizontal, 12).frame(minHeight: 30)
                .foregroundStyle(filled ? Theme.onAccent : color)
                .background(filled ? color : color.opacity(0.1), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// A full-width 44 pt button.
struct WideButton: View {
    let title: String
    var systemImage: String?
    var filled = true
    var color: Color = Theme.accent
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title)
            }
            .font(Theme.font(16, .semibold))
            .frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(filled ? Theme.onAccent : color)
            .background(filled ? color : Theme.card, in: RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).stroke(filled ? Color.clear : Theme.border))
        }
        .buttonStyle(.plain)
    }
}
