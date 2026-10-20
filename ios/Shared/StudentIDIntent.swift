import AppIntents
import Foundation

/// The Control Center button's action: opens Dewey on the ID card (classwork://id).
/// In both targets, so the system can open the app from the widget extension.
@available(iOS 18.0, *)
struct OpenStudentIDIntent: AppIntent {
    static let title: LocalizedStringResource = "Open Student ID"
    static let isDiscoverable = false
    static let openAppWhenRun = true

    func perform() async throws -> some IntentResult & OpensIntent {
        .result(opensIntent: OpenURLIntent(AppLink.studentID))
    }
}
