import SwiftUI

/// The top of Classes: each class is a book on a shelf, in its own colour, its
/// name up the spine and a call-number label with the period and the letter.
/// A tag on top says when work is late, due today or due tomorrow. Classes taken
/// off the shelf (long-press, or Settings → Classes on the shelf) lie flat under
/// it; at the accessibility text sizes every class lies flat.
struct Shelf: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var typeSize
    @AppStorage(Shelf.offKey) private var offRaw = ""

    /// Section ids of the classes taken off the shelf, comma-separated (new classes start on it).
    static let offKey = "offShelf"
    static func off(_ raw: String) -> Set<String> { Set(raw.split(separator: ",").map(String.init)) }
    static func toggled(_ raw: String, _ id: String) -> String {
        var s = off(raw)
        if s.contains(id) { s.remove(id) } else { s.insert(id) }
        return s.sorted().joined(separator: ",")
    }

    /// The tag on a book's top edge.
    enum Due: Equatable {
        case late, today, tomorrow
        var label: String { switch self { case .late: "LATE"; case .today: "TODAY"; case .tomorrow: "TMRW" } }
        var spoken: String { switch self { case .late: "work late"; case .today: "work due today"; case .tomorrow: "work due tomorrow" } }
    }

    var body: some View {
        let off = Self.off(offRaw)
        let flatAll = typeSize.isAccessibilitySize
        let standing = flatAll ? [] : model.classes.filter { !off.contains($0.sectionId) }
        let lying = flatAll ? model.classes : model.classes.filter { off.contains($0.sectionId) }
        let now = Date()
        let open = model.todoItems().filter { !model.isFinished($0) }
        VStack(alignment: .leading, spacing: Theme.Space.s) {
            if !standing.isEmpty {
                VStack(spacing: 0) {
                    HStack(alignment: .bottom, spacing: 5) {
                        ForEach(standing) { c in
                            NavigationLink(value: c) {
                                BookSpine(course: c, due: due(c, open, now), openCount: open.filter { $0.sectionId == c.sectionId }.count)
                            }
                            .buttonStyle(.plain)
                            .contextMenu { Button("Take off the shelf", systemImage: "arrow.down.to.line") { offRaw = Self.toggled(offRaw, c.sectionId) } }
                        }
                    }
                    .padding(.horizontal, Theme.Space.xs)
                    // The plank.
                    Theme.border.frame(height: 8)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.tag / 3, style: .continuous))
                        .accessibilityHidden(true)
                }
                .padding(.top, Theme.Space.l)
            }
            ForEach(lying) { c in
                NavigationLink(value: c) {
                    FlatBook(course: c, due: due(c, open, now), openCount: open.filter { $0.sectionId == c.sectionId }.count)
                }
                .buttonStyle(.plain)
                .contextMenu {
                    if !flatAll { Button("Put on the shelf", systemImage: "arrow.up.to.line") { offRaw = Self.toggled(offRaw, c.sectionId) } }
                }
            }
            .padding(.top, standing.isEmpty ? 0 : Theme.Space.xs)
        }
        // The letters on the labels (cached gradebooks; Grades refreshes them).
        .task { await model.twoAtATime(model.classes) { await model.loadGrades($0.sectionId, force: false) } }
    }

    private func due(_ c: Course, _ open: [ItemState], _ now: Date) -> Due? {
        let mine = open.filter { $0.sectionId == c.sectionId }
        if mine.contains(where: { TodoLogic.bucket(due: $0.due, now: now) == .overdue && !TodoLogic.isOlder(due: $0.due, now: now) }) { return .late }
        if mine.contains(where: { TodoLogic.bucket(due: $0.due, now: now) == .today }) { return .today }
        if mine.contains(where: { $0.due.map { Calendar.current.isDateInTomorrow($0) } ?? false }) { return .tomorrow }
        return nil
    }
}

/// The LATE / TODAY / TMRW tag: filled for late and today, outlined for tomorrow.
struct DueTag: View {
    let due: Shelf.Due
    var body: some View {
        let color = due == .late ? Theme.late : Theme.accent
        Text(due.label)
            .font(Theme.caption.weight(.heavy)).tracking(0.4)
            .lineLimit(1).minimumScaleFactor(0.7)
            .padding(.horizontal, 5).padding(.vertical, 2)
            .foregroundStyle(due == .tomorrow ? color : due == .late ? Theme.card : Theme.onAccent)
            .background(due == .tomorrow ? Theme.card : color, in: RoundedRectangle(cornerRadius: Theme.Radius.tag / 2, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.tag / 2, style: .continuous).stroke(color, lineWidth: 1.5))
    }
}

/// One book standing up. Heights differ a little per class (stable, meaningless),
/// like a real shelf.
struct BookSpine: View {
    @Environment(AppModel.self) private var model
    let course: Course
    let due: Shelf.Due?
    let openCount: Int

    /// The call-number label's white and ink: the same in light and dark, like a printed sticker.
    static let labelInk = Color(hex: "#15191D")

    var body: some View {
        let g = model.grades[course.sectionId]
        let height = Self.height(course.sectionId)
        let labelH: CGFloat = 58
        VStack(spacing: 0) {
            Text(course.shortTitle)
                .font(Theme.subhead.weight(.semibold))
                .foregroundStyle(.white)
                .lineLimit(1).minimumScaleFactor(0.5)
                .frame(width: height - labelH - 28, height: 30)
                .rotationEffect(.degrees(-90))
                .frame(width: 30, height: height - labelH - 28)
                .padding(.top, 14)
            Spacer(minLength: 0)
            VStack(spacing: 0) {
                Text(course.periodLabel.count <= 3 ? course.periodLabel : "").font(Theme.caption.weight(.bold))
                Text(g?.letter ?? "–").font(Theme.displaySmall).lineLimit(1).minimumScaleFactor(0.6)
            }
            .foregroundStyle(Self.labelInk)
            .frame(maxWidth: .infinity, minHeight: labelH - 14)
            .background(.white, in: RoundedRectangle(cornerRadius: Theme.Radius.tag / 2, style: .continuous))
            .padding(.horizontal, 5).padding(.bottom, 8)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .background(model.spineColor(course.sectionId), in: RoundedRectangle(cornerRadius: Theme.Radius.tag / 1.5, style: .continuous))
        .overlay(alignment: .top) { if let due { DueTag(due: due).offset(y: -10).padding(.horizontal, 2) } }
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(classLabel(course, g, due, openCount))
        .accessibilityHint("Opens this class's materials")
    }

    /// 200–236 pt, from the section id (not hashValue, which changes every launch).
    static func height(_ id: String) -> CGFloat {
        200 + CGFloat(id.unicodeScalars.reduce(0) { $0 + Int($1.value) } % 5) * 9
    }
}

/// A book lying flat: a class taken off the shelf, or every class at the largest text sizes.
struct FlatBook: View {
    @Environment(AppModel.self) private var model
    let course: Course
    let due: Shelf.Due?
    let openCount: Int

    var body: some View {
        let g = model.grades[course.sectionId]
        HStack(spacing: Theme.Space.m) {
            model.classColor(course.sectionId).frame(width: 6)
            AdaptiveStack(spacing: Theme.Space.s) {
                Text(course.shortTitle).font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.text)
                Text(course.periodLabel).font(Theme.footnote).foregroundStyle(Theme.tertiary).oneLine()
                Spacer(minLength: 0)
                if let due { DueTag(due: due) }
                if openCount > 0 { Text("\(openCount) open").font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.accent) }
                Text(g?.letter ?? "–").font(Theme.displaySmall).foregroundStyle(Theme.text)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, Theme.Space.s)
            .padding(.trailing, Theme.Space.m)
        }
        .frame(minHeight: 44)
        .background(Theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(classLabel(course, g, due, openCount))
        .accessibilityHint("Opens this class's materials")
    }
}

private func classLabel(_ c: Course, _ g: CourseGrade?, _ due: Shelf.Due?, _ open: Int) -> String {
    [c.shortTitle, c.periodLabel, g?.letter.map { "grade \($0)" }, due?.spoken, open > 0 ? "\(open) open" : nil]
        .compactMap { $0 }.joined(separator: ", ")
}

/// Settings → Classes on the shelf: which classes stand on the Classes shelf.
struct ShelfSettings: View {
    @Environment(AppModel.self) private var model
    @AppStorage(Shelf.offKey) private var offRaw = ""

    var body: some View {
        Form {
            Section {
                ForEach(model.classes) { c in
                    Toggle(isOn: Binding(get: { !Shelf.off(offRaw).contains(c.sectionId) },
                                         set: { _ in offRaw = Shelf.toggled(offRaw, c.sectionId) })) {
                        HStack(spacing: Theme.Space.s) {
                            Circle().fill(model.classColor(c.sectionId)).frame(width: 10, height: 10)
                            Text(c.shortTitle)
                        }
                    }
                    .tint(Theme.accent)
                }
            } footer: {
                Text("Classes off the shelf lie flat under it. You can also long-press a class on the Classes tab.")
            }
        }
        .navigationTitle("Classes on the shelf")
        .navigationBarTitleDisplayMode(.inline)
    }
}
