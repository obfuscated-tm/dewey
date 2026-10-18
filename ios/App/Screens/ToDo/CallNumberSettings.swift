import SwiftUI

/// Settings → To Do → Call numbers (the To Do gear lands on that page): each class's Dewey
/// number on its catalog card. Automatic shows the guess (or that there isn't one).
struct CallNumberSettings: View {
    @Environment(AppModel.self) private var model
    @AppStorage(CallNumber.key) private var raw = ""

    var body: some View {
        DewForm {
            Section {
                ForEach(model.classes) { c in
                    Picker(selection: binding(c)) {
                        Text(autoLabel(c)).tag("auto")
                        Text("None").tag("")
                        ForEach(CallNumber.mains, id: \.number) { m in Text("\(m.number) · \(m.name)").tag(m.number) }
                        // A precise automatic number the student picked by hand once stays listed.
                        if let o = CallNumber.overrides(raw)[c.sectionId], !o.isEmpty, !CallNumber.mains.contains(where: { $0.number == o }) {
                            Text(o).tag(o)
                        }
                    } label: {
                        HStack(spacing: Theme.Space.s) {
                            Circle().fill(model.classColor(c.sectionId)).frame(width: 10, height: 10)
                            Text(c.shortTitle)
                        }
                    }
                }
            } footer: {
                Text("Each class's card on To Do carries its library call number. Dewey guesses from the class name and leaves it blank when it can't tell; pick one here if it's wrong or missing.")
            }
        }
        .navigationTitle("Call numbers")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func autoLabel(_ c: Course) -> String {
        CallNumber.guess(c.shortTitle).map { "Automatic · \($0)" } ?? "Automatic · none"
    }

    private func binding(_ c: Course) -> Binding<String> {
        Binding(get: { CallNumber.overrides(raw)[c.sectionId] ?? "auto" },
                set: { raw = CallNumber.setting(raw, c.sectionId, $0 == "auto" ? nil : $0) })
    }
}
