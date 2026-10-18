import SwiftUI

/// The drop from Dewey's logo, point up: the selected day in the week strip and
/// the centre of the ripple. Fills its frame; the round part sits at the bottom.
struct DropShape: Shape {
    func path(in rect: CGRect) -> Path {
        let r = min(rect.width / 2, rect.height * 0.38)
        let c = CGPoint(x: rect.midX, y: rect.maxY - r)
        var p = Path()
        p.move(to: CGPoint(x: rect.midX, y: rect.minY))
        p.addCurve(to: CGPoint(x: c.x + r, y: c.y),
                   control1: CGPoint(x: rect.midX + r * 0.35, y: rect.minY + (c.y - rect.minY) * 0.35),
                   control2: CGPoint(x: c.x + r, y: c.y - r * 0.75))
        p.addArc(center: c, radius: r, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
        p.addCurve(to: CGPoint(x: rect.midX, y: rect.minY),
                   control1: CGPoint(x: c.x - r, y: c.y - r * 0.75),
                   control2: CGPoint(x: rect.midX - r * 0.35, y: rect.minY + (c.y - rect.minY) * 0.35))
        p.closeSubpath()
        return p
    }
}

/// "1 of 3 done today": a drop with a ripple ring per thing due today (at most
/// four), filling from the centre out as they're done.
struct RippleProgress: View {
    let done: Int
    let total: Int
    @ScaledMetric(relativeTo: .footnote) private var size: CGFloat = 40

    var body: some View {
        let rings = min(total, 4)
        let filled = done >= total ? rings : Int(Double(done) / Double(max(total, 1)) * Double(rings))
        HStack(spacing: Theme.Space.s) {
            ZStack {
                ForEach(0..<rings, id: \.self) { i in
                    let d = size * (0.42 + 0.58 * CGFloat(i + 1) / CGFloat(max(rings, 1)))
                    Circle().stroke(i < filled ? Theme.accent : Theme.divider, lineWidth: 2.5).frame(width: d - 2.5, height: d - 2.5)
                }
                DropShape().fill(done >= total ? Theme.accent : Theme.accent.opacity(0.75))
                    .frame(width: size * 0.2, height: size * 0.26)
            }
            .frame(width: size, height: size)
            VStack(alignment: .leading, spacing: 0) {
                Text("\(done) of \(total)").font(Theme.footnote.weight(.bold).monospacedDigit()).foregroundStyle(Theme.text)
                Text("done today").font(Theme.footnote).foregroundStyle(Theme.secondary)
            }
            .fixedSize()
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(done) of \(total) done today")
    }
}
