import AppIntents
import WidgetKit

/// The To Do widget's circle (square for a test): Done / Studied in the app
/// only, never on Schoology. The app applies it the next time it runs.
struct ToggleDoneIntent: AppIntent {
    static let title: LocalizedStringResource = "Mark done"
    static let isDiscoverable = false

    @Parameter(title: "Item") var id: String

    init() {}
    init(id: String) { self.id = id }

    func perform() async throws -> some IntentResult {
        SharedStore.toggleFromWidget(id)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}
