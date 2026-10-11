import SwiftUI

// 4c · the states every screen needs.

/// First load: grey skeleton rows.
struct SkeletonRows: View {
    var count = 5
    var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<count, id: \.self) { i in
                HStack(spacing: 12) {
                    Circle().fill(Theme.divider).frame(width: 22, height: 22)
                    VStack(alignment: .leading, spacing: 8) {
                        RoundedRectangle(cornerRadius: 4).fill(Theme.divider).frame(width: CGFloat(160 + (i * 37) % 110), height: 13)
                        RoundedRectangle(cornerRadius: 4).fill(Theme.divider.opacity(0.7)).frame(width: 110, height: 10)
                    }
                    Spacer()
                }
                .padding(16)
                .overlay(alignment: .top) { if i > 0 { Theme.divider.frame(height: 1) } }
            }
        }
        .card()
        .redacted(reason: .placeholder)
    }
}

/// Offline / stale: "Offline · as of 12 min ago".
struct AsOfBanner: View {
    let date: Date?
    var error: EngineError?
    var body: some View {
        if let date, error != nil {
            HStack(spacing: 6) {
                Image(systemName: error == .rateLimit ? "tortoise" : "wifi.slash")
                Text("\(error == .rateLimit ? "Slowed down" : "Offline") · as of \(Fmt.ago(date))")
            }
            .font(Theme.font(12.5, .medium))
            .foregroundStyle(Theme.secondary)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Theme.tagSoft, in: Capsule())
        }
    }
}

/// An error with nothing saved to show: never a blank screen.
struct ErrorState: View {
    let error: EngineError
    var schoologyURL: URL?
    var retry: (() -> Void)?
    var open: ((URL) -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon).font(Theme.font(30)).foregroundStyle(Theme.tertiary)
            Text(title).font(Theme.font(17, .semibold))
            Text(error.message).font(Theme.font(14)).foregroundStyle(Theme.secondary).multilineTextAlignment(.center)
            if case .parse = error, let schoologyURL, let open {
                PillButton(title: "Open this page in Schoology ↗") { open(schoologyURL) }
            } else if let retry {
                PillButton(title: "Try again", action: retry)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .card()
    }

    private var icon: String {
        switch error {
        case .network: return "wifi.slash"
        case .parse: return "doc.questionmark"
        case .rateLimit: return "tortoise"
        default: return "exclamationmark.triangle"
        }
    }
    private var title: String {
        switch error {
        case .network: return "Offline"
        case .parse: return "Couldn't read this page"
        case .rateLimit: return "Too many requests"
        default: return "Something went wrong"
        }
    }
}

struct EmptyState: View {
    let icon: String
    let title: String
    var detail: String?
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon).font(Theme.font(30)).foregroundStyle(Theme.good)
            Text(title).font(Theme.font(17, .semibold))
            if let detail { Text(detail).font(Theme.font(14)).foregroundStyle(Theme.secondary).multilineTextAlignment(.center) }
        }
        .padding(28)
        .frame(maxWidth: .infinity)
        .card()
    }
}

/// The large-title header used by the tab screens: date line + title + trailing buttons.
struct ScreenHeader<Trailing: View>: View {
    var kicker: String?
    let title: String
    @ViewBuilder var trailing: Trailing
    var body: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 2) {
                if let kicker {
                    Text(kicker.uppercased()).font(Theme.font(13, .semibold)).tracking(0.5).foregroundStyle(Theme.secondary)
                }
                Text(title).font(Theme.font(34, .bold))
            }
            Spacer()
            trailing
        }
    }
}
