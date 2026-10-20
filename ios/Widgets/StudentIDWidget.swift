import AppIntents
import SwiftUI
import WidgetKit

// The student ID, one tap away: a widget and a Control Center button that open
// Dewey on the barcode. Neither shows the number itself (it stays in the app's
// Keychain, never in the shared widget data).

struct StudentIDEntry: TimelineEntry { let date = Date() }

struct StudentIDProvider: TimelineProvider {
    func placeholder(in context: Context) -> StudentIDEntry { StudentIDEntry() }
    func getSnapshot(in context: Context, completion: @escaping (StudentIDEntry) -> Void) { completion(StudentIDEntry()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<StudentIDEntry>) -> Void) {
        completion(Timeline(entries: [StudentIDEntry()], policy: .never))
    }
}

struct StudentIDWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StudentID", provider: StudentIDProvider()) { _ in
            StudentIDWidgetView().containerBackground(.fill.tertiary, for: .widget).widgetURL(AppLink.studentID)
        }
        .configurationDisplayName("Student ID")
        .description("Opens your student ID barcode.")
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryRectangular])
    }
}

struct StudentIDWidgetView: View {
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch family {
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "barcode").font(.title2.weight(.semibold))
            }
            .accessibilityLabel("Student ID")
        case .accessoryRectangular:
            HStack(spacing: 8) {
                Image(systemName: "barcode").font(.title2.weight(.semibold))
                VStack(alignment: .leading) {
                    Text("Student ID").font(.headline)
                    Text("Tap for the barcode").font(.caption)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        default:
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: "person.text.rectangle").font(.title.weight(.semibold))
                Spacer()
                Text("Student ID").font(.headline)
                Text("Tap for the barcode").font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
    }
}

/// Control Center (and the Lock Screen's controls, Action button): the ID card.
@available(iOS 18.0, *)
struct StudentIDControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "com.owenleung.classwork.studentid") {
            ControlWidgetButton(action: OpenStudentIDIntent()) {
                Label("Student ID", systemImage: "barcode")
            }
        }
        .displayName("Student ID")
        .description("Opens your student ID barcode in Dewey.")
    }
}
