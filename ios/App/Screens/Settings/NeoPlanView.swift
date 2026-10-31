import SwiftUI

/// Settings → neo-plan: what it is, who wants it, what Dewey syncs, and how to
/// connect. The only place the app mentions neo-plan until it's connected.
/// Mockup: docs/ios/design/Tutorial.options.html, layout 1.
struct NeoPlanView: View {
    @Environment(AppModel.self) private var model
    @Binding var connected: Bool
    @State private var tokenField = ""
    @State private var tokenError: String?
    @State private var syncing = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.l) {
                if connected { connectedCard }
                Text("Optional. Dewey works fully without it.")
                    .font(Theme.subhead).foregroundStyle(Theme.secondary)
                part("What it is", "A planner website (neo-plan.vercel.app) that lays your week out as a board, one column per class. Dewey can fill it from Schoology for you.")
                part("Who it's for", "People who already plan in neo-plan, or want one board shared between this phone and a computer with the Dewey extension. If you don't have an account, skip this.")
                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    heading("What Dewey syncs")
                    VStack(alignment: .leading, spacing: 0) {
                        syncLine("plus", "New work from your To Do goes onto the board.")
                        syncLine("plus", "A new board gets a column for each class with work in it.")
                        syncLine("checkmark", "Done, Studied and Turned in, both ways. The newer change wins.")
                        syncLine("checkmark", "Submitted on Schoology clears it from the board.")
                        syncLine("checkmark", "Type: HW, Test, Task or Classwork.")
                        syncLine("minus", "Never your Schoology password, grades or messages.", dim: true)
                    }
                    .padding(.horizontal, Theme.Space.m)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
                }
                if !connected { connectCard }
            }
            .padding(Theme.Space.l)
        }
        .background(Theme.background)
        .navigationTitle("neo-plan")
        .navigationBarTitleDisplayMode(.inline)
    }

    // ── Connect / connected ──────────────────────────────────────────────

    private var connectCard: some View {
        VStack(alignment: .leading, spacing: Theme.Space.s) {
            heading("Connect")
            VStack(alignment: .leading, spacing: Theme.Space.m) {
                VStack(alignment: .leading, spacing: 6) {
                    numbered(1, "Sign in at neo-plan.vercel.app.")
                    numbered(2, "Open Settings and make an extension token.")
                    numbered(3, "Copy it (it starts with np_) and paste it here.")
                }
                SecureField("np_…", text: $tokenField)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .font(Theme.subhead.monospacedDigit())
                    .padding(10)
                    .overlay(RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous).stroke(Theme.border))
                if let tokenError { Text(tokenError).font(.footnote).foregroundStyle(Theme.late) }
                WideButton(title: "Connect") { connect() }
                    .disabled(tokenField.isEmpty)
                    .opacity(tokenField.isEmpty ? 0.5 : 1)
                Text("The token is kept in the Keychain.").font(Theme.caption).foregroundStyle(Theme.tertiary)
            }
            .padding(Theme.Space.m)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
        }
    }

    private var connectedCard: some View {
        VStack(alignment: .leading, spacing: Theme.Space.m) {
            Label(Self.statusText(), systemImage: "checkmark.circle.fill")
                .font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.good)
            if let problem = model.neoPlanProblem {
                Text(problem).font(.footnote).foregroundStyle(Theme.late)
            }
            LabeledContent("Token", value: NeoPlanClient.mask(model.neoPlan.token))
                .font(Theme.subhead)
            HStack(spacing: Theme.Space.s) {
                PillButton(title: syncing ? "Syncing…" : "Sync now") {
                    guard !syncing else { return }
                    syncing = true
                    Task { await model.syncNeoPlan(byHand: true); syncing = false }
                }
                Spacer()
                PillButton(title: "Disconnect", color: Theme.late) {
                    model.neoPlan.setToken(nil)
                    connected = false
                }
            }
        }
        .padding(Theme.Space.m)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
    }

    private func connect() {
        let t = tokenField.trimmingCharacters(in: .whitespacesAndNewlines)
        guard NeoPlanClient.validToken(t) else { tokenError = "That doesn't look like a neo-plan token (np_…)."; return }
        model.neoPlan.setToken(t)
        tokenField = ""
        tokenError = nil
        connected = true
        Task { await model.syncNeoPlan(byHand: true) }
    }

    /// "Connected · synced 2 min ago", or just "Connected" before the first sync.
    static func statusText(now: Date = Date()) -> String {
        guard let at = Cache.load("neoplanSyncedAt", as: Date.self)?.value else { return "Connected" }
        let ago = RelativeDateTimeFormatter().localizedString(for: at, relativeTo: now)
        return "Connected · synced \(ago)"
    }

    // ── Pieces ───────────────────────────────────────────────────────────

    private func heading(_ s: String) -> some View {
        Text(s.uppercased()).font(Theme.caption.weight(.semibold)).tracking(0.5).foregroundStyle(Theme.secondary)
            .accessibilityAddTraits(.isHeader)
    }

    private func part(_ title: String, _ body: String) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.s) {
            heading(title)
            Text(body).font(Theme.body)
        }
    }

    private func syncLine(_ icon: String, _ text: String, dim: Bool = false) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Space.s) {
            Image(systemName: icon).font(.footnote.weight(.bold)).foregroundStyle(dim ? Theme.tertiary : Theme.accent)
                .frame(width: 18).accessibilityHidden(true)
            Text(text).font(Theme.subhead).foregroundStyle(dim ? Theme.secondary : Theme.text)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 9)
    }

    private func numbered(_ n: Int, _ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Space.s) {
            Text("\(n).").font(Theme.subhead.monospacedDigit()).foregroundStyle(Theme.secondary)
            Text(text).font(Theme.subhead)
        }
    }
}
