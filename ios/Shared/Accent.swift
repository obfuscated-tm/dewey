import SwiftUI

/// The student's accent colour, for the app and the widgets: as chosen in light
/// mode, lightened in dark mode so it stays readable on a dark background.
enum Accent {
    static func color(hex: String) -> Color {
        let v = UInt64(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0
        let base = UIColor(red: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255, blue: CGFloat(v & 0xFF) / 255, alpha: 1)
        return Color(UIColor { $0.userInterfaceStyle == .dark ? dark(base) : base })
    }

    /// Less saturated, full brightness.
    static func dark(_ base: UIColor) -> UIColor {
        var h: CGFloat = 0, sat: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        base.getHue(&h, saturation: &sat, brightness: &b, alpha: &a)
        return UIColor(hue: h, saturation: sat * 0.6, brightness: 1, alpha: a)
    }
}
