import SwiftUI

/// 3d · The full site, for everything without a native screen. Bottom bar:
/// back, forward, reload, share. On a page the app has a screen for, an
/// "Open in app" pill.
struct SchoologyTabView: View {
    @Environment(AppModel.self) private var model
    @State private var web = WebController(url: URL(string: "\(Engine.origin.absoluteString)/home")!)
    @State private var path = NavigationPath()

    private var route: WebConfig.NativeRoute? { WebConfig.nativeRoute(for: web.url) }

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                SchoologyWebView(controller: web)
                    .overlay(alignment: .bottom) {
                        if let route {
                            Button { open(route) } label: {
                                Label("Open in app", systemImage: "arrow.up.forward.app")
                                    .font(Theme.font(14, .semibold))
                                    .padding(.horizontal, 16).padding(.vertical, 10)
                                    .foregroundStyle(Theme.onAccent)
                                    .background(Theme.accent, in: Capsule())
                                    .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
                            }
                            .padding(.bottom, 12)
                        }
                    }
                bar
            }
            .navigationTitle(web.title.isEmpty ? "Schoology" : web.title)
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: Dest.self) { d in
                switch d {
                case .assignment(let id): AssignmentView(id: id)
                case .grades(let c): CourseGradesView(course: c)
                case .materials(let c): MaterialsView(sectionId: c.sectionId, folderId: nil, title: c.shortTitle)
                }
            }
        }
    }

    enum Dest: Hashable { case assignment(String), grades(Course), materials(Course) }

    private func open(_ r: WebConfig.NativeRoute) {
        switch r {
        case .assignment(let id): path.append(Dest.assignment(id))
        case .grades(let sid):
            if let c = model.course(sid) { path.append(Dest.grades(c)) }
        case .materials(let sid):
            if let c = model.course(sid) { path.append(Dest.materials(c)) }
        }
    }

    private var bar: some View {
        HStack {
            Button { web.goBack() } label: { Label("Back", systemImage: "chevron.left").labelStyle(.iconOnly) }.disabled(!web.canGoBack)
            Spacer()
            Button { web.goForward() } label: { Label("Forward", systemImage: "chevron.right").labelStyle(.iconOnly) }.disabled(!web.canGoForward)
            Spacer()
            Button { web.load(URL(string: "\(Engine.origin.absoluteString)/home")!) } label: { Label("Schoology home", systemImage: "house").labelStyle(.iconOnly) }
            Spacer()
            if web.isLoading {
                ProgressView().frame(width: 22)
            } else {
                Button { web.reload() } label: { Label("Reload", systemImage: "arrow.clockwise").labelStyle(.iconOnly) }
            }
            Spacer()
            if let u = web.url {
                ShareLink(item: u) { Label("Share", systemImage: "square.and.arrow.up").labelStyle(.iconOnly) }
            } else {
                Image(systemName: "square.and.arrow.up").foregroundStyle(Theme.tertiary)
            }
        }
        .font(Theme.font(19))
        .padding(.horizontal, 28)
        .padding(.vertical, 10)
        .background(.bar)
        .overlay(alignment: .top) { Theme.divider.frame(height: 1) }
    }
}
