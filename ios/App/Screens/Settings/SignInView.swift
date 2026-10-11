import SwiftUI

/// 4a · First launch: Schoology in a web view → Google SSO. The app never sees
/// the password; it only watches for a signed-in Schoology page.
struct SignInView: View {
    @Environment(AppModel.self) private var model
    @State private var web: WebController?

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 18) {
                Spacer()
                Image(systemName: "checklist")
                    .font(Theme.font(44, .semibold))
                    .foregroundStyle(Theme.accent)
                Text("Classwork")
                    .font(Theme.font(34, .bold))
                Text("Your Schoology To Do, grades and materials, made for your phone. Sign in with your school Google account, the same way you do on Schoology.")
                    .font(Theme.font(16))
                    .foregroundStyle(Theme.secondary)
                VStack(alignment: .leading, spacing: 10) {
                    step("1", "Sign in to fuhsd.schoology.com", "Required")
                    step("2", "Connect neo-plan", "Optional · in Settings")
                    step("3", "Turn on reminders", "Optional · in Settings")
                }
                .padding(16)
                .card()
                Spacer()
                WideButton(title: "Sign in to Schoology", systemImage: "person.crop.circle") {
                    let w = WebController(url: Engine.origin)
                    w.onFinish = { url in
                        if WebConfig.isSignedInURL(url), url.path.hasPrefix("/home") || url.path == "/" || url.path.hasPrefix("/course") {
                            web = nil
                            Task { await model.signedIn() }
                        }
                    }
                    web = w
                }
                Text("Nothing is sent anywhere but Schoology. The app only reads; submitting happens on Schoology's own page.")
                    .font(Theme.font(12))
                    .foregroundStyle(Theme.tertiary)
            }
            .padding(24)
        }
        // sheet(item:): the sheet is built with the controller, never before it exists.
        .sheet(item: $web) { web in
            NavigationStack {
                    SchoologyWebView(controller: web)
                        .ignoresSafeArea(edges: .bottom)
                        .navigationTitle("Sign in")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { self.web = nil } }
                            ToolbarItem(placement: .primaryAction) {
                                if web.isLoading { ProgressView() } else {
                                    Button { web.reload() } label: { Label("Reload", systemImage: "arrow.clockwise").labelStyle(.iconOnly) }.accessibilityLabel("Reload")
                                }
                            }
                        }
            }
        }
    }

    private func step(_ n: String, _ title: String, _ sub: String) -> some View {
        HStack(spacing: 12) {
            Text(n).font(Theme.mono(13, .semibold))
                .frame(width: 26, height: 26)
                .background(Theme.accentSoft, in: Circle())
                .foregroundStyle(Theme.accent)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(Theme.font(15, .semibold))
                Text(sub).font(Theme.font(13)).foregroundStyle(Theme.secondary)
            }
        }
    }
}
